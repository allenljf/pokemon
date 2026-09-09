package com.allen.pokemon.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connectivity-recovery stamp for [LocalImageReloadKey]. Screens bump this value
 * whenever the network transitions back online so that artwork which failed to
 * load while offline is requested again.
 */
val LocalImageReloadKey = staticCompositionLocalOf { 0 }

@Composable
fun ProvideImageReloadKey(
    reloadKey: Int,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalImageReloadKey provides reloadKey, content = content)
}

/**
 * [AsyncImage] that recovers artwork which errored while offline.
 *
 * Two mechanisms keep a failed request from being stuck forever:
 * 1. The [LocalImageReloadKey] is baked into the request parameters, so a new
 *    connectivity recovery produces a different [ImageRequest] and [AsyncImage]
 *    restarts it (Coil's default model equality includes request parameters).
 * 2. A single restart right after recovery can still race the network coming up,
 *    so errors also schedule a few bounded, backoff retries before giving up.
 */
@Composable
fun ReloadableAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val reloadKey = LocalImageReloadKey.current
    var retryCount by remember(reloadKey, model) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val request = remember(model, reloadKey, retryCount) {
        ImageRequest.Builder(context)
            .data(model)
            .setParameter("imageReloadKey", reloadKey)
            .setParameter("imageRetryAttempt", retryCount)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = {
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                val attempt = retryCount + 1
                scope.launch {
                    delay(RETRY_BASE_DELAY_MS * attempt)
                    retryCount = attempt
                }
            }
        },
    )
}

private const val MAX_RETRY_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 800L
