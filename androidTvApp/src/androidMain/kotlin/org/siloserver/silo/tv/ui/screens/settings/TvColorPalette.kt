package org.siloserver.silo.tv.ui.screens.settings

import androidx.compose.ui.graphics.Color

/** A named entry in the custom colour grid. */
internal data class NamedColor(val name: String, val color: Color)

/**
 * The custom colour grid: a column per hue and ten tones down, each named
 * "<tone> <hue>" so the name shown while browsing says what the swatch is.
 * The last two columns are the blue-grey Slate and a neutral Gray.
 */
internal object TvColorPalette {
    /** [saturationScale] mutes a hue: Slate is the blue hue at a third of its saturation. */
    private class Hue(val name: String, val degrees: Float, val saturationScale: Float = 1f)

    private val hues = listOf(
        Hue("Red", 355f),
        Hue("Orange", 22f),
        Hue("Gold", 45f),
        Hue("Yellow", 60f),
        Hue("Lime", 85f),
        Hue("Green", 135f),
        Hue("Teal", 175f),
        Hue("Sky", 200f),
        Hue("Blue", 225f),
        Hue("Purple", 270f),
        Hue("Pink", 320f),
        Hue("Slate", 215f, saturationScale = 0.35f),
    )

    /** Tone name, saturation, value: pale to near black, then the softer dusty and muted pair. */
    private val tones = listOf(
        Triple("Pastel", 0.30f, 1.00f),
        Triple("Light", 0.55f, 1.00f),
        Triple("Vivid", 0.95f, 1.00f),
        Triple("Medium", 0.85f, 0.80f),
        Triple("Strong", 0.95f, 0.65f),
        Triple("Dark", 0.90f, 0.50f),
        Triple("Deep", 0.85f, 0.38f),
        Triple("Midnight", 0.80f, 0.26f),
        Triple("Dusty", 0.45f, 0.75f),
        Triple("Muted", 0.30f, 0.55f),
    )

    /** The neutral column, light to dark, one per tone row. */
    private val grays = listOf(
        NamedColor("Snow", Color(0xFFF5F6F8)),
        NamedColor("Fog", Color(0xFFE0E3E8)),
        NamedColor("Silver", Color(0xFFC6CAD1)),
        NamedColor("Ash", Color(0xFFAEB3BC)),
        NamedColor("Light Gray", Color(0xFF969CA7)),
        NamedColor("Gray", Color(0xFF7F8590)),
        NamedColor("Dim Gray", Color(0xFF686E79)),
        NamedColor("Stone", Color(0xFF525863)),
        NamedColor("Charcoal", Color(0xFF3D424C)),
        NamedColor("Coal", Color(0xFF2A2E36)),
    )

    /** Swatches per row: every hue plus the neutral column. */
    val Columns: Int = hues.size + 1

    val Rows: Int = tones.size

    val colors: List<NamedColor> = buildList {
        for ((row, tone) in tones.withIndex()) {
            val (toneName, saturation, value) = tone
            for (hue in hues) {
                add(
                    NamedColor(
                        "$toneName ${hue.name}",
                        Color.hsv(hue.degrees, saturation * hue.saturationScale, value),
                    ),
                )
            }
            add(grays[row])
        }
    }

    fun nameOf(color: Color): String? = colors.firstOrNull { it.color == color }?.name
}
