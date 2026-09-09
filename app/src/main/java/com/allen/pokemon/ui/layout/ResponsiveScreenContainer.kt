package com.allen.pokemon.ui.layout

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val MaxContentWidth = 840.dp

/**
 * Keeps content comfortably readable on wide displays and out of a separating vertical fold.
 */
@Composable
fun ResponsiveScreenContainer(
    hingeBounds: Rect?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val isVerticalHinge = hingeBounds?.let { it.height() > it.width() } == true
        val leftPaneWidth = hingeBounds?.let { with(density) { it.left.toDp() } } ?: maxWidth
        val rightPaneWidth = hingeBounds?.let { with(density) { (constraints.maxWidth - it.right).toDp() } } ?: 0.dp
        val useRightPane = isVerticalHinge && rightPaneWidth > leftPaneWidth
        val availableWidth = when {
            !isVerticalHinge -> maxWidth
            useRightPane -> rightPaneWidth
            else -> leftPaneWidth
        }.coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = minOf(MaxContentWidth, availableWidth))
                .align(if (useRightPane) Alignment.TopEnd else Alignment.TopStart),
            content = content,
        )
    }
}
