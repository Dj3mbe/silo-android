package org.siloserver.silo.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

private val GlassSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.30f),
    0.55f to Color.Transparent,
    1f to Color.Transparent,
)

/**
 * A soft light along the top of a selected control, like a glass edge catching
 * light. It draws only while [TvSelected.glass] is on and [visible] is true.
 */
fun Modifier.glassSheen(shape: Shape, visible: Boolean = true): Modifier =
    if (visible && TvSelected.glass) background(GlassSheen, shape) else this
