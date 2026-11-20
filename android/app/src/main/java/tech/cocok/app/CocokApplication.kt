package tech.cocok.app

import android.app.Application
import tech.cocok.app.data.SeedData
import tech.cocok.app.data.local.CocokDatabase
import tech.cocok.app.data.local.RoomCatalogRepository
import tech.cocok.app.domain.model.CatalogRepository

class CocokApplication : Application() {
    private val database by lazy { CocokDatabase.open(this) }
    val catalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(database) { SeedData.catalog }
    }
}
