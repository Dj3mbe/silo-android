package org.siloserver.silo.tv.ui.screens.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Looping 0..1 progress for the ring that spreads from the mic button while the
 * app is listening. Returned as State so only the draw phase reads it.
 */
@Composable
internal fun rememberListeningPulse(): State<Float> =
    rememberInfiniteTransition(label = "voicePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "voicePulseProgress",
    )
