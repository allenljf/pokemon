package com.allen.pokemon.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.allen.pokemon.core.model.PokemonDetail
import com.allen.pokemon.data.repository.DetailRefreshResult
import com.allen.pokemon.data.repository.PokemonDetailRepository
import com.allen.pokemon.testing.MutableNetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
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
    fun `cached core detail remains visible when missing species cannot load offline`() = runTest {
        val cachedDetail = PokemonDetail(
            id = 25,
            name = "pikachu",
            types = listOf("electric"),
            imageUrl = "https://example.test/pikachu.png",
            description = null,
        )
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = cachedDetail
                override suspend fun refreshSpeciesIfMissing(speciesId: Int) = DetailRefreshResult.Unavailable
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 25)),
        )

        advanceUntilIdle()

        assertEquals(cachedDetail, viewModel.uiState.value.pokemon)
        assertTrue(viewModel.uiState.value.speciesUnavailable)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `offline detail without cached core shows an offline error`() = runTest {
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = null
                override suspend fun refreshSpeciesIfMissing(speciesId: Int) = DetailRefreshResult.Offline
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 25)),
            networkMonitor = MutableNetworkMonitor(initialOnline = false),
        )

        advanceUntilIdle()

        assertEquals(DetailLoadError.Offline, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.pokemon)
    }

    @Test
    fun `api error for an uncached detail shows a readable error`() = runTest {
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = null
                override suspend fun refreshSpeciesIfMissing(speciesId: Int) =
                    DetailRefreshResult.ApiError("HTTP 500")
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 25)),
            networkMonitor = MutableNetworkMonitor(initialOnline = true),
        )

        advanceUntilIdle()

        assertEquals(DetailLoadError.Api("HTTP 500"), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `never downloaded detail shows an explicit unavailable state`() = runTest {
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = null
                override suspend fun refreshSpeciesIfMissing(speciesId: Int) = DetailRefreshResult.Available
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 25)),
            networkMonitor = MutableNetworkMonitor(initialOnline = true),
        )

        advanceUntilIdle()

        assertEquals(DetailLoadError.NotDownloaded, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `network recovery reloads an offline detail and bumps the image reload key`() = runTest {
        val monitor = MutableNetworkMonitor(initialOnline = false)
        var speciesFetches = 0
        val cached = PokemonDetail(
            id = 25,
            name = "pikachu",
            types = listOf("electric"),
            imageUrl = "https://example.test/pikachu.png",
            description = null,
        )
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = cached
                override suspend fun refreshSpeciesIfMissing(speciesId: Int): DetailRefreshResult {
                    speciesFetches += 1
                    return DetailRefreshResult.Available
                }
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 25)),
            networkMonitor = monitor,
        )

        advanceUntilIdle()
        assertEquals(DetailLoadError.Offline, viewModel.uiState.value.speciesIssue)
        assertEquals(0, viewModel.imageReloadKey.value)
        assertEquals(0, speciesFetches)

        monitor.setOnline(true)
        advanceUntilIdle()

        assertTrue(viewModel.imageReloadKey.value >= 1)
        assertTrue(speciesFetches >= 1)
        assertNull(viewModel.uiState.value.speciesIssue)
    }

    @Test
    fun `detail renders the pre-evolution species`() = runTest {
        val main = PokemonDetail(
            id = 6,
            name = "charizard",
            types = listOf("fire", "flying"),
            imageUrl = null,
            description = "Spits fire that is hot enough to melt boulders.",
            evolvesFromSpeciesId = 5,
        )
        val preEvolution = PokemonDetail(
            id = 5,
            name = "charmeleon",
            types = listOf("fire"),
            imageUrl = null,
            description = null,
        )
        val viewModel = DetailViewModel(
            repository = object : PokemonDetailRepository {
                override suspend fun getPokemonDetail(pokemonId: Int): PokemonDetail? = when (pokemonId) {
                    6 -> main
                    5 -> preEvolution
                    else -> null
                }

                override suspend fun refreshSpeciesIfMissing(speciesId: Int) = DetailRefreshResult.Available
            },
            savedStateHandle = SavedStateHandle(mapOf("pokemonId" to 6)),
            networkMonitor = MutableNetworkMonitor(initialOnline = true),
        )

        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.evolvesFromPokemon?.id)
        assertEquals("charmeleon", viewModel.uiState.value.evolvesFromPokemon?.name)
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.evolutionIssue)
    }
}
