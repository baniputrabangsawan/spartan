package tech.cocok.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CatalogDao {
    @Query("SELECT * FROM vehicles ORDER BY sortOrder")
    suspend fun vehicles(): List<VehicleEntity>

    @Query("SELECT * FROM part_categories ORDER BY sortOrder")
    suspend fun categories(): List<PartCategoryEntity>

    @Query("SELECT * FROM parts ORDER BY sortOrder")
    suspend fun parts(): List<PartEntity>

    @Query("SELECT * FROM fitment_groups ORDER BY sortOrder")
    suspend fun groups(): List<FitmentGroupEntity>

    @Query("SELECT * FROM group_parts ORDER BY sortOrder")
    suspend fun groupParts(): List<GroupPartEntity>

    @Query("SELECT * FROM fitments ORDER BY sortOrder")
    suspend fun fitments(): List<FitmentEntity>

    @Query("SELECT * FROM synonyms ORDER BY sortOrder")
    suspend fun synonyms(): List<SynonymEntity>

    @Query("SELECT * FROM confirmations ORDER BY sortOrder")
    suspend fun confirmations(): List<ConfirmationEntity>

    @Query("SELECT * FROM match_evidence ORDER BY sortOrder")
    suspend fun matchEvidence(): List<MatchEvidenceEntity>

    @Query("SELECT initialized FROM catalog_metadata WHERE id = 'catalog'")
    suspend fun initialized(): Boolean?

    @Upsert
    suspend fun upsertVehicles(rows: List<VehicleEntity>)

    @Upsert
    suspend fun upsertCategories(rows: List<PartCategoryEntity>)

    @Upsert
    suspend fun upsertParts(rows: List<PartEntity>)

    @Upsert
    suspend fun upsertGroups(rows: List<FitmentGroupEntity>)

    @Upsert
    suspend fun upsertGroupParts(rows: List<GroupPartEntity>)

    @Upsert
    suspend fun upsertFitments(rows: List<FitmentEntity>)

    @Upsert
    suspend fun upsertSynonyms(rows: List<SynonymEntity>)

    @Upsert
    suspend fun upsertConfirmations(rows: List<ConfirmationEntity>)


    @Upsert
    suspend fun upsertMatchEvidence(rows: List<MatchEvidenceEntity>)
    @Upsert
    suspend fun upsertMetadata(row: CatalogMetadataEntity)

    @Delete
    suspend fun deleteVehicles(rows: List<VehicleEntity>)

    @Delete
    suspend fun deleteCategories(rows: List<PartCategoryEntity>)

    @Delete
    suspend fun deleteParts(rows: List<PartEntity>)

    @Delete
    suspend fun deleteGroups(rows: List<FitmentGroupEntity>)

    @Delete
    suspend fun deleteGroupParts(rows: List<GroupPartEntity>)

    @Delete
    suspend fun deleteFitments(rows: List<FitmentEntity>)

    @Delete
    suspend fun deleteSynonyms(rows: List<SynonymEntity>)

    @Delete
    suspend fun deleteConfirmations(rows: List<ConfirmationEntity>)

    @Delete
    suspend fun deleteMatchEvidence(rows: List<MatchEvidenceEntity>)
}
