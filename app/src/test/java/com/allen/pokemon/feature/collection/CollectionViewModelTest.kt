package com.allen.pokemon.feature.collection

import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.repository.CollectionRepository
import com.allen.pokemon.data.repository.DetailRefreshResult
import com.allen.pokemon.data.repository.PokemonDetailRepository
import com.allen.pokemon.data.sync.PokemonSyncRemote
import com.allen.pokemon.data.sync.PokemonSynchronizer
import com.allen.pokemon.data.sync.SyncFailure
import com.allen.pokemon.testing.MutableNetworkMonitor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `collection state alphabetizes sections sorts ids and keeps dual type membership`() = runTest {
        val repository = FakeCollectionRepository(
            typeNames = listOf("water", "electric"),
            pokemonByType = mapOf(
                "electric" to listOf(pokemon(25, "pikachu"), pokemon(1, "bulbasaur")),
                "water" to listOf(pokemon(7, "squirtle"), pokemon(1, "bulbasaur")),
            ),
        )
        val viewModel = CollectionViewModel(repository, synchronizer(), detailRepository())

        advanceUntilIdle()

        assertEquals(listOf("electric", "water"), viewModel.uiState.value.sections.map { it.name })
        assertEquals(listOf(1, 25), viewModel.uiState.value.sections[0].pokemon.map { it.id })
        assertEquals(listOf(1, 7), viewModel.uiState.value.sections[1].pokemon.map { it.id })
        assertEquals(listOf(2, 2), viewModel.uiState.value.sections.map { it.count })
        assertEquals(
            listOf("electric", "water"),
            viewModel.uiState.value.sections.filter { section -> section.pokemon.any { it.id == 1 } }.map { it.name },
        )
    }

    @Test
    fun `partial collection retains content through a failed sync and successful retry`() = runTest {
        val repository = FakeCollectionRepository(
            typeNames = listOf("electric"),
            pokemonByType = mapOf("electric" to listOf(pokemon(25, "pikachu"))),
        )
        val viewModel = CollectionViewModel(repository, partialRetrySynchronizer(), detailRepository())

        advanceUntilIdle()
        repository.commit("electric", listOf(pokemon(25, "pikachu"), pokemon(26, "raichu")))
        advanceUntilIdle()

        assertEquals(listOf(25, 26), viewModel.uiState.value.sections.single().pokemon.map { it.id })
        assertEquals(2, viewModel.uiState.value.sections.single().count)
        assertTrue(viewModel.uiState.value.syncProgress < viewModel.uiState.value.totalToSync)
        assertEquals(SyncFailure.Offline, viewModel.uiState.value.syncFailure)
        viewModel.retrySync()
        advanceUntilIdle()
        assertEquals(listOf(25, 26), viewModel.uiState.value.sections.single().pokemon.map { it.id })
        assertEquals(null, viewModel.uiState.value.syncFailure)
    }

    @Test
    fun `first launch offline exposes a retryable empty state`() = runTest {
        val viewModel = CollectionViewModel(
            FakeCollectionRepository(typeNames = emptyList(), pokemonByType = emptyMap()),
            synchronizer(failIndex = true),
            detailRepository(),
        )

        advanceUntilIdle()

        assertEquals(SyncFailure.Offline, viewModel.uiState.value.syncFailure)
        assertTrue(viewModel.uiState.value.showsOfflineEmptyState)
    }

    @Test
    fun `api failure on first launch exposes a retryable empty state`() = runTest {
        val viewModel = CollectionViewModel(
            FakeCollectionRepository(typeNames = emptyList(), pokemonByType = emptyMap()),
            synchronizer(
                failIndex = true,
                indexFailure = HttpException(Response.error<Any>(503, "".toResponseBody())),
            ),
            detailRepository(),
        )

        advanceUntilIdle()

        assertEquals(SyncFailure.Api("HTTP 503"), viewModel.uiState.value.syncFailure)
        assertTrue(viewModel.uiState.value.showsFailedEmptyState)
        assertFalse(viewModel.uiState.value.showsOfflineEmptyState)
    }

    @Test
    fun `network recovery restarts sync and bumps the image reload key`() = runTest {
        val monitor = MutableNetworkMonitor(initialOnline = false)
        var indexFetches = 0
        val synchronizer = PokemonSynchronizer(
            remote = object : PokemonSyncRemote {
                override suspend fun fetchIndex(): List<PokemonReference> {
                    indexFetches += 1
                    return emptyList()
                }

                override suspend fun fetchCore(id: Int): PokemonCoreSnapshot = error("unused")
            },
            store = emptyStore(),
        )
        val viewModel = CollectionViewModel(
            FakeCollectionRepository(typeNames = emptyList(), pokemonByType = emptyMap()),
            synchronizer,
            detailRepository(),
            monitor,
        )

        advanceUntilIdle()
        assertEquals(SyncFailure.Offline, viewModel.uiState.value.syncFailure)
        assertEquals(0, indexFetches)
        assertEquals(0, viewModel.imageReloadKey.value)

        monitor.setOnline(true)
        advanceUntilIdle()

        assertTrue(indexFetches >= 1)
        assertTrue(viewModel.imageReloadKey.value >= 1)
        assertEquals(null, viewModel.uiState.value.syncFailure)
    }

    @Test
    fun `recreated view model retains persisted capture events`() = runTest {
        val repository = FakeCollectionRepository(
            typeNames = emptyList(),
            pokemonByType = emptyMap(),
            captures = listOf(CapturedPokemon("capture-1", pokemonId = 25, capturedAt = 1_000L, pokemonName = "pikachu", imageUrl = null)),
        )

        val first = CollectionViewModel(repository, synchronizer(), detailRepository())
        advanceUntilIdle()
        val recreated = CollectionViewModel(repository, synchronizer(), detailRepository())
        advanceUntilIdle()

        assertEquals(listOf("capture-1"), first.uiState.value.captures.map { it.captureId })
        assertEquals(listOf("capture-1"), recreated.uiState.value.captures.map { it.captureId })
    }

    private fun synchronizer(
        failIndex: Boolean = false,
        indexFailure: Throwable = IOException("offline"),
    ): PokemonSynchronizer = PokemonSynchronizer(
        remote = object : PokemonSyncRemote {
            override suspend fun fetchIndex(): List<PokemonReference> {
                if (failIndex) throw indexFailure
                return emptyList()
            }

            override suspend fun fetchCore(id: Int): PokemonCoreSnapshot = error("unused")
        },
        store = emptyStore(),
    )

    private fun emptyStore(): PokemonStore = object : PokemonStore {
        override suspend fun seedPending(references: List<PokemonReference>) = Unit
        override suspend fun pendingCore(limit: Int): List<Int> = emptyList()
        override suspend fun commitCore(snapshot: PokemonCoreSnapshot) = Unit
        override suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure) = Unit
        override suspend fun completedCoreCount(): Int = 0
    }

    private fun partialRetrySynchronizer(): PokemonSynchronizer = PokemonSynchronizer(
        remote = object : PokemonSyncRemote {
            private var shouldFailSecondCore = true

            override suspend fun fetchIndex(): List<PokemonReference> =
                (1..151).map { PokemonReference(it, "pokemon-$it") }

            override suspend fun fetchCore(id: Int): PokemonCoreSnapshot {
                if (id == 2 && shouldFailSecondCore) {
                    shouldFailSecondCore = false
                    throw IOException("offline")
                }
                return PokemonCoreSnapshot(id, "pokemon-$id", imageUrl = null, typeNames = listOf("electric"))
            }
        },
        store = object : PokemonStore {
            private val completed = mutableSetOf<Int>()

            override suspend fun seedPending(references: List<PokemonReference>) = Unit
            override suspend fun pendingCore(limit: Int): List<Int> = when {
                completed.isEmpty() -> listOf(1, 2)
                2 !in completed -> listOf(2)
                else -> emptyList()
            }
            override suspend fun commitCore(snapshot: PokemonCoreSnapshot) {
                completed += snapshot.id
            }
            override suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure) = Unit
            override suspend fun completedCoreCount(): Int = completed.size
        },
    )

    private fun pokemon(id: Int, name: String) = Pokemon(id, name, emptyList(), imageUrl = null)

    private fun detailRepository(): PokemonDetailRepository = object : PokemonDetailRepository {
        override suspend fun getPokemonDetail(pokemonId: Int) = null
        override suspend fun refreshSpeciesIfMissing(speciesId: Int) = DetailRefreshResult.Available
    }

    private class FakeCollectionRepository(
        typeNames: List<String>,
        pokemonByType: Map<String, List<Pokemon>>,
        captures: List<CapturedPokemon> = emptyList(),
    ) : CollectionRepository {
        private val types = MutableStateFlow(typeNames)
        private val pokemon = pokemonByType.mapValues { MutableStateFlow(it.value) }.toMutableMap()
        private val captures = MutableStateFlow(captures)

        override fun getAllTypes(): Flow<List<String>> = types
        override fun getPokemonByType(typeName: String): Flow<List<Pokemon>> = pokemon.getValue(typeName)
        override fun getCompletedPokemonIds(): Flow<List<Int>> = flowOf(emptyList())
        override fun getTypeCount(typeName: String): Flow<Int> = pokemon.getValue(typeName).map { it.size }
        override fun getAllCaptures(): Flow<List<CapturedPokemon>> = captures
        override suspend fun addCapture(pokemonId: Int): String = error("unused")
        override suspend fun removeCapture(captureId: String) = error("unused")

        fun commit(typeName: String, pokemon: List<Pokemon>) {
            this.pokemon.getValue(typeName).value = pokemon
        }
    }
}
