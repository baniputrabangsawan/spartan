package tech.cocok.app.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.CatalogRepository
import tech.cocok.app.domain.model.Confirmation
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.FitmentGroup
import tech.cocok.app.domain.model.GroupPart
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Sumber
import tech.cocok.app.domain.model.Vehicle

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "catalog-test-${UUID.randomUUID()}.db"
    private var database: CocokDatabase? = null

    @After
    fun closeAndDeleteOwnDatabase() {
        try {
            closeDatabase()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun additionsStatusAndMetadataSurviveCloseAndReopen() = runBlocking {
        val repository: CatalogRepository = RoomCatalogRepository(openDatabase(), seed = { SeedData.catalog })
        val initial = repository.load()
        assertCatalogEquals(SeedData.catalog, initial)

        val part = Part(
            id = "p-local",
            categoryId = SeedData.lampuStop.id,
            name = "Lampu stop lokal",
            brand = "Merek toko",
            partNumber = "LOCAL-001",
            isOriginal = false,
            photoKey = "parts/local/photo.jpg",
            notes = "Catatan pemilik\nDiperiksa di toko.",
        )
        val group = FitmentGroup(
            id = "g-local",
            categoryId = part.categoryId,
            label = "Lampu stop lokal kiri",
            notes = "Soket berbeda dari model lama.",
        )
        val vehicle = Vehicle(
            id = "v-local",
            brand = "Toyota",
            model = "Hilux",
            generation = "Varian lokal",
            yearFrom = 2022,
            yearTo = null,
            attributes = linkedMapOf("mesin" to "2.4 diesel", "kabin\"" to "ganda\nbaris kedua"),
            verified = false,
        )
        val fitment = Fitment(
            id = "f-local",
            groupId = group.id,
            vehicleId = vehicle.id,
            yearFrom = 2022,
            yearTo = 2025,
            constraints = linkedMapOf("sisi" to "kiri", "soket" to "3 pin: \"A\"\n12V"),
            status = Status.MUNGKIN,
            sumber = Sumber.KATALOG,
        )
        val confirmation = Confirmation(
            id = "confirmation-local",
            fitmentId = fitment.id,
            groupId = group.id,
            oleh = "Pemilik toko",
            role = Role.PEMILIK,
            hasil = false,
            catatan = "Soket tidak cocok saat dicoba.",
            waktu = "2026-11-18T09:30:00+07:00",
        )
        val expected = initial.copy(
            parts = initial.parts + part,
            groups = initial.groups + group,
            groupParts = initial.groupParts + GroupPart(group.id, part.id, Status.MUNGKIN),
            vehicles = initial.vehicles + vehicle,
            fitments = initial.fitments + fitment,
            synonyms = initial.synonyms + mapOf("lampu lokal" to "lampu stop", "rebon" to "reborn lokal"),
        ).tambahKonfirmasi(confirmation).tetapkanStatus(
            fitmentId = initial.fitments.first().id,
            status = Status.TIDAK_COCOK,
            oleh = Role.PEMILIK,
            waktu = "2026-11-18T10:00:00+07:00",
        )

        assertCatalogEquals(expected, repository.update { expected })
        closeDatabase()

        val restored = repositoryWithoutSeed().load()
        assertCatalogEquals(expected, restored)
        assertEquals(Status.TIDAK_COCOK, restored.fitments.single { it.id == fitment.id }.status)
        assertEquals(confirmation.waktu, restored.fitments.single { it.id == fitment.id }.confirmedAt)
    }

    @Test
    fun invalidForeignKeyRollsBackTheWholeUpdateAndRetainsPriorState() = runBlocking {
        val repository: CatalogRepository = RoomCatalogRepository(openDatabase(), seed = { SeedData.catalog })
        val committedPart = SeedData.pStopRebornAsli.copy(id = "p-already-committed", partNumber = "SAVED-001")
        val prior = repository.update { it.copy(parts = it.parts + committedPart) }
        val rejectedPart = committedPart.copy(id = "p-must-roll-back", partNumber = "REJECTED-001")

        val failure = runCatching {
            repository.update {
                it.copy(
                    parts = it.parts + rejectedPart,
                    groupParts = it.groupParts + GroupPart("g-does-not-exist", rejectedPart.id, Status.PASTI),
                    synonyms = it.synonyms + ("rollback" to "must not persist"),
                )
            }
        }.exceptionOrNull()

        assertNotNull("An invalid group foreign key must reject the update", failure)
        assertCatalogEquals(prior, repository.load())
        closeDatabase()

        val restored = repositoryWithoutSeed().load()
        assertCatalogEquals(prior, restored)
        assertEquals(committedPart, restored.parts.single { it.id == committedPart.id })
        assertFalse("The otherwise valid part must roll back too", restored.parts.any { it.id == rejectedPart.id })
        assertFalse(restored.synonyms.containsKey("rollback"))
    }

    @Test
    fun concurrentRepositoryInstancesRetainBothAdditions() = runBlocking {
        val db = openDatabase()
        val first: CatalogRepository = RoomCatalogRepository(db, seed = { SeedData.catalog })
        val second: CatalogRepository = RoomCatalogRepository(db, seed = { error("Database is already initialized") })
        val initial = first.load()
        assertCatalogEquals(initial, second.load())
        val firstPart = SeedData.pStopRebornAsli.copy(id = "p-concurrent-first", partNumber = "CONCURRENT-001")
        val secondPart = SeedData.pStopRebornAsli.copy(id = "p-concurrent-second", partNumber = "CONCURRENT-002")
        val start = CompletableDeferred<Unit>()

        val updates = listOf(first to firstPart, second to secondPart).map { (repository, part) ->
            async(Dispatchers.Default) {
                start.await()
                repository.update { it.copy(parts = it.parts + part) }
            }
        }
        start.complete(Unit)
        updates.awaitAll()
        val expected = initial.copy(parts = initial.parts + firstPart + secondPart)
        assertCatalogEquals(expected, first.load())
        assertCatalogEquals(expected, second.load())
        closeDatabase()

        assertCatalogEquals(expected, repositoryWithoutSeed().load())
    }

    @Test
    fun emptyInitializedCatalogIsNotReseeded() = runBlocking {
        val repository: CatalogRepository = RoomCatalogRepository(openDatabase(), seed = { Catalog() })
        assertCatalogEquals(Catalog(), repository.load())
        closeDatabase()

        assertCatalogEquals(Catalog(), repositoryWithoutSeed().load())
    }

    private fun openDatabase(): CocokDatabase {
        check(database == null) { "Close the previous database handle before reopening" }
        return CocokDatabase.open(context, databaseName).also { database = it }
    }

    private fun closeDatabase() {
        database?.close()
        database = null
    }

    private fun repositoryWithoutSeed(): CatalogRepository = RoomCatalogRepository(
        openDatabase(),
        seed = { error("Persisted catalogs, including empty ones, must never be reseeded") },
    )

    private fun assertCatalogEquals(expected: Catalog, actual: Catalog) {
        assertEquals(expected.canonicalOrder(), actual.canonicalOrder())
    }

    private fun Catalog.canonicalOrder(): Catalog = copy(
        vehicles = vehicles.sortedBy { it.id },
        categories = categories.sortedBy { it.id },
        parts = parts.sortedBy { it.id },
        groups = groups.sortedBy { it.id },
        groupParts = groupParts.sortedWith(compareBy({ it.groupId }, { it.partId })),
        fitments = fitments.sortedBy { it.id },
        confirmations = confirmations.sortedBy { it.id },
    )
}
