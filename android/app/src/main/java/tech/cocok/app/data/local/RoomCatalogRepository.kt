package tech.cocok.app.data.local

import androidx.room.withTransaction
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.CatalogRepository

class RoomCatalogRepository(
    private val database: CocokDatabase,
    private val seed: () -> Catalog,
) : CatalogRepository {
    private val dao = database.catalogDao()

    override suspend fun load(): Catalog = database.withTransaction {
        loadRows().toCatalog()
    }

    override suspend fun update(transform: (Catalog) -> Catalog): Catalog = database.withTransaction {
        val stored = loadRows()
        val current = stored.toCatalog()
        val updated = transform(current)
        if (updated != current) persistChanges(stored, CatalogRows.fromCatalog(updated))
        updated
    }

    // The flag and seed commit together, including when the seed has no rows.
    // Room serializes these transactions even across repository instances.
    private suspend fun loadRows(): CatalogRows {
        val initialized = dao.initialized() == true
        val stored = CatalogRows.read(dao)
        if (initialized) return stored

        val initial = CatalogRows.fromCatalog(seed())
        persistChanges(stored, initial)
        dao.upsertMetadata(CatalogMetadataEntity())
        return initial
    }

    private suspend fun persistChanges(before: CatalogRows, after: CatalogRows) {
        // Deferred, non-cascading foreign keys validate the complete result at commit.
        // Removals and reparenting work without erasing unrelated rows or orphaning links.
        syncRows("vehicles", before.vehicles, after.vehicles, { it.id }, dao::upsertVehicles, dao::deleteVehicles)
        syncRows("categories", before.categories, after.categories, { it.id }, dao::upsertCategories, dao::deleteCategories)
        syncRows("parts", before.parts, after.parts, { it.id }, dao::upsertParts, dao::deleteParts)
        syncRows("groups", before.groups, after.groups, { it.id }, dao::upsertGroups, dao::deleteGroups)
        syncRows("group parts", before.groupParts, after.groupParts, { it.groupId to it.partId }, dao::upsertGroupParts, dao::deleteGroupParts)
        syncRows("fitments", before.fitments, after.fitments, { it.id }, dao::upsertFitments, dao::deleteFitments)
        syncRows("synonyms", before.synonyms, after.synonyms, { it.term }, dao::upsertSynonyms, dao::deleteSynonyms)
        syncRows("confirmations", before.confirmations, after.confirmations, { it.id }, dao::upsertConfirmations, dao::deleteConfirmations)
        syncRows("match evidence", before.matchEvidence, after.matchEvidence, { it.id }, dao::upsertMatchEvidence, dao::deleteMatchEvidence)

        // Android's SQLiteSession can leave a deferred-FK COMMIT failure open.
        // Detect violations before COMMIT so withTransaction takes its normal
        // rollback path and the same connection remains usable afterwards.
        database.query("PRAGMA foreign_key_check", emptyArray()).use { violations ->
            check(!violations.moveToFirst()) { "Catalog contains an invalid foreign key" }
        }
    }

    private suspend fun <T, K> syncRows(
        table: String,
        before: List<T>,
        after: List<T>,
        key: (T) -> K,
        upsert: suspend (List<T>) -> Unit,
        delete: suspend (List<T>) -> Unit,
    ) {
        if (before == after) return
        val remaining = before.associateByTo(LinkedHashMap(before.size), key)
        val seen = HashSet<K>(after.size)
        val changed = ArrayList<T>()
        for (row in after) {
            val id = key(row)
            require(seen.add(id)) { "Duplicate key in catalog $table: $id" }
            if (remaining.remove(id) != row) changed.add(row)
        }
        if (remaining.isNotEmpty()) delete(remaining.values.toList())
        if (changed.isNotEmpty()) upsert(changed)
    }
}
