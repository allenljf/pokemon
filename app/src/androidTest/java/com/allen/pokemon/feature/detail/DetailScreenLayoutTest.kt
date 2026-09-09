package com.allen.pokemon.feature.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.allen.pokemon.core.model.PokemonDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailPlacesArtworkIdentityEvolutionAndDescriptionInAssignmentOrder() {
        composeRule.setContent {
            DetailScreenContent(
                uiState = DetailUiState(
                    pokemon = PokemonDetail(
                        id = 6,
                        name = "charizard",
                        types = listOf("fire", "flying"),
                        imageUrl = null,
                        description = "Spits fire that is hot enough to melt boulders.",
                    ),
                    evolvesFromPokemon = PokemonDetail(
                        id = 5,
                        name = "charmeleon",
                        types = listOf("fire"),
                        imageUrl = null,
                        description = null,
                    ),
                    isLoading = false,
                ),
                onBackClick = {},
                onNavigateToPokemon = {},
                onRetry = {},
            )
        }

        val numberTop = composeRule.onNodeWithTag("detail-number").fetchSemanticsNode().boundsInRoot.top
        val artworkTop = composeRule.onNodeWithTag("detail-artwork").fetchSemanticsNode().boundsInRoot.top
        val nameTop = composeRule.onNodeWithTag("detail-name").fetchSemanticsNode().boundsInRoot.top
        val typesTop = composeRule.onNodeWithTag("detail-types").fetchSemanticsNode().boundsInRoot.top
        val evolutionTop = composeRule.onNodeWithTag("detail-evolution").fetchSemanticsNode().boundsInRoot.top
        val descriptionTop = composeRule.onNodeWithTag("detail-description").fetchSemanticsNode().boundsInRoot.top

        assertTrue(numberTop < artworkTop)
        assertTrue(artworkTop < nameTop)
        assertTrue(nameTop < typesTop)
        assertTrue(typesTop < evolutionTop)
        assertTrue(evolutionTop < descriptionTop)
    }

    @Test
    fun tappingEvolvesFromLinkNavigatesToThePreEvolutionDetail() {
        val openedPokemonIds = mutableListOf<Int>()

        composeRule.setContent {
            DetailScreenContent(
                uiState = DetailUiState(
                    pokemon = PokemonDetail(
                        id = 6,
                        name = "charizard",
                        types = listOf("fire", "flying"),
                        imageUrl = null,
                        description = "Spits fire that is hot enough to melt boulders.",
                    ),
                    evolvesFromPokemon = PokemonDetail(
                        id = 5,
                        name = "charmeleon",
                        types = listOf("fire"),
                        imageUrl = null,
                        description = null,
                    ),
                    isLoading = false,
                ),
                onBackClick = {},
                onNavigateToPokemon = openedPokemonIds::add,
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("detail-evolution").performScrollTo().performClick()

        assertEquals(listOf(5), openedPokemonIds)
    }

    @Test
    fun offlineUnavailableStateShowsRetry() {
        composeRule.setContent {
            DetailScreenContent(
                uiState = DetailUiState(isLoading = false, error = DetailLoadError.Offline),
                onBackClick = {},
                onNavigateToPokemon = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("detail-offline-unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("You are offline").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-detail").assertIsDisplayed()
    }

    @Test
    fun apiErrorStateShowsRetry() {
        composeRule.setContent {
            DetailScreenContent(
                uiState = DetailUiState(isLoading = false, error = DetailLoadError.Api("HTTP 503")),
                onBackClick = {},
                onNavigateToPokemon = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Unable to load details").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-detail").assertIsDisplayed()
    }

    @Test
    fun detailContentStaysWithinTheHeaderEdges() {
        composeRule.setContent {
            DetailScreenContent(
                uiState = DetailUiState(
                    pokemon = PokemonDetail(
                        id = 25,
                        name = "pikachu",
                        types = listOf("electric"),
                        imageUrl = null,
                        description = null,
                    ),
                    isLoading = false,
                ),
                onBackClick = {},
                onNavigateToPokemon = {},
                onRetry = {},
            )
        }

        val backBounds = composeRule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val numberBounds = composeRule.onNodeWithTag("detail-number").fetchSemanticsNode().boundsInRoot
        val artworkBounds = composeRule.onNodeWithTag("detail-artwork").fetchSemanticsNode().boundsInRoot

        assertTrue(artworkBounds.left >= backBounds.left)
        assertTrue(artworkBounds.right <= numberBounds.right)
    }

    @Test
    fun detailArtworkDoesNotPaintTheSurfaceVariantBehindTheImage() {
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = Color.Green,
                    surface = Color.Red,
                    surfaceVariant = Color.Blue,
                    surfaceContainer = Color.Red,
                    surfaceContainerHigh = Color.Red,
                    surfaceContainerHighest = Color.Red,
                    surfaceContainerLow = Color.Red,
                    surfaceContainerLowest = Color.Red,
                ),
            ) {
                DetailScreenContent(
                    uiState = DetailUiState(
                        pokemon = PokemonDetail(
                            id = 25,
                            name = "pikachu",
                            types = listOf("electric"),
                            imageUrl = null,
                            description = null,
                        ),
                        isLoading = false,
                    ),
                    onBackClick = {},
                    onNavigateToPokemon = {},
                    onRetry = {},
                )
            }
        }

        val artwork = composeRule.onNodeWithTag("detail-artwork").captureToImage().toPixelMap()

        assertTrue(artwork[8, 8].green > artwork[8, 8].red + 0.1f)
        assertTrue(artwork[8, 8].green > artwork[8, 8].blue + 0.1f)
    }
}
