package com.allen.pokemon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.entity.CaptureEntity
import com.allen.pokemon.data.local.entity.PokemonTypeCrossRef
import com.allen.pokemon.data.local.entity.SyncPhaseStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPokemonStoreTest {
    private lateinit var database: PokemonDatabase
    private lateinit var store: RoomPokemonStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PokemonDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomPokemonStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun coreStatus_isTheSingleCheckpointAndReseedingPreservesIt() = runBlocking {
        store.seedPending(listOf(PokemonReference(id = 25, name = "pikachu")))

        val initial = database.pokemonDao().getSyncRecord(25)!!
        assertEquals("pikachu", initial.name)
        assertEquals(SyncPhaseStatus.PENDING, initial.coreStatus)
        assertNull(database.pokemonDao().getPokemonById(25))
        assertEquals(0, store.completedCoreCount())

        store.commitCore(snapshot(id = 25, name = "pikachu", types = listOf("electric")))

        store.seedPending(listOf(PokemonReference(id = 25, name = "stale-index-name")))
        val committed = database.pokemonDao().getSyncRecord(25)!!
        assertEquals(SyncPhaseStatus.COMPLETE, committed.coreStatus)
        assertEquals("pikachu", committed.name)
        assertEquals(1, store.completedCoreCount())
        assertEquals(emptyList<Int>(), store.pendingCore(limit = 10))

        store.seedPending(listOf(PokemonReference(id = 24, name = "arbok")))
        store.seedPending(listOf(PokemonReference(id = 26, name = "raichu")))
        store.markCoreFailure(26, PersistedSyncFailure(isRetryable = true))
        store.seedPending(listOf(PokemonReference(id = 27, name = "sandshrew")))
        store.markCoreFailure(27, PersistedSyncFailure(isRetryable = false))
        store.seedPending(listOf(PokemonReference(26, "raichu"), PokemonReference(27, "sandshrew")))

        assertEquals(listOf(24, 26), store.pendingCore(limit = 10))
    }

    @Test
    fun commitCore_replacesStaleTypeMembershipsAndKeepsDistinctCurrentTypes() = runBlocking {
        store.seedPending(listOf(PokemonReference(id = 6, name = "charizard")))
        store.commitCore(snapshot(id = 6, name = "charizard", types = listOf("fire", "flying")))
        store.commitCore(snapshot(id = 6, name = "charizard", types = listOf("fire", "dragon")))

        assertEquals(listOf("dragon", "fire"), database.pokemonDao().getTypesByPokemonId(6))
        assertEquals(1, database.pokemonDao().getTypeCountNow("fire"))
        assertEquals(1, database.pokemonDao().getTypeCountNow("dragon"))
        assertEquals(0, database.pokemonDao().getTypeCountNow("flying"))
    }

    @Test
    fun commitCore_publishesPokemonMembershipsAndCoreCompletionTogether() = runBlocking {
        store.seedPending(listOf(PokemonReference(id = 1, name = "bulbasaur")))
        val initialCountObserved = CompletableDeferred<Unit>()
        val publishedCounts = async {
            database.pokemonDao().getTypeCount("grass")
                .onEach { if (it == 0) initialCountObserved.complete(Unit) }
                .take(2)
                .toList()
        }
        initialCountObserved.await()

        store.commitCore(snapshot(id = 1, name = "bulbasaur", types = listOf("grass", "poison")))

        assertEquals("bulbasaur", database.pokemonDao().getPokemonById(1)?.name)
        assertEquals(listOf("grass", "poison"), database.pokemonDao().getTypesByPokemonId(1))
        assertEquals(SyncPhaseStatus.COMPLETE, database.pokemonDao().getSyncRecord(1)?.coreStatus)
        assertEquals(listOf(0, 1), publishedCounts.await())
    }

    @Test
    fun commitCore_rollsBackRowsMembershipsAndCompletionWhenTheTransactionFails() = runBlocking {
        database.captureDao().insertCapture(CaptureEntity(id = "capture-1", pokemonId = 25, capturedAt = 1))
        val failingStore = RoomPokemonStore(database) { error("injected core commit failure") }
        failingStore.seedPending(listOf(PokemonReference(id = 25, name = "pikachu")))

        runCatching { failingStore.commitCore(snapshot(id = 25, name = "pikachu", types = listOf("electric"))) }

        assertNull(database.pokemonDao().getPokemonById(25))
        assertEquals(emptyList<String>(), database.pokemonDao().getTypesByPokemonId(25))
        assertEquals(SyncPhaseStatus.PENDING, database.pokemonDao().getSyncRecord(25)?.coreStatus)
        assertEquals("pikachu", database.pokemonDao().getSyncRecord(25)?.name)
        assertEquals(0, store.completedCoreCount())
        assertEquals("capture-1", database.captureDao().getCaptureById("capture-1")?.id)
    }

    @Test
    fun placeholdersAndStaleMembershipsAreHiddenFromEveryDisplayQuery() = runBlocking {
        store.seedPending(listOf(PokemonReference(25, "pikachu")))
        database.pokemonDao().insertPokemonTypeCrossRef(PokemonTypeCrossRef(25, "electric"))
        database.captureDao().insertCapture(CaptureEntity("capture-1", 25, 1))

        assertNull(database.pokemonDao().getPokemonById(25))
        assertEquals(emptyList<String>(), database.pokemonDao().getTypesByPokemonId(25))
        assertEquals(emptyList<String>(), database.pokemonDao().getAllTypes().first())
        assertEquals(0, database.pokemonDao().getAllPokemon().first().size)
        assertEquals(0, database.pokemonDao().getPokemonByType("electric").first().size)
        assertEquals(0, database.pokemonDao().getTypeCount("electric").first())
        assertEquals(0, database.pokemonDao().getTypeCountNow("electric"))
        assertEquals(0, database.captureDao().getAllCapturesWithPokemon().first().size)

        store.commitCore(snapshot(25, "pikachu", listOf("electric")))

        assertEquals(listOf("electric"), database.pokemonDao().getAllTypes().first())
        assertEquals(1, database.pokemonDao().getTypeCountNow("electric"))
        assertEquals(1, database.captureDao().getAllCapturesWithPokemon().first().size)
    }

    @Test
    fun failedReplacementPreservesPreviouslyCommittedDataAndCaptures() = runBlocking {
        store.commitCore(snapshot(6, "charizard", listOf("fire", "flying")))
        database.captureDao().insertCapture(CaptureEntity("first", 6, 1))
        database.captureDao().insertCapture(CaptureEntity("second", 6, 1))
        val failingStore = RoomPokemonStore(database) { error("injected failure") }

        runCatching { failingStore.commitCore(snapshot(6, "changed", listOf("dragon"))) }

        assertEquals("charizard", database.pokemonDao().getPokemonById(6)?.name)
        assertEquals(listOf("fire", "flying"), database.pokemonDao().getTypesByPokemonId(6))
        assertEquals(1, store.completedCoreCount())
        assertEquals(listOf("second", "first"), database.captureDao().getAllCapturesWithPokemon().first().map { it.captureId })
    }

    @Test
    fun diskReopenRetainsCheckpointsAndOnlySelectsIncompleteIds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "four-table-resume-test"
        context.deleteDatabase(databaseName)
        fun open() = Room.databaseBuilder(context, PokemonDatabase::class.java, databaseName).build()
        try {
            val first = open()
            try {
                val firstStore = RoomPokemonStore(first)
                firstStore.seedPending((1..4).map { PokemonReference(it, "pokemon-$it") })
                firstStore.commitCore(snapshot(1, "bulbasaur", listOf("grass", "poison")))
                firstStore.markCoreFailure(3, PersistedSyncFailure(true))
                firstStore.markCoreFailure(4, PersistedSyncFailure(false))
                first.captureDao().insertCapture(CaptureEntity("capture-1", 1, 1))
            } finally {
                first.close()
            }
            val reopened = open()
            try {
                val resumedStore = RoomPokemonStore(reopened)
                resumedStore.seedPending((1..4).map { PokemonReference(it, "pokemon-$it") })
                assertEquals(listOf(2, 3), resumedStore.pendingCore(10))
                assertEquals(1, resumedStore.completedCoreCount())
                assertEquals("bulbasaur", reopened.captureDao().getAllCapturesWithPokemon().first().single().pokemonName)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun capturedPokemon_join_includes_the_persisted_artwork() = runBlocking {
        store.seedPending(listOf(PokemonReference(id = 25, name = "pikachu")))
        store.commitCore(snapshot(id = 25, name = "pikachu", types = listOf("electric")))
        database.captureDao().insertCapture(CaptureEntity(id = "capture-1", pokemonId = 25, capturedAt = 1))

        val captures = database.captureDao().getAllCapturesWithPokemon().first()

        assertEquals(1, captures.size)
        assertEquals("capture-1", captures.single().captureId)
        assertEquals("pikachu", captures.single().pokemonName)
        assertEquals("https://example.test/25.png", captures.single().imageUrl)
    }

    private fun snapshot(id: Int, name: String, types: List<String>) = PokemonCoreSnapshot(
        id = id,
        name = name,
        imageUrl = "https://example.test/$id.png",
        typeNames = types,
    )
}
