package com.allen.pokemon.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allen.pokemon.core.model.PokemonDetail
import com.allen.pokemon.data.repository.DetailRefreshResult
import com.allen.pokemon.data.repository.PokemonDetailRepository
import com.allen.pokemon.data.network.AlwaysOnlineNetworkMonitor
import com.allen.pokemon.data.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val pokemon: PokemonDetail? = null,
    val evolvesFromPokemon: PokemonDetail? = null,
    val isLoading: Boolean = true,
    val error: DetailLoadError? = null,
    val speciesIssue: DetailLoadError? = null,
    val evolutionIssue: DetailLoadError? = null,
) {
    @Deprecated("Use speciesIssue to retain the reason for the unavailable data.")
    val speciesUnavailable: Boolean
        get() = speciesIssue != null

    @Deprecated("Use evolutionIssue to retain the reason for the unavailable data.")
    val evolutionUnavailable: Boolean
        get() = evolutionIssue != null
}

sealed interface DetailLoadError {
    data object Offline : DetailLoadError
    data class Api(val message: String) : DetailLoadError
    data object NotDownloaded : DetailLoadError
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PokemonDetailRepository,
    savedStateHandle: SavedStateHandle,
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
) : ViewModel() {
    private val pokemonId: Int = savedStateHandle.get<Int>("pokemonId") ?: 0

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private val _imageReloadKey = MutableStateFlow(0)

    /**
     * Bumped every time connectivity returns, so the detail artwork that failed to
     * load while offline is re-requested instead of staying in its remembered error state.
     */
    val imageReloadKey: StateFlow<Int> = _imageReloadKey.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline
                .collect { isOnline ->
                    if (isOnline) {
                        if (_uiState.value.isLoading || _uiState.value.hasOfflineIssue) {
                            loadDetail()
                        }
                    } else {
                        showOfflineState()
                    }
                }
        }
        viewModelScope.launch {
            networkMonitor.isOnline
                .collect { isOnline ->
                    if (isOnline) _imageReloadKey.value += 1
                }
        }
    }

    private fun loadDetail() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Read the current StateFlow value before starting any Retrofit work. This
            // makes a detail page opened while offline fail locally instead of waiting
            // for the HTTP client's connection timeout.
            if (!networkMonitor.isOnline.value) {
                renderOfflineState()
                return@launch
            }
            try {
                val cachedPokemon = repository.getPokemonDetail(pokemonId)
                if (cachedPokemon != null) {
                    // Room remains readable immediately; the network refresh only enriches it.
                    // Clear the old offline notice before retrying, so recovery does
                    // not leave a stale Retry action visible during the refresh.
                    _uiState.value = DetailUiState(
                        pokemon = cachedPokemon,
                        isLoading = false,
                    )
                } else {
                    _uiState.value = DetailUiState(isLoading = true)
                }
                val speciesIssue = repository.refreshSpeciesIfMissing(pokemonId).toLoadError()
                if (cachedPokemon == null && speciesIssue != null) {
                    _uiState.value = DetailUiState(
                        isLoading = false,
                        error = speciesIssue,
                    )
                    return@launch
                }

                val pokemon = repository.getPokemonDetail(pokemonId) ?: cachedPokemon
                if (pokemon != null) {
                    var evolvesFromPokemon: PokemonDetail? = null
                    var evolutionIssue: DetailLoadError? = null
                    if (pokemon.evolvesFromSpeciesId != null) {
                        evolutionIssue = repository.refreshSpeciesIfMissing(pokemon.evolvesFromSpeciesId).toLoadError()
                        evolvesFromPokemon = repository.getPokemonDetail(pokemon.evolvesFromSpeciesId)
                    }

                    _uiState.value = DetailUiState(
                        pokemon = pokemon,
                        evolvesFromPokemon = evolvesFromPokemon,
                        isLoading = false,
                        speciesIssue = speciesIssue,
                        evolutionIssue = evolutionIssue,
                    )
                } else {
                    _uiState.value = DetailUiState(
                        isLoading = false,
                        error = DetailLoadError.NotDownloaded,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = DetailLoadError.Api(e.message ?: "Unexpected API error"),
                )
            }
        }
    }

    private fun showOfflineState() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            renderOfflineState()
        }
    }

    private suspend fun renderOfflineState() {
        val cachedPokemon = repository.getPokemonDetail(pokemonId)
        if (cachedPokemon == null) {
            _uiState.value = DetailUiState(
                isLoading = false,
                error = DetailLoadError.Offline,
            )
        } else {
            // The species row can identify the previous evolution, but the section
            // still needs that Pokémon's cached core data for its name and image.
            // Render it from the cache when present; when it is known but absent
            // offline, keep the recovery path explicit instead of omitting the block.
            val cachedEvolution = cachedPokemon.evolvesFromSpeciesId?.let { preId ->
                repository.getPokemonDetail(preId)
            }
            _uiState.value = DetailUiState(
                pokemon = cachedPokemon,
                evolvesFromPokemon = cachedEvolution,
                isLoading = false,
                speciesIssue = if (cachedPokemon.description == null) DetailLoadError.Offline else null,
                evolutionIssue = cachedPokemon.evolvesFromSpeciesId
                    ?.takeIf { cachedEvolution == null }
                    ?.let { DetailLoadError.Offline },
            )
        }
    }

    fun retry() {
        loadDetail()
    }

    private val DetailUiState.hasOfflineIssue: Boolean
        get() = error == DetailLoadError.Offline ||
            speciesIssue == DetailLoadError.Offline ||
            evolutionIssue == DetailLoadError.Offline

    private fun DetailRefreshResult.toLoadError(): DetailLoadError? = when (this) {
        DetailRefreshResult.Available -> null
        DetailRefreshResult.Unavailable,
        DetailRefreshResult.Offline,
        -> DetailLoadError.Offline
        is DetailRefreshResult.ApiError -> DetailLoadError.Api(message)
    }
}
