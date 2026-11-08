package org.siloserver.silo.tv.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * The fill and outline that mark the current choice: the open top-bar tab, the
 * chosen season, the highlighted row in a menu.
 *
 * It is chosen independently of the focus accent, from the same colour list.
 * New installs pair the gold accent with Graphite; it can also follow the
 * accent ([MatchId]) or be a custom colour. With [glass] on, the colour is
 * drawn as a see-through tint with a bright edge and white text.
 *
 * Read it through [SelectedFill], [SelectedStroke] and [SelectedContent]. It is
 * backed by Compose state, so changing the choice recolours the open screen at
 * once.
 */
object TvSelected {
    /** [selectedId] while the colour follows the accent. */
    const val MatchId = "match"

    /** [selectedId] when the viewer chose their own colour. */
    const val CustomId = "custom"

    private const val PrefsName = "tv_appearance"
    private const val KeySelectedId = "selected_id"
    private const val KeyCustomColor = "selected_custom_argb"
    private const val KeyGlass = "selected_glass"

    private const val GlassTintAlpha = 0.38f
    private const val GlassEdgeAlpha = 0.75f

    /** Text on a light fill is this dark tone instead of white. */
    private val DarkContent = Color(0xFF111318)

    private var prefs: SharedPreferences? = null

    /** [MatchId], [CustomId], or the id of an [AccentPreset]. New installs start on Graphite. */
    var selectedId by mutableStateOf(AccentPreset.Graphite.id)
        private set

    /** The viewer's own colour; used when [selectedId] is [CustomId]. */
    var customColor by mutableStateOf(Color(0xFF4F7CE0))
        private set

    /** Whether the colour is drawn as a glass pane instead of a solid fill. */
    var glass by mutableStateOf(true)
        private set

    /** The chosen colour, exactly as picked. */
    private val color: Color
        get() = when (selectedId) {
            MatchId -> TvAccent.color
            CustomId -> customColor
            else -> AccentPreset.fromId(selectedId)?.color ?: TvAccent.color
        }

    /** The chosen colour at full strength, for swatches that show it. */
    val swatch: Color
        get() = color

    /** The fill: the chosen colour, or a see-through tint of it with [glass] on. */
    val fill: Color
        get() = if (glass) color.copy(alpha = GlassTintAlpha) else color

    /** The outline: the colour lightened to read on the dark background, or a bright edge with [glass] on. */
    val stroke: Color
        get() = if (glass) Color.White.copy(alpha = GlassEdgeAlpha) else lerp(color, Color.White, 0.55f)

    /** Text and icons that sit on [fill]: dark on light colours, white on dark ones and on glass. */
    val content: Color
        get() = if (!glass && color.luminance() > 0.40f) DarkContent else Color.White

    /** Loads the saved choice. Call once from [android.app.Application.onCreate]. */
    fun init(context: Context) {
        val stored = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        prefs = stored
        if (stored.contains(KeyCustomColor)) {
            customColor = Color(stored.getInt(KeyCustomColor, customColor.toArgb()))
        }
        glass = stored.getBoolean(KeyGlass, true)
        val savedId = stored.getString(KeySelectedId, null)
        if (savedId == MatchId || savedId == CustomId || AccentPreset.fromId(savedId) != null) {
            selectedId = savedId!!
        }
    }

    fun setGlassEnabled(enabled: Boolean) {
        glass = enabled
        prefs?.edit()?.putBoolean(KeyGlass, enabled)?.apply()
    }

    fun matchAccent() {
        selectedId = MatchId
        prefs?.edit()?.putString(KeySelectedId, MatchId)?.apply()
    }

    fun select(preset: AccentPreset) {
        selectedId = preset.id
        prefs?.edit()?.putString(KeySelectedId, preset.id)?.apply()
    }

    fun selectCustom(color: Color) {
        customColor = color
        selectedId = CustomId
        prefs?.edit()
            ?.putString(KeySelectedId, CustomId)
            ?.putInt(KeyCustomColor, color.toArgb())
            ?.apply()
    }
}
