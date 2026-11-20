package tech.cocok.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val model: String,
    val generation: String?,
    val yearFrom: Int?,
    val yearTo: Int?,
    val attributes: Map<String, String>,
    val verified: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "part_categories")
data class PartCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "parts",
    foreignKeys = [ForeignKey(
        entity = PartCategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        deferred = true,
    )],
    indices = [Index("categoryId")],
)
data class PartEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val brand: String?,
    val partNumber: String?,
    val isOriginal: Boolean,
    val photoKey: String?,
    val specifications: Map<String, String>,
    val notes: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "fitment_groups",
    foreignKeys = [ForeignKey(
        entity = PartCategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        deferred = true,
    )],
    indices = [Index("categoryId")],
)
data class FitmentGroupEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val label: String,
    val notes: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "group_parts",
    primaryKeys = ["groupId", "partId"],
    foreignKeys = [
        ForeignKey(
            entity = FitmentGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            deferred = true,
        ),
        ForeignKey(
            entity = PartEntity::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            deferred = true,
        ),
    ],
    indices = [Index("partId")],
)
data class GroupPartEntity(
    val groupId: String,
    val partId: String,
    val status: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "fitments",
    foreignKeys = [
        ForeignKey(
            entity = FitmentGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            deferred = true,
        ),
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            deferred = true,
        ),
    ],
    indices = [Index("groupId"), Index("vehicleId"), Index(value = ["id", "groupId"], unique = true)],
)
data class FitmentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val vehicleId: String,
    val yearFrom: Int?,
    val yearTo: Int?,
    val constraints: Map<String, String>,
    val status: String,
    val sumber: String,
    val confirmedByRole: String?,
    val confirmedAt: String?,
    val sortOrder: Int,
)

@Entity(tableName = "synonyms")
data class SynonymEntity(
    @PrimaryKey val term: String,
    val replacement: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "confirmations",
    foreignKeys = [ForeignKey(
        entity = FitmentEntity::class,
        parentColumns = ["id", "groupId"],
        childColumns = ["fitmentId", "groupId"],
        deferred = true,
    )],
    indices = [Index(value = ["fitmentId", "groupId"])],
)
data class ConfirmationEntity(
    @PrimaryKey val id: String,
    val fitmentId: String,
    val groupId: String,
    val oleh: String,
    val role: String,
    val hasil: Boolean,
    val catatan: String?,
    val waktu: String,
    val sortOrder: Int,
)

@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @PrimaryKey val id: String = "catalog",
    val initialized: Boolean = true,
)

@Entity(
    tableName = "match_evidence",
    foreignKeys = [
        ForeignKey(
            entity = PartEntity::class,
            parentColumns = ["id"],
            childColumns = ["firstPartId"],
            deferred = true,
        ),
        ForeignKey(
            entity = PartEntity::class,
            parentColumns = ["id"],
            childColumns = ["secondPartId"],
            deferred = true,
        ),
    ],
    indices = [Index("firstPartId"), Index("secondPartId")],
)
data class MatchEvidenceEntity(
    @PrimaryKey val id: String,
    val firstPartId: String,
    val secondPartId: String,
    val vehicleId: String,
    val year: Int,
    val variant: Map<String, String>,
    val kind: String,
    val note: String,
    val oleh: String,
    val role: String,
    val createdAt: String,
    val sortOrder: Int,
)
