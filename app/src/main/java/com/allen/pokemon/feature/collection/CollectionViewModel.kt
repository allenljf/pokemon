package com.allen.pokemon.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.data.repository.CollectionRepository
import com.allen.pokemon.data.repository.DetailRefreshResult
import com.allen.pokemon.data.repository.PokemonDetailRepository
import com.allen.pokemon.data.network.AlwaysOnlineNetworkMonitor
import com.allen.pokemon.data.network.NetworkMonitor
import com.allen.pokemon.data.sync.PokemonSynchronizer
import com.allen.pokemon.data.sync.SyncFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TypeSection(
    val name: String,
    val count: Int,
    val pokemon: List<Pokemon>,
)

data class CollectionUiState(
    val captures: List<CapturedPokemon> = emptyList(),
    val sections: List<TypeSection> = emptyList(),
    val syncProgress: Int = 0,
    val totalToSync: Int = 151,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = true,
    val syncFailure: SyncFailure? = null,
) {
    val showsFailedEmptyState: Boolean
        get() = syncFailure != null && captures.isEmpty() && sections.isEmpty() && syncProgress == 0

    @Deprecated("Use showsFailedEmptyState so API failures are also represented.")
    val showsOfflineEmptyState: Boolean
        get() = syncFailure == SyncFailure.Offline && showsFailedEmptyState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val synchronizer: PokemonSynchronizer,
    private val detailRepository: PokemonDetailRepository,
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
) : ViewModel() {
    private val syncRequests = MutableStateFlow(0)
    private val speciesPrefetchGeneration = MutableStateFlow(0)
    private val speciesPrefetchFailure = MutableStateFlow<SyncFailure?>(null)
    private val _imageReloadKey = MutableStateFlow(0)

    /**
     * Bumped every time connectivity returns, so the collection can remount artwork
     * that failed to load while offline instead of leaving it stuck in an error state.
     */
    val imageReloadKey: StateFlow<Int> = _imageReloadKey.asStateFlow()
    private val completedPokemonIds = repository.getCompletedPokemonIds().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    private val captures = repository.getAllCaptures().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sections = repository.getAllTypes().flatMapLatest { typeNames ->
        if (typeNames.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(typeNames.sorted().map { typeName ->
                repository.getPokemonByType(typeName).combine(
                    repository.getTypeCount(typeName),
                ) { pokemon, count ->
                    TypeSection(name = typeName, count = count, pokemon = pokemon.sortedBy { it.id })
                }
            }) { it.toList() }
        }
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        captures,
        sections,
        synchronizer.syncState,
        speciesPrefetchFailure,
        networkMonitor.isOnline,
    ) { captures, typeSections, syncState, prefetchFailure, isOnline ->
        CollectionUiState(
            captures = captures,
            sections = typeSections,
            syncProgress = syncState.progress,
            totalToSync = syncState.total,
            isSyncing = syncState.isSyncing,
            isLoading = false,
            syncFailure = if (!isOnline) SyncFailure.Offline else syncState.failure ?: prefetchFailure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CollectionUiState(),
    )

    init {
        viewModelScope.launch {
            combine(networkMonitor.isOnline, syncRequests) { isOnline, _ -> isOnline }
                .collectLatest { isOnline ->
                    if (isOnline) {
                        runSyncRetry()
                    } else {
                        speciesPrefetchFailure.value = SyncFailure.Offline
                    }
                }
        }
        viewModelScope.launch {
            networkMonitor.isOnline
                .collect { isOnline ->
                    if (isOnline) _imageReloadKey.value += 1
                }
        }
        viewModelScope.launch {
            networkMonitor.isOnline
                .flatMapLatest { isOnline ->
                    if (isOnline) {
                        combine(
                            completedPokemonIds,
                            speciesPrefetchGeneration,
                        ) { pokemonIds, _ -> pokemonIds }
                            .conflate()
                            .map { pokemonIds -> prefetchCompletedSpecies(pokemonIds) }
                    } else {
                        speciesPrefetchFailure.value = SyncFailure.Offline
                        emptyFlow()
                    }
                }
                .collect()
        }
    }

    fun capturePokemon(pokemonId: Int) {
        viewModelScope.launch {
            repository.addCapture(pokemonId)
        }
    }

    fun releaseCapture(captureId: String) {
        viewModelScope.launch {
            repository.removeCapture(captureId)
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            syncRequests.update { it + 1 }
        }
    }

    /** Shared by the Retry button and a network recovery, so both paths restart identically. */
    private suspend fun runSyncRetry() {
        clearSpeciesPrefetchFailure(clearAllFailures = true)
        synchronizer.dismissFailure()
        speciesPrefetchGeneration.update { it + 1 }
        synchronizer.syncMissing()
    }

    private fun clearSpeciesPrefetchFailure(clearAllFailures: Boolean) {
        speciesPrefetchFailure.update { failure ->
            if (clearAllFailures || failure == SyncFailure.Offline) null else failure
        }
    }

    fun dismissSyncFailure() {
        viewModelScope.launch { synchronizer.dismissFailure() }
    }

    private suspend fun prefetchCompletedSpecies(pokemonIds: List<Int>) {
        pokemonIds.chunked(SPECIES_PREFETCH_BATCH_SIZE).forEach { batch ->
            val results = coroutineScope {
                batch.map { pokemonId ->
                    async { detailRepository.refreshSpeciesIfMissing(pokemonId) }
                }.awaitAll()
            }
            val failure = results.firstNotNullOfOrNull { it.toSyncFailure() }
            if (failure != null) {
                speciesPrefetchFailure.value = failure
                return
            }
        }
        speciesPrefetchFailure.value = null
    }

    private fun DetailRefreshResult.toSyncFailure(): SyncFailure? = when (this) {
        DetailRefreshResult.Available -> null
        DetailRefreshResult.Unavailable,
        DetailRefreshResult.Offline,
        -> SyncFailure.Offline
        is DetailRefreshResult.ApiError -> SyncFailure.Api(message)
    }

    private companion object {
        const val SPECIES_PREFETCH_BATCH_SIZE = 3
    }
}
