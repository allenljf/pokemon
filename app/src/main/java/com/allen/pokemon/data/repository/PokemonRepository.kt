package com.allen.pokemon.data.repository

import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.core.model.PokemonDetail
import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.CaptureDao
import com.allen.pokemon.data.local.PokemonDao
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.local.SpeciesDao
import com.allen.pokemon.data.local.entity.CaptureEntity
import com.allen.pokemon.data.local.entity.SpeciesEntity
import com.allen.pokemon.data.network.AlwaysOnlineNetworkMonitor
import com.allen.pokemon.data.network.NetworkMonitor
import com.allen.pokemon.data.remote.PokemonApiService
import com.allen.pokemon.data.sync.PokemonSyncRemote
import com.allen.pokemon.data.sync.SyncFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DetailRefreshResult {
    data object Available : DetailRefreshResult
    /** Kept so existing callers with the old binary contract still compile. */
    data object Unavailable : DetailRefreshResult
    data object Offline : DetailRefreshResult
    data class ApiError(val message: String) : DetailRefreshResult
}

interface PokemonDetailRepository {
    suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail?
    suspend fun refreshSpeciesIfMissing(speciesId: Int): DetailRefreshResult
}

@Singleton
class PokemonRepository @Inject constructor(
    private val apiService: PokemonApiService,
    private val pokemonDao: PokemonDao,
    private val captureDao: CaptureDao,
    private val speciesDao: SpeciesDao,
    private val store: PokemonStore,
    private val captureClock: CaptureClock,
    private val captureIdGenerator: CaptureIdGenerator,
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
) : PokemonSyncRemote, CollectionRepository, PokemonDetailRepository {
    private val speciesRefreshLocks = ConcurrentHashMap<Int, Mutex>()

    override suspend fun fetchIndex(): List<PokemonReference> =
        apiService.getPokemonList(limit = 151).results.mapNotNull { reference ->
            reference.url.trimEnd('/').substringAfterLast('/').toIntOrNull()?.let { id ->
                PokemonReference(id = id, name = reference.name)
            }
        }

    override suspend fun fetchCore(id: Int): PokemonCoreSnapshot {
        val response = apiService.getPokemonDetail(id)
        return PokemonCoreSnapshot(
            id = response.id,
            name = response.name,
            imageUrl = response.sprites.other?.officialArtwork?.front_default,
            typeNames = response.types.map { it.type.name },
        )
    }

    override suspend fun refreshSpeciesIfMissing(speciesId: Int): DetailRefreshResult {
        if (speciesDao.getSpeciesById(speciesId)?.descriptionFormatVersion == SPECIES_FORMAT_VERSION) {
            return DetailRefreshResult.Available
        }
        if (!networkMonitor.isOnline.value) {
            return DetailRefreshResult.Offline
        }

        return speciesRefreshLocks.computeIfAbsent(speciesId) { Mutex() }.withLock {
            if (speciesDao.getSpeciesById(speciesId)?.descriptionFormatVersion == SPECIES_FORMAT_VERSION) {
                return@withLock DetailRefreshResult.Available
            }
            if (!networkMonitor.isOnline.value) {
                return@withLock DetailRefreshResult.Offline
            }
            try {
                val response = apiService.getPokemonSpecies(speciesId)

                val description = response.flavorTextEntries
                    .find { it.language.name == "en" }
                    ?.flavorText
                    ?.normalizeFlavorText()

                val evolvesFromId = response.evolvesFromSpecies?.url?.let {
                    it.split("/").dropLast(1).last().toIntOrNull()
                }

                speciesDao.insertSpecies(
                    SpeciesEntity(
                        id = speciesId,
                        description = description,
                        evolvesFromSpeciesId = evolvesFromId,
                        descriptionFormatVersion = SPECIES_FORMAT_VERSION,
                    )
                )
                DetailRefreshResult.Available
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (val failure = SyncFailure.from(e)) {
                    SyncFailure.Offline -> {
                        networkMonitor.reportNetworkFailure()
                        DetailRefreshResult.Offline
                    }
                    is SyncFailure.Api -> DetailRefreshResult.ApiError(failure.message)
                }
            }
        }
    }

    override fun getPokemonByType(typeName: String): Flow<List<Pokemon>> =
        pokemonDao.getPokemonByType(typeName).map { entities ->
            entities.map { entity ->
                Pokemon(
                    id = entity.id,
                    name = entity.name,
                    types = emptyList(),
                    imageUrl = entity.imageUrl,
                )
            }
        }

    override fun getCompletedPokemonIds(): Flow<List<Int>> =
        pokemonDao.getAllPokemon().map { pokemon -> pokemon.map { it.id } }

    override fun getAllTypes(): Flow<List<String>> = pokemonDao.getAllTypes()

    override fun getTypeCount(typeName: String): Flow<Int> = pokemonDao.getTypeCount(typeName)

    suspend fun getPokemonById(pokemonId: Int): Pokemon? {
        val entity = pokemonDao.getPokemonById(pokemonId) ?: return null
        val types = pokemonDao.getTypesByPokemonId(pokemonId)
        return Pokemon(
            id = entity.id,
            name = entity.name,
            types = types,
            imageUrl = entity.imageUrl,
        )
    }

    override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? {
        val pokemon = pokemonDao.getPokemonById(pokemonId) ?: return null
        val types = pokemonDao.getTypesByPokemonId(pokemonId)
        val species = speciesDao.getSpeciesById(pokemonId)
        
        return PokemonDetail(
            id = pokemon.id,
            name = pokemon.name,
            types = types,
            imageUrl = pokemon.imageUrl,
            description = species?.description,
            evolvesFromSpeciesId = species?.evolvesFromSpeciesId,
        )
    }

    override fun getAllCaptures(): Flow<List<CapturedPokemon>> =
        captureDao.getAllCapturesWithPokemon().map { rows ->
            rows.map { row ->
                CapturedPokemon(
                    captureId = row.captureId,
                    pokemonId = row.pokemonId,
                    capturedAt = row.capturedAt,
                    pokemonName = row.pokemonName,
                    imageUrl = row.imageUrl,
                )
            }
        }

    override suspend fun addCapture(pokemonId: Int): String {
        val id = captureIdGenerator.nextId()
        captureDao.insertCapture(
            CaptureEntity(
                id = id,
                pokemonId = pokemonId,
                capturedAt = captureClock.nowMillis(),
            )
        )
        return id
    }

    override suspend fun removeCapture(captureId: String) {
        val capture = captureDao.getCaptureById(captureId)
        if (capture != null) {
            captureDao.deleteCapture(capture)
        }
    }

    private fun String.normalizeFlavorText(): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u000C', '\n')
            .lineSequence()
            .joinToString("\n") { line -> line.trim().replace(Regex("[\\t ]+"), " ") }
            .trim()

    private companion object {
        // Version the complete species payload, rather than just the description:
        // a successful pokemon-species response supplies both fields together.
        const val SPECIES_FORMAT_VERSION = 2
    }
}
