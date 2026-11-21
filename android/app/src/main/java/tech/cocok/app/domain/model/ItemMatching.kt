package tech.cocok.app.domain.model

enum class CandidateOrigin { CATALOG, INTERNET, INPUT }

data class MatchItem(
    val id: String,
    val name: String,
    val partNumber: String? = null,
    val brand: String? = null,
    val categoryId: String? = null,
    val photo: String? = null,
    val specifications: Map<String, String> = emptyMap(),
    /**
     * Set only while this item is an unmodified catalog row. This is the trust
     * boundary for verification, so editing clears it.
     */
    val catalogPartId: String? = null,
    /**
     * The catalog row this item was derived from, kept across edits so filled-in
     * specifications can still be saved back. Never grants trust on its own.
     */
    val linkedCatalogPartId: String? = null,
    val sourceUrl: String? = null,
    val origin: CandidateOrigin = CandidateOrigin.INPUT,
)

data class MatchContext(
    val vehicleId: String? = null,
    val year: Int? = null,
    val variant: Map<String, String> = emptyMap(),
)

enum class MatchKind { SAME_ITEM, DIRECT_REPLACEMENT, NOT_REPLACEMENT }

data class MatchEvidence(
    val id: String,
    val firstPartId: String,
    val secondPartId: String,
    val vehicleId: String,
    val year: Int,
    val variant: Map<String, String>,
    val kind: MatchKind,
    val note: String,
    val by: String,
    val role: Role,
    val createdAt: String,
)

enum class ComparisonKind { SAME_ITEM, VERIFIED_REPLACEMENT, REJECTED, DIFFERENCES, CANDIDATE }
enum class SpecificationState { SAME, DIFFERENT, MISSING }

data class SpecificationComparison(
    val key: String,
    val label: String,
    val first: String?,
    val second: String?,
    val state: SpecificationState,
    val check: String,
)

data class ItemComparison(
    val kind: ComparisonKind,
    val label: String,
    val explanation: String,
    val rows: List<SpecificationComparison>,
    val checks: List<String>,
    val canVerify: Boolean,
)

data class MatchCandidate(val item: MatchItem, val reason: String, val similarity: Float? = null)
