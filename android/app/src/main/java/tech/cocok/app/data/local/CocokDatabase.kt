package tech.cocok.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VehicleEntity::class,
        PartCategoryEntity::class,
        PartEntity::class,
        FitmentGroupEntity::class,
        GroupPartEntity::class,
        FitmentEntity::class,
        SynonymEntity::class,
        ConfirmationEntity::class,
        MatchEvidenceEntity::class,
        CatalogMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(CatalogConverters::class)
abstract class CocokDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun open(context: Context, name: String = "cocok.db"): CocokDatabase =
            Room.databaseBuilder(context.applicationContext, CocokDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v2 adds editable specifications and scoped match evidence. Both are additive
         * with defaults, so existing rows keep their meaning and the seed flag stays put.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parts ADD COLUMN specifications TEXT NOT NULL DEFAULT '{}'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS match_evidence (
                        id TEXT NOT NULL PRIMARY KEY,
                        firstPartId TEXT NOT NULL,
                        secondPartId TEXT NOT NULL,
                        vehicleId TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        variant TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        note TEXT NOT NULL,
                        oleh TEXT NOT NULL,
                        role TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(firstPartId) REFERENCES parts(id),
                        FOREIGN KEY(secondPartId) REFERENCES parts(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_evidence_firstPartId ON match_evidence(firstPartId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_evidence_secondPartId ON match_evidence(secondPartId)")
            }
        }
    }
}
