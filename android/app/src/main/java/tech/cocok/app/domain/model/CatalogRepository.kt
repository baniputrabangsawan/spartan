package tech.cocok.app.domain.model

/** Local source of truth. A mutation commits completely before returning. */
interface CatalogRepository {
    suspend fun load(): Catalog
    suspend fun update(transform: (Catalog) -> Catalog): Catalog
}
