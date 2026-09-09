package com.allen.pokemon.feature.collection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.data.sync.SyncFailure
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingCapturePromptsBeforeCapturing() {
        val capturedIds = mutableListOf<Int>()

        composeRule.setContent {
            CollectionContent(
                uiState = collectionState(),
                onCapture = capturedIds::add,
                onRelease = {},
                onRetry = {},
                onDismissFailure = {},
                onNavigateToDetail = {},
            )
        }

        composeRule.onNodeWithTag("capture-action-25").performClick()

        composeRule.onNodeWithText("Capture Pikachu?").assertIsDisplayed()
        assertEquals(emptyList<Int>(), capturedIds)
        composeRule.onNodeWithText("Capture").performClick()

        assertEquals(listOf(25), capturedIds)
    }

    @Test
    fun tappingReleasePromptsBeforeReleasing() {
        val releasedIds = mutableListOf<String>()

        composeRule.setContent {
            CollectionContent(
                uiState = collectionState(),
                onCapture = {},
                onRelease = releasedIds::add,
                onRetry = {},
                onDismissFailure = {},
                onNavigateToDetail = {},
            )
        }

        composeRule.onNodeWithTag("release-action-capture-25").performClick()

        composeRule.onNodeWithText("Release Pikachu?").assertIsDisplayed()
        assertEquals(emptyList<String>(), releasedIds)
        composeRule.onNodeWithText("Release").performClick()

        assertEquals(listOf("capture-25"), releasedIds)
    }

    @Test
    fun tappingPocketCardNavigatesToThePokemonDetail() {
        val openedPokemonIds = mutableListOf<Int>()

        composeRule.setContent {
            CollectionContent(
                uiState = collectionState(),
                onCapture = {},
                onRelease = {},
                onRetry = {},
                onDismissFailure = {},
                onNavigateToDetail = openedPokemonIds::add,
            )
        }

        composeRule.onNodeWithTag("capture-card-capture-25").performClick()

        assertEquals(listOf(25), openedPokemonIds)
    }

    @Test
    fun offlineFailureShowsNetworkOfflineChipAndRetry() {
        composeRule.setContent {
            CollectionContent(
                uiState = CollectionUiState(isLoading = false, syncFailure = SyncFailure.Offline),
                onCapture = {},
                onRelease = {},
                onRetry = {},
                onDismissFailure = {},
                onNavigateToDetail = {},
            )
        }

        composeRule.onNodeWithText("Network offline").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-sync").assertIsDisplayed()
    }

    @Test
    fun apiFailureShowsSyncErrorChipAndRetry() {
        composeRule.setContent {
            CollectionContent(
                uiState = CollectionUiState(isLoading = false, syncFailure = SyncFailure.Api("HTTP 503")),
                onCapture = {},
                onRelease = {},
                onRetry = {},
                onDismissFailure = {},
                onNavigateToDetail = {},
            )
        }

        composeRule.onNodeWithText("Sync error").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-sync").assertIsDisplayed()
    }

    @Test
    fun listAndPocketImagesDoNotPaintTheSurfaceVariantBehindTheImage() {
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
                CollectionContent(
                    uiState = collectionState(),
                    onCapture = {},
                    onRelease = {},
                    onRetry = {},
                    onDismissFailure = {},
                    onNavigateToDetail = {},
                )
            }
        }

        val pocketCard = composeRule.onNodeWithTag("capture-card-capture-25").captureToImage().toPixelMap()
        val listCard = composeRule.onNodeWithContentDescription("Open Pikachu details").captureToImage().toPixelMap()

        assertTrue(pocketCard[8, 8].green > pocketCard[8, 8].red + 0.1f)
        assertTrue(pocketCard[8, 8].green > pocketCard[8, 8].blue + 0.1f)
        assertTrue(listCard[8, 8].green > listCard[8, 8].red + 0.1f)
        assertTrue(listCard[8, 8].green > listCard[8, 8].blue + 0.1f)
    }

    private fun collectionState() = CollectionUiState(
        isLoading = false,
        captures = listOf(
            CapturedPokemon(
                captureId = "capture-25",
                pokemonId = 25,
                capturedAt = 1_000L,
                pokemonName = "Pikachu",
                imageUrl = null,
            ),
        ),
        sections = listOf(
            TypeSection(
                name = "electric",
                count = 1,
                pokemon = listOf(Pokemon(25, "Pikachu", listOf("electric"), imageUrl = null)),
            ),
        ),
    )
}
