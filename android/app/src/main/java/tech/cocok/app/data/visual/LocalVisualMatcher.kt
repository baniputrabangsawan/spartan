package tech.cocok.app.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.cocok.app.domain.model.Part
import tech.cocok.app.ui.foto.PembacaFoto

/**
 * Offline MobileNet-v3-small image embeddings, not OCR or a fitment classifier.
 * Values are raw cosine similarities [-1, 1], NOT identity/compatibility probabilities.
 * Callers may rank descending; even the highest score requires independent verification.
 */
internal class LocalVisualMatcher(context: Context) {
    private val context = context.applicationContext
    private val searchLock = Mutex()

    /**
     * Reads current photo bytes on every search; no stale URI-keyed embedding cache.
     * A missing/unreadable catalog photo is omitted. An unreadable source or model failure
     * is an error, not an empty successful match. No-photo catalogs return an empty map.
     */
    suspend fun search(photo: Uri, parts: List<Part>): Map<String, Float> = withContext(Dispatchers.IO) {
        searchLock.withLock {
            currentCoroutineContext().ensureActive()
            if (parts.none { localUri(it.photoKey) != null }) return@withLock emptyMap()
            // The source may be a cache path from OCR or a FileProvider URI.
            val sourceUri = Uri.parse(photo.toString()).takeIf {
                it.scheme == "content" || it.scheme == "file"
            } ?: error("Pencocokan lokal hanya menerima foto perangkat, bukan alamat internet.")
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()

            // One model per search, not per candidate; finally/use also closes on cancellation.
            ImageEmbedder.createFromOptions(context, options).use { embedder ->
                val sourceBitmap = PembacaFoto.pratinjau(context, sourceUri)
                val source = try {
                    embed(embedder, sourceBitmap)
                } finally {
                    if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
                }
                val scores = linkedMapOf<String, Float>()
                for (part in parts) {
                    currentCoroutineContext().ensureActive()
                    val uri = localUri(part.photoKey) ?: continue
                    val bitmap = catalogBitmap(uri) ?: continue
                    try {
                        val candidate = embed(embedder, bitmap)
                        val score = ImageEmbedder.cosineSimilarity(source, candidate)
                        check(score.isFinite()) { "Model tidak menghasilkan kemiripan visual yang valid." }
                        scores[part.id] = score.coerceIn(-1.0, 1.0).toFloat()
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                currentCoroutineContext().ensureActive()
                scores
            }
        }
    }

    private suspend fun embed(embedder: ImageEmbedder, bitmap: Bitmap): Embedding {
        currentCoroutineContext().ensureActive()
        val rgb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: error("Warna foto tidak bisa dibaca.")
        }
        try {
            // MediaPipe uses the bundled metadata: resize to 224x224, RGB float [0, 1].
            // MPImage owns this bitmap until synchronous inference completes, then recycles it.
            return BitmapImageBuilder(rgb).build().use { image ->
                val result = embedder.embed(image)
                currentCoroutineContext().ensureActive()
                result.embeddingResult().embeddings().single()
            }
        } finally {
            if (rgb !== bitmap && !rgb.isRecycled) rgb.recycle()
        }
    }

    private suspend fun catalogBitmap(uri: Uri): Bitmap? = try {
        // Keep the same IO dispatcher as pratinjau so a cancelled dispatcher return cannot
        // orphan its bitmap. Decoder bounds the longest edge to 1600 and applies EXIF.
        PembacaFoto.pratinjau(context, uri)
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun localUri(value: String?): Uri? {
        if (value.isNullOrBlank()) return null
        val uri = Uri.parse(value)
        return when (uri.scheme) {
            "content", "file" -> uri
            null -> if (File(value).isAbsolute) Uri.fromFile(File(value)) else null
            else -> null // A catalog URL is not fetched or treated as an indexed local photo.
        }
    }

    private companion object {
        // Official MediaPipe model v1, Apache-2.0; 224x224 float32, ImageNet feature vector.
        const val MODEL_ASSET = "mobilenet_v3_small.tflite"
    }
}
