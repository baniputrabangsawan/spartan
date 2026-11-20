package tech.cocok.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.cocok.app.data.internet.GoogleKeywordSearch
import tech.cocok.app.data.internet.GoogleVisualSearch
import tech.cocok.app.data.visual.LocalVisualMatcher
import tech.cocok.app.domain.model.CandidateOrigin
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.InternetImage
import tech.cocok.app.domain.model.MatchCandidate
import tech.cocok.app.domain.model.MatchContext
import tech.cocok.app.domain.model.MatchItem
import tech.cocok.app.domain.usecase.MatchParts

/**
 * Photo/keyword item matching. Photo shapes are ranked by real on-device embeddings;
 * nothing here changes catalog confidence, and no result becomes a verified replacement.
 */
internal data class MatchState(
    val keyword: String = "",
    val source: MatchItem? = null,
    val photo: String? = null,
    val localLoading: Boolean = false,
    val internetLoading: Boolean = false,
    val candidates: List<MatchCandidate> = emptyList(),
    val internet: List<InternetImage> = emptyList(),
    val internetDone: Boolean = false,
    val noPhotoIndex: Boolean = false,
    val error: String? = null,
)

internal class MatchViewModel(application: Application) : AndroidViewModel(application) {
    private val parts = MatchParts()
    private val visual = LocalVisualMatcher(application)
    private val internetVisual = GoogleVisualSearch(application)
    private val internetKeyword = GoogleKeywordSearch(application)
    private val mutable = MutableStateFlow(MatchState())
    val state = mutable.asStateFlow()

    private var localRequest: Job? = null
    private var internetRequest: Job? = null
    private var internetGeneration = 0

    fun editKeyword(value: String) = mutable.update { it.copy(keyword = value.take(300)) }

    fun setPhoto(reference: String) = mutable.update { it.copy(photo = reference) }

    /**
     * Editing identity/specs must not inherit trust from a catalog row: the item
     * becomes plain input, so no verification or "same item" label can ride along.
     */
    fun editItem(id: String, transform: (MatchItem) -> MatchItem) = mutable.update { state ->
        val edited = transform(
            if (state.source?.id == id) state.source
            else state.candidates.firstOrNull { it.item.id == id }?.item
            ?: return@update state,
        ).copy(catalogPartId = null, origin = CandidateOrigin.INPUT)
        state.copy(
            source = if (state.source?.id == id) edited else state.source,
            candidates = state.candidates.map { if (it.item.id == id) it.copy(item = edited) else it },
        )
    }

    fun useCatalogItem(partId: String, catalog: Catalog) {
        val part = catalog.part(partId) ?: return
        localRequest?.cancel()
        mutable.update {
            it.copy(
                source = parts.item(part),
                photo = part.photoKey,
                candidates = parts.discover(catalog, "", sourcePartId = part.id),
                noPhotoIndex = false,
                error = null,
            )
        }
    }

    fun searchLocal(catalog: Catalog, context: MatchContext = MatchContext()) {
        localRequest?.cancel()
        mutable.update { it.copy(localLoading = true, error = null, candidates = emptyList()) }
        localRequest = viewModelScope.launch {
            try {
                val found = parts.discover(
                    catalog = catalog,
                    query = mutable.value.keyword,
                    fromPhoto = mutable.value.photo != null,
                    sourcePartId = mutable.value.source?.catalogPartId,
                    context = context,
                )
                mutable.update { it.copy(localLoading = false, candidates = found) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update {
                    it.copy(localLoading = false, error = error.message ?: "Pencarian toko gagal.")
                }
            }
        }
    }

    /** Bare-photo discovery needs real embeddings; with no photo index it says so. */
    fun searchVisualLocal(catalog: Catalog, photo: Uri) {
        localRequest?.cancel()
        mutable.update { it.copy(localLoading = true, noPhotoIndex = false, error = null) }
        localRequest = viewModelScope.launch {
            try {
                val scores = visual.search(photo, catalog.parts)
                if (scores.isEmpty()) {
                    mutable.update { it.copy(localLoading = false, noPhotoIndex = true) }
                    return@launch
                }
                val ranked = scores.entries
                    .sortedByDescending { it.value }
                    .mapNotNull { (partId, score) ->
                        catalog.part(partId)?.let { part ->
                            MatchCandidate(
                                item = parts.item(part),
                                reason = "Kemiripan bentuk ${(score * 100).toInt()}% dari model lokal; bukan bukti cocok.",
                                similarity = score,
                            )
                        }
                    }
                mutable.update { it.copy(localLoading = false, candidates = ranked) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update {
                    it.copy(
                        localLoading = false,
                        error = error.message ?: "Foto tidak dapat dibandingkan dengan katalog lokal.",
                    )
                }
            }
        }
    }

    /** Reverse-image search. Caller must have asked the user before uploading. */
    fun searchInternet(photo: Uri) = runInternet { internetVisual.search(photo) }

    /**
     * Keyword image search. Used when the photo has no text and no catalog photo
     * index exists, so the user still gets real candidates instead of a dead end.
     */
    fun searchInternetKeyword(keyword: String) {
        val query = keyword.trim().take(300)
        if (query.isEmpty()) {
            mutable.update { it.copy(error = "Isi nama barang atau nomor part sebelum mencari gambar.") }
            return
        }
        runInternet { internetKeyword.search(query) }
    }

    private fun runInternet(block: suspend () -> List<InternetImage>) {
        val generation = ++internetGeneration
        internetRequest?.cancel()
        mutable.update {
            it.copy(internetLoading = true, internetDone = false, internet = emptyList(), error = null)
        }
        internetRequest = viewModelScope.launch {
            try {
                val images = block()
                if (generation != internetGeneration) return@launch
                mutable.update { it.copy(internetLoading = false, internetDone = true, internet = images) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != internetGeneration) return@launch
                mutable.update {
                    it.copy(
                        internetLoading = false,
                        internetDone = true,
                        error = error.message ?: "Pencarian gambar internet gagal.",
                    )
                }
            }
        }
    }

    fun candidateFromImage(image: InternetImage): MatchCandidate = MatchCandidate(
        item = MatchItem(
            id = "web-" + UUID.nameUUIDFromBytes(image.sourceUrl.toByteArray()).toString(),
            name = image.title,
            sourceUrl = image.sourceUrl,
            photo = image.thumbnailUrl,
            origin = CandidateOrigin.INTERNET,
        ),
        reason = "Hasil gambar internet; judul dan tautan sumber belum diperiksa toko.",
    )

    /**
     * Internet results must be comparable, not just openable links. Returns the
     * candidate id after inserting it, so the screen can move to the comparison.
     */
    fun useInternetCandidate(image: InternetImage): String {
        val candidate = candidateFromImage(image)
        mutable.update { state ->
            if (state.candidates.any { it.item.id == candidate.item.id }) state
            else state.copy(candidates = state.candidates + candidate)
        }
        return candidate.item.id
    }

    fun clear() {
        localRequest?.cancel()
        internetRequest?.cancel()
        internetGeneration++
        mutable.value = MatchState()
    }
}
