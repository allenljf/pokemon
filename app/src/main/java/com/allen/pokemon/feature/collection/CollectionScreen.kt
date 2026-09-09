package com.allen.pokemon.feature.collection

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.Pokemon
import com.allen.pokemon.data.sync.SyncFailure
import com.allen.pokemon.ui.image.ProvideImageReloadKey
import com.allen.pokemon.ui.image.ReloadableAsyncImage
import com.allen.pokemon.ui.layout.ResponsiveScreenContainer

@Composable
fun CollectionScreen(
    onNavigateToDetail: (Int) -> Unit,
    hingeBounds: Rect? = null,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageReloadKey by viewModel.imageReloadKey.collectAsState()

    ProvideImageReloadKey(reloadKey = imageReloadKey) {
        CollectionContent(
            uiState = uiState,
            onCapture = viewModel::capturePokemon,
            onRelease = viewModel::releaseCapture,
            onRetry = viewModel::retrySync,
            onDismissFailure = viewModel::dismissSyncFailure,
            onNavigateToDetail = onNavigateToDetail,
            hingeBounds = hingeBounds,
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun CollectionContent(
    uiState: CollectionUiState,
    onCapture: (Int) -> Unit,
    onRelease: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissFailure: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    hingeBounds: Rect? = null,
) {
    var pendingAction by remember { mutableStateOf<PendingCollectionAction?>(null) }

    ResponsiveScreenContainer(hingeBounds = hingeBounds) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("collection-loading"),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("collection-list")
                    .then(if (uiState.isSyncing && uiState.sections.isNotEmpty()) Modifier.testTag("collection-partial-content") else Modifier),
            ) {
                item {
                    SyncStatusRow(
                        current = uiState.syncProgress,
                        total = uiState.totalToSync,
                        isSyncing = uiState.isSyncing,
                        failure = uiState.syncFailure,
                        onRetry = onRetry,
                    )
                }

                item {
                    PocketSection(
                        captures = uiState.captures,
                        onReleaseRequested = { capture -> pendingAction = PendingCollectionAction.Release(capture) },
                        onNavigateToDetail = onNavigateToDetail,
                    )
                }

                items(
                    uiState.sections,
                    key = { it.name },
                ) { section ->
                    TypeSection(
                        section = section,
                        onCaptureRequested = { pokemon -> pendingAction = PendingCollectionAction.Capture(pokemon) },
                        onNavigateToDetail = onNavigateToDetail,
                    )
                }
            }
        }
    }

    pendingAction?.let { action ->
        CollectionActionConfirmationDialog(
            action = action,
            onConfirm = {
                when (action) {
                    is PendingCollectionAction.Capture -> onCapture(action.pokemon.id)
                    is PendingCollectionAction.Release -> onRelease(action.capture.captureId)
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
}

private sealed interface PendingCollectionAction {
    data class Capture(val pokemon: Pokemon) : PendingCollectionAction
    data class Release(val capture: CapturedPokemon) : PendingCollectionAction
}

@Composable
private fun CollectionActionConfirmationDialog(
    action: PendingCollectionAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, message, confirmLabel) = when (action) {
        is PendingCollectionAction.Capture -> {
            val name = action.pokemon.name.displayName()
            Triple("Capture $name?", "Do you want to add $name to your pocket?", "Capture")
        }
        is PendingCollectionAction.Release -> {
            val name = action.capture.pokemonName.displayName()
            Triple("Release $name?", "Do you want to release $name from your pocket?", "Release")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun String.displayName(): String = replaceFirstChar { it.uppercase() }

@Composable
private fun PocketSection(
    captures: List<CapturedPokemon>,
    onReleaseRequested: (CapturedPokemon) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(captures.firstOrNull()?.captureId) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("captured-section"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "My Pocket",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = captures.size.toString(),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No Pokemon captured yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("captured-row"),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                items(captures, key = { it.captureId }) { capture ->
                    PocketCard(
                        capture = capture,
                        onReleaseRequested = onReleaseRequested,
                        onNavigateToDetail = onNavigateToDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun PocketCard(
    capture: CapturedPokemon,
    onReleaseRequested: (CapturedPokemon) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 152.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigateToDetail(capture.pokemonId) }
            .testTag("capture-card-${capture.captureId}"),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (capture.imageUrl != null) {
                        ReloadableAsyncImage(
                            model = capture.imageUrl,
                            contentDescription = capture.pokemonName,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(text = "#${capture.pokemonId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onReleaseRequested(capture) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .testTag("release-action-${capture.captureId}")
                            .semantics { contentDescription = "Release captured Pokémon #${capture.pokemonId}" },
                    ) {
                        PokeBallIcon(modifier = Modifier.size(20.dp))
                    }
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    text = capture.pokemonName,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SyncStatusRow(
    current: Int,
    total: Int,
    isSyncing: Boolean,
    failure: SyncFailure?,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = if (isSyncing) "Syncing Pokémon" else "Pokémon sync",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "$current / $total",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (failure != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (failure == SyncFailure.Offline) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (failure == SyncFailure.Offline) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("sync-failure-tag"),
                ) {
                    Text(
                        text = when (failure) {
                            SyncFailure.Offline -> "Network offline"
                            is SyncFailure.Api -> "Sync error"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(
                    onClick = onRetry,
                    enabled = !isSyncing,
                    modifier = Modifier.testTag("retry-sync"),
                ) {
                    Text("Retry")
                }
            }
        } else if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun TypeSection(
    section: TypeSection,
    onCaptureRequested: (Pokemon) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("type-section-${section.name}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.name.replaceFirstChar { it.uppercase() },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = section.count.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("pokemon-row-${section.name}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                section.pokemon,
                key = { it.id },
            ) { pokemon ->
                PokemonCard(
                    pokemon = pokemon,
                    onCapture = { onCaptureRequested(pokemon) },
                    onNavigateToDetail = { onNavigateToDetail(pokemon.id) },
                )
            }
        }
    }
}

@Composable
private fun PokemonCard(
    pokemon: Pokemon,
    onCapture: () -> Unit,
    onNavigateToDetail: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 152.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clickable { onNavigateToDetail() }
                    .semantics { contentDescription = "Open ${pokemon.name.replaceFirstChar { it.uppercase() }} details" },
                contentAlignment = Alignment.Center,
            ) {
                if (pokemon.imageUrl != null) {
                    ReloadableAsyncImage(
                        model = pokemon.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .testTag("capture-action-${pokemon.id}")
                        .semantics { contentDescription = "Capture ${pokemon.name.replaceFirstChar { it.uppercase() }}" },
                ) {
                    PokeBallIcon(modifier = Modifier.size(22.dp))
                }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                text = pokemon.name.replaceFirstChar { it.uppercase() },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PokeBallIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White, radius = radius, center = center)
        drawArc(
            color = Color(0xFFF44336),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
        )
        drawLine(
            color = Color(0xFF3B3B3B),
            start = androidx.compose.ui.geometry.Offset(0f, center.y),
            end = androidx.compose.ui.geometry.Offset(size.width, center.y),
            strokeWidth = size.minDimension * .12f,
        )
        drawCircle(color = Color.White, radius = radius * .32f, center = center)
        drawCircle(color = Color(0xFF3B3B3B), radius = radius * .32f, center = center, style = Stroke(size.minDimension * .1f))
        drawCircle(color = Color(0xFF3B3B3B), radius = radius, center = center, style = Stroke(size.minDimension * .08f))
    }
}
