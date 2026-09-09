package com.allen.pokemon.data.repository

import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.CaptureDao
import com.allen.pokemon.data.local.CapturedPokemonRow
import com.allen.pokemon.data.local.PokemonDao
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.local.SpeciesDao
import com.allen.pokemon.data.local.entity.CaptureEntity
import com.allen.pokemon.data.local.entity.PokemonEntity
import com.allen.pokemon.data.local.entity.PokemonTypeCrossRef
import com.allen.pokemon.data.local.entity.SpeciesEntity
import com.allen.pokemon.data.local.entity.SyncPhaseStatus
import com.allen.pokemon.data.remote.PokemonApiService
import com.allen.pokemon.data.remote.dto.PokemonDetailResponse
import com.allen.pokemon.data.remote.dto.PokemonListResponse
import com.allen.pokemon.data.remote.dto.PokemonSpeciesResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PokemonRepositoryCaptureTest {
    @Test
    fun `cached species detail is returned without a new network request`() = runBlocking {
        val repository = PokemonRepository(
            apiService = UnusedPokemonApiService,
            pokemonDao = UnusedPokemonDao,
            captureDao = FakeCaptureDao(),
            speciesDao = object : SpeciesDao {
                override suspend fun insertSpecies(species: SpeciesEntity) = error("unused")
                override suspend fun getSpeciesById(id: Int): SpeciesEntity? = SpeciesEntity(
                    id = id,
                    description = "Stored description",
                    evolvesFromSpeciesId = null,
                    descriptionFormatVersion = 2,
                )
            },
            store = UnusedPokemonStore,
            captureClock = CaptureClock { 0L },
            captureIdGenerator = CaptureIdGenerator { "unused" },
        )

        assertEquals(DetailRefreshResult.Available, repository.refreshSpeciesIfMissing(25))
    }

    @Test
    fun `duplicate captures keep distinct ids and sort newest first with deterministic ties`() = runBlocking {
        val captureDao = FakeCaptureDao()
        val idsToGenerate = ArrayDeque(listOf("z-first", "a-second"))
        val repository = repository(
            captureDao = captureDao,
            clock = CaptureClock { 1_000L },
            ids = CaptureIdGenerator { idsToGenerate.removeFirst() },
        )

        val firstId = repository.addCapture(pokemonId = 25)
        val secondId = repository.addCapture(pokemonId = 25)

        assertNotEquals(firstId, secondId)
        assertEquals(
            listOf("a-second", "z-first"),
            repository.getAllCaptures().first().map { it.captureId },
        )
    }

    @Test
    fun `releasing one capture leaves another capture of the same pokemon`() = runBlocking {
        val captureDao = FakeCaptureDao()
        val captureTimes = ArrayDeque(listOf(100L, 200L))
        val idsToGenerate = ArrayDeque(listOf("capture-1", "capture-2"))
        val repository = repository(
            captureDao = captureDao,
            clock = CaptureClock { captureTimes.removeFirst() },
            ids = CaptureIdGenerator { idsToGenerate.removeFirst() },
        )
        val firstId = repository.addCapture(pokemonId = 25)
        val secondId = repository.addCapture(pokemonId = 25)

        repository.removeCapture(secondId)

        assertEquals(listOf(firstId), repository.getAllCaptures().first().map { it.captureId })
    }

    @Test
    fun `captures persist across repository recreation without collapsing rapid taps`() = runBlocking {
        val captureDao = FakeCaptureDao()
        val firstRepository = repository(
            captureDao = captureDao,
            clock = CaptureClock { 1_000L },
            ids = CaptureIdGenerator { "capture-1" },
        )
        firstRepository.addCapture(pokemonId = 25)
        val recreatedRepository = repository(
            captureDao = captureDao,
            clock = CaptureClock { 1_000L },
            ids = CaptureIdGenerator { "capture-2" },
        )

        recreatedRepository.addCapture(pokemonId = 25)

        assertEquals(
            listOf("capture-2", "capture-1"),
            recreatedRepository.getAllCaptures().first().map { it.captureId },
        )
    }

    private fun repository(
        captureDao: CaptureDao,
        clock: CaptureClock,
        ids: CaptureIdGenerator,
    ) = PokemonRepository(
        apiService = UnusedPokemonApiService,
        pokemonDao = UnusedPokemonDao,
        captureDao = captureDao,
        speciesDao = UnusedSpeciesDao,
        store = UnusedPokemonStore,
        captureClock = clock,
        captureIdGenerator = ids,
    )

    private class FakeCaptureDao : CaptureDao {
        private data class StoredCapture(val entity: CaptureEntity, val insertionOrder: Long)

        private val captures = linkedMapOf<String, StoredCapture>()
        private val state = MutableStateFlow<List<CaptureEntity>>(emptyList())
        private var insertionOrder = 0L

        override suspend fun insertCapture(capture: CaptureEntity) {
            captures[capture.id] = StoredCapture(capture, ++insertionOrder)
            publish()
        }

        override suspend fun deleteCapture(capture: CaptureEntity) {
            captures.remove(capture.id)
            publish()
        }

        override fun getAllCaptures(): Flow<List<CaptureEntity>> = state

        override fun getAllCapturesWithPokemon(): Flow<List<CapturedPokemonRow>> = state.map { entities ->
            entities.map { entity ->
                CapturedPokemonRow(
                    captureId = entity.id,
                    pokemonId = entity.pokemonId,
                    capturedAt = entity.capturedAt,
                    pokemonName = "pokemon-${entity.pokemonId}",
                    imageUrl = null,
                )
            }
        }

        override suspend fun getCaptureById(id: String): CaptureEntity? = captures[id]?.entity

        override fun getCaptureCount(): Flow<Int> = state.map { it.size }

        private fun publish() {
            state.value = captures.values.sortedWith(
                compareByDescending<StoredCapture> { it.entity.capturedAt }
                    .thenByDescending { it.insertionOrder },
            ).map { it.entity }
        }
    }

    private object UnusedPokemonApiService : PokemonApiService {
        override suspend fun getPokemonList(limit: Int, offset: Int): PokemonListResponse = error("unused")
        override suspend fun getPokemonDetail(id: Int): PokemonDetailResponse = error("unused")
        override suspend fun getPokemonSpecies(id: Int): PokemonSpeciesResponse = error("unused")
    }

    private object UnusedPokemonDao : PokemonDao {
        override suspend fun insertPendingPokemon(pokemon: PokemonEntity) = error("unused")
        override suspend fun getSyncRecord(id: Int): PokemonEntity? = error("unused")
        override suspend fun getPendingCoreIds(limit: Int): List<Int> = error("unused")
        override suspend fun updateCoreStatus(id: Int, status: SyncPhaseStatus) = error("unused")
        override suspend fun getCompletedCoreCount(): Int = error("unused")
        override suspend fun insertPokemon(pokemon: PokemonEntity) = error("unused")
        override suspend fun insertPokemonTypeCrossRef(crossRef: PokemonTypeCrossRef) = error("unused")
        override suspend fun deleteTypeMembershipsForPokemon(pokemonId: Int) = error("unused")
        override suspend fun getPokemonById(id: Int): PokemonEntity? = error("unused")
        override suspend fun getTypesByPokemonId(pokemonId: Int): List<String> = error("unused")
        override fun getAllPokemon(): Flow<List<PokemonEntity>> = error("unused")
        override fun getPokemonByType(typeName: String): Flow<List<PokemonEntity>> = error("unused")
        override fun getAllTypes(): Flow<List<String>> = error("unused")
        override fun getTypeCount(typeName: String): Flow<Int> = error("unused")
        override suspend fun getTypeCountNow(typeName: String): Int = error("unused")
    }

    private object UnusedSpeciesDao : SpeciesDao {
        override suspend fun insertSpecies(species: SpeciesEntity) = error("unused")
        override suspend fun getSpeciesById(id: Int): SpeciesEntity? = error("unused")
    }

    private object UnusedPokemonStore : PokemonStore {
        override suspend fun seedPending(references: List<PokemonReference>) = error("unused")
        override suspend fun pendingCore(limit: Int): List<Int> = error("unused")
        override suspend fun commitCore(snapshot: PokemonCoreSnapshot) = error("unused")
        override suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure) = error("unused")
        override suspend fun completedCoreCount(): Int = error("unused")
    }
}
