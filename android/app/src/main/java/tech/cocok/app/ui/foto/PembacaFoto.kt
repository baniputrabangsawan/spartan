package tech.cocok.app.ui.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** OCR and preview stay local. Only recognized or typed keywords are sent for image search. */
object PembacaFoto {
    private const val SISI_MAKS = 1600
    private const val BATAS_BYTE = 25L * 1024 * 1024

    fun berkasBaru(context: Context): File =
        File(context.cacheDir, "foto").apply { mkdirs() }
            .let { File(it, "barang-${UUID.randomUUID()}.jpg") }

    fun uriUntuk(context: Context, berkas: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", berkas)

    /** Salinan privat tidak bergantung pada izin galeri yang bisa berakhir. */
    suspend fun salin(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        val berkas = berkasBaru(context)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Foto tidak bisa dibuka")
            input.use { sumber ->
                berkas.outputStream().use { tujuan ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = sumber.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= BATAS_BYTE) { "Foto terlalu besar (maksimal 25 MB)." }
                        tujuan.write(buffer, 0, count)
                    }
                }
            }
            uriUntuk(context, berkas)
        } catch (error: Exception) {
            berkas.delete()
            throw error
        }
    }

    suspend fun pratinjau(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val batas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, batas) }
        check(batas.outWidth > 0 && batas.outHeight > 0) { "Berkas bukan foto yang bisa dibaca." }
        var skala = 1
        while (batas.outWidth / skala > SISI_MAKS || batas.outHeight / skala > SISI_MAKS) skala *= 2
        val opsi = BitmapFactory.Options().apply { inSampleSize = skala }
        val asli = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opsi)
        } ?: error("Foto tidak bisa dibaca")
        try {
            val orientasi = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientasi) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) asli else {
                val hasil = Bitmap.createBitmap(asli, 0, 0, asli.width, asli.height, matrix, true)
                if (hasil !== asli) asli.recycle()
                hasil
            }
        } catch (error: Exception) {
            asli.recycle()
            throw error
        }
    }

    /** Resource tetap hidup sampai ML Kit selesai, termasuk saat coroutine dibatalkan. */
    suspend fun baca(context: Context, uri: Uri): Result<String> = try {
        val bitmap = pratinjau(context, uri)
        val pengenal = try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
        val teks = suspendCancellableCoroutine<String> { lanjut ->
            try {
                pengenal.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                    try {
                        if (lanjut.isActive) {
                            if (task.isSuccessful) lanjut.resume(task.result.text)
                            else lanjut.resumeWithException(task.exception ?: error("OCR gagal"))
                        }
                    } finally {
                        pengenal.close()
                        bitmap.recycle()
                    }
                }
            } catch (error: Exception) {
                pengenal.close()
                bitmap.recycle()
                if (lanjut.isActive) lanjut.resumeWithException(error)
            }
        }
        Result.success(teks)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
