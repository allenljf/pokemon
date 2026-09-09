package com.allen.pokemon.feature.detail

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.allen.pokemon.core.model.PokemonDetail
import com.allen.pokemon.ui.image.ProvideImageReloadKey
import com.allen.pokemon.ui.image.ReloadableAsyncImage
import com.allen.pokemon.ui.layout.ResponsiveScreenContainer

@Composable
fun DetailScreen(
    onBackClick: () -> Unit,
    onNavigateToPokemon: (Int) -> Unit,
    hingeBounds: Rect? = null,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageReloadKey by viewModel.imageReloadKey.collectAsState()

    ProvideImageReloadKey(reloadKey = imageReloadKey) {
        DetailScreenContent(
            uiState = uiState,
            onBackClick = onBackClick,
            onNavigateToPokemon = onNavigateToPokemon,
            onRetry = viewModel::retry,
            hingeBounds = hingeBounds,
        )
    }
}

@Composable
fun DetailScreenContent(
    uiState: DetailUiState,
    onBackClick: () -> Unit,
    onNavigateToPokemon: (Int) -> Unit,
    onRetry: () -> Unit,
    hingeBounds: Rect? = null,
) {
    ResponsiveScreenContainer(hingeBounds = hingeBounds) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = uiState.pokemon?.let { "#${it.id}" }.orEmpty(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .testTag("detail-number"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).testTag("detail-loading"),
                    )
                }
                uiState.error != null -> {
                    val error = requireNotNull(uiState.error)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .then(if (error == DetailLoadError.Offline) Modifier.testTag("detail-offline-unavailable") else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = error.title(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = error.message(),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry-detail"),
                        ) { Text("Retry") }
                    }
                }
                uiState.pokemon != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DetailContent(
                            pokemon = uiState.pokemon,
                            evolvesFromPokemon = uiState.evolvesFromPokemon,
                            speciesIssue = uiState.speciesIssue,
                            evolutionIssue = uiState.evolutionIssue,
                            onNavigateToPokemon = onNavigateToPokemon,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun OfflineDetailNotice(
    issue: DetailLoadError,
    onRetry: () -> Unit,
    tag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(tag),
    ) {
        Text(text = issue.message(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun DetailContent(
    pokemon: PokemonDetail,
    evolvesFromPokemon: PokemonDetail?,
    speciesIssue: DetailLoadError?,
    evolutionIssue: DetailLoadError?,
    onNavigateToPokemon: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .testTag("detail-artwork"),
            contentAlignment = Alignment.Center,
        ) {
            if (pokemon.imageUrl != null) {
                ReloadableAsyncImage(
                    model = pokemon.imageUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("No image available")
            }
        }

        Text(
            text = pokemon.name.replaceFirstChar { it.uppercase() },
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag("detail-name"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (pokemon.types.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("detail-types"),
                horizontalArrangement = Arrangement.Center,
            ) {
                pokemon.types.forEach { type ->
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    ) {
                        Text(
                            text = type.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (evolvesFromPokemon != null) {
            EvolvesFromSection(
                pokemon = evolvesFromPokemon,
                onClick = { onNavigateToPokemon(evolvesFromPokemon.id) },
            )
        } else if (evolutionIssue != null) {
            OfflineDetailNotice(
                issue = evolutionIssue,
                onRetry = onRetry,
                tag = "detail-evolution-offline",
            )
        }

        if (pokemon.description != null) {
            Text(
                text = pokemon.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .testTag("detail-description"),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )
        } else if (speciesIssue != null) {
            OfflineDetailNotice(
                issue = speciesIssue,
                onRetry = onRetry,
                tag = "detail-species-offline",
            )
        }
    }
}

private fun DetailLoadError.title(): String = when (this) {
    DetailLoadError.Offline -> "You are offline"
    is DetailLoadError.Api -> "Unable to load details"
    DetailLoadError.NotDownloaded -> "Details not downloaded"
}

private fun DetailLoadError.message(): String = when (this) {
    DetailLoadError.Offline -> "No network connection. Connect to the internet and try again."
    is DetailLoadError.Api -> "Something went wrong while loading this data. Please try again later."
    DetailLoadError.NotDownloaded -> "This Pokémon has not been downloaded yet. Connect to the internet and retry."
}

@Composable
private fun EvolvesFromSection(
    pokemon: PokemonDetail,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
            .clickable(onClick = onClick)
            .testTag("detail-evolution"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Evolves from",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pokemon.name.replaceFirstChar { it.uppercase() },
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (pokemon.imageUrl != null) {
                ReloadableAsyncImage(
                    model = pokemon.imageUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "#${pokemon.id}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
