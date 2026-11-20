package tech.cocok.app.data.internet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tech.cocok.app.domain.model.InternetImage

/** Uploads only after the caller has obtained explicit photo-upload consent. No Lens page is shown. */
internal class GoogleVisualSearch(context: Context) {
    private val applicationContext = context.applicationContext
    private val keywordSearch = GoogleKeywordSearch(applicationContext)

    suspend fun search(photo: Uri): List<InternetImage> {
        require(photo.scheme == "content" || photo.scheme == "file") { "Pilih foto lokal untuk pencarian gambar." }
        val resultUrl = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val jpeg = prepareJpeg(photo)
                currentCoroutineContext().ensureActive()
                upload(jpeg)
            }
        } ?: throw GoogleKeywordSearchException(
            GoogleKeywordSearchException.Reason.TIMEOUT,
            "Unggah foto ke Google terlalu lama. Periksa koneksi lalu coba lagi.",
        )
        return keywordSearch.fromResultUrl(resultUrl)
    }

    private suspend fun prepareJpeg(photo: Uri): ByteArray {
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openPhoto(photo).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS
        ) { "Foto tidak dapat dibaca atau ukurannya terlalu besar." }
        val orientation = openPhoto(photo).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = openPhoto(photo).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Foto tidak dapat dibaca.")
        try {
            // Sampling can round upwards. Also constrain the actual allocation before encoding.
            val edge = maxOf(bitmap.width, bitmap.height)
            if (edge > MAX_EDGE) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width.toLong() * MAX_EDGE / edge).toInt().coerceAtLeast(1),
                    (bitmap.height.toLong() * MAX_EDGE / edge).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            val matrix = orientationMatrix(orientation)
            if (!matrix.isIdentity) {
                val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (upright !== bitmap) bitmap.recycle()
                bitmap = upright
            }
            currentCoroutineContext().ensureActive()
            // Encoding pixels afresh excludes EXIF, GPS, original filename and all source metadata.
            return BoundedJpegStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw IOException("Foto gagal diproses.")
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun openPhoto(photo: Uri): InputStream = BoundedPhotoStream(
        applicationContext.contentResolver.openInputStream(photo) ?: throw IOException("Foto tidak dapat dibuka."),
    )

    private suspend fun upload(jpeg: ByteArray): String = suspendCancellableCoroutine { pending ->
        // Keep this endpoint query-free: Google currently rejects uploads containing `hl`.
        val connection = URL(UPLOAD_URL).openConnection() as HttpsURLConnection
        pending.invokeOnCancellation { connection.disconnect() }
        try {
            val boundary = "Cocok${UUID.randomUUID()}"
            val header = ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"encoded_image\"; filename=\"image.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n").toByteArray(Charsets.US_ASCII)
            val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII)
            connection.apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "text/html")
                setFixedLengthStreamingMode(header.size + jpeg.size + footer.size)
            }
            if (!pending.isActive) throw CancellationException()
            connection.outputStream.use { output ->
                output.write(header)
                var offset = 0
                while (offset < jpeg.size) {
                    if (!pending.isActive) throw CancellationException()
                    val count = minOf(16 * 1024, jpeg.size - offset)
                    output.write(jpeg, offset, count)
                    offset += count
                }
                output.write(footer)
            }
            if (!pending.isActive) throw CancellationException()
            val status = connection.responseCode
            if (status in setOf(401, 403, 429)) throw blockedFailure()
            // The public v3 endpoint returns 303 to /search?vsrid=...&udm=26. Never replay the
            // photograph to a redirect destination (including 307/308), or follow consent/CAPTCHA.
            if (status != 302 && status != 303) throw GoogleKeywordSearchException(
                if (status >= 500) GoogleKeywordSearchException.Reason.NETWORK else GoogleKeywordSearchException.Reason.FORMAT,
                "Google tidak mengembalikan halaman hasil unggah foto (HTTP $status).",
            )
            val location = connection.getHeaderField("Location") ?: throw GoogleKeywordSearchException(
                GoogleKeywordSearchException.Reason.FORMAT,
                "Alamat hasil unggah foto Google tidak tersedia.",
            )
            val result = checkedResultUrl(location)
            pending.resumeWith(Result.success(result))
        } catch (error: Exception) {
            if (pending.isActive) pending.resumeWith(Result.failure(
                when (error) {
                    is CancellationException, is GoogleKeywordSearchException -> error
                    else -> GoogleKeywordSearchException(
                        GoogleKeywordSearchException.Reason.NETWORK,
                        "Foto tidak dapat dikirim ke Google. Periksa koneksi internet.",
                        error,
                    )
                },
            ))
        } finally {
            connection.disconnect()
        }
    }

    private class BoundedPhotoStream(input: InputStream) : FilterInputStream(input) {
        private var consumed = 0L
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = `in`.read(buffer, offset, minOf(length.toLong(), MAX_SOURCE_BYTES - consumed + 1).toInt())
            if (count > 0) record(count.toLong())
            return count
        }
        override fun skip(count: Long): Long {
            val skipped = super.skip(minOf(count, MAX_SOURCE_BYTES - consumed + 1))
            record(skipped)
            return skipped
        }
        private fun record(count: Long) {
            consumed += count
            if (consumed > MAX_SOURCE_BYTES) throw IOException("Ukuran berkas foto melebihi 20 MB.")
        }
    }

    private class BoundedJpegStream : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= MAX_JPEG_BYTES) throw IOException("Foto hasil kompresi terlalu besar.")
            super.write(value)
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length > MAX_JPEG_BYTES - count) throw IOException("Foto hasil kompresi terlalu besar.")
            super.write(buffer, offset, length)
        }
    }

    private companion object {
        const val UPLOAD_URL = "https://lens.google.com/v3/upload"
        const val MAX_EDGE = 1600
        const val MAX_SOURCE_PIXELS = 120_000_000L
        const val MAX_SOURCE_BYTES = 20L * 1024 * 1024
        const val MAX_JPEG_BYTES = 2 * 1024 * 1024
        const val UPLOAD_TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        val RESULT_HOSTS = setOf("www.google.com", "google.com", "www.google.co.id", "google.co.id")

        fun checkedResultUrl(location: String): String {
            val uri = try {
                require(location.length <= 32 * 1024)
                URI(UPLOAD_URL).resolve(location)
            } catch (_: IllegalArgumentException) { throw blockedFailure() }
            catch (_: java.net.URISyntaxException) { throw blockedFailure() }
            if (uri.scheme != "https" || uri.host?.lowercase() !in RESULT_HOSTS ||
                uri.rawUserInfo != null || uri.port !in setOf(-1, 443) || uri.path != "/search" || uri.rawFragment != null
            ) throw blockedFailure()
            return uri.toASCIIString()
        }

        fun blockedFailure() = GoogleKeywordSearchException(
            GoogleKeywordSearchException.Reason.BLOCKED,
            "Google membatasi pencarian atau mengalihkan unggahan ke halaman yang tidak diizinkan. Pencarian tidak dapat dilanjutkan di aplikasi.",
        )

        fun orientationMatrix(orientation: Int) = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
    }
}
