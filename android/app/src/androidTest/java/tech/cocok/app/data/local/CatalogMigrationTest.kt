package tech.cocok.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.data.SeedData

/**
 * v1 -> v2 is additive: specifications default to empty and evidence starts empty,
 * so previously saved catalogs keep their meaning instead of being reseeded.
 */
@RunWith(AndroidJUnit4::class)
class CatalogMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CocokDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesVersionOneCatalogWithSpecsAndEvidenceDefaults() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO vehicles VALUES ('v-1','Toyota','Hilux','Reborn',2016,2020,'{}',1,0)",
            )
            db.execSQL("INSERT INTO part_categories VALUES ('c-1','Lampu stop',NULL,0)")
            db.execSQL(
                "INSERT INTO parts VALUES ('p-1','c-1','Lampu stop kiri','Toyota','81560-0K290',1,NULL,'catatan',0)",
            )
            db.execSQL("INSERT INTO catalog_metadata VALUES ('catalog',1)")
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            CocokDatabase.MIGRATION_1_2,
        )
        migrated.query("SELECT specifications FROM parts WHERE id='p-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{}", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM match_evidence").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT initialized FROM catalog_metadata WHERE id='catalog'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        assertEquals(1, migrated.query("SELECT COUNT(*) FROM vehicles").use {
            it.moveToFirst(); it.getInt(0)
        })
    }

    @Test
    fun migratedDatabaseReopensWithTheRepositoryWithoutReseeding() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO part_categories VALUES ('c-1','Lampu stop',NULL,0)")
            db.execSQL(
                "INSERT INTO parts VALUES ('p-1','c-1','Lampu stop kiri','Toyota','81560-0K290',1,NULL,NULL,0)",
            )
            db.execSQL("INSERT INTO catalog_metadata VALUES ('catalog',1)")
        }
        helper.runMigrationsAndValidate(dbName, 2, true, CocokDatabase.MIGRATION_1_2).close()

        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, CocokDatabase::class.java, dbName)
            .addMigrations(CocokDatabase.MIGRATION_1_2)
            .build()
        try {
            val catalog = kotlinx.coroutines.runBlocking {
                RoomCatalogRepository(database) { error("must not reseed") }.load()
            }
            assertEquals(listOf("p-1"), catalog.parts.map { it.id })
            assertTrue(catalog.parts.single().specifications.isEmpty())
            assertTrue(catalog.matchEvidence.isEmpty())
            assertEquals(1, catalog.parts.single().partNumber?.let { 1 } ?: 0)
            assertTrue(SeedData.catalog.parts.isNotEmpty())
        } finally {
            database.close()
        }
    }
}
