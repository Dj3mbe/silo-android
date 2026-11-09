package org.siloserver.silo.tv.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The colour of focus rings, selection outlines and loading indicators. The
 * same list is offered for the selected colour (see [TvSelected]). Every colour
 * but plain white is deep enough to carry white text as a selected fill.
 *
 * Read it through [FocusAccent] wherever a highlight is drawn. It is backed by
 * Compose state, so changing the accent recolours the open screen at once.
 */
enum class AccentPreset(val id: String, val label: String, val color: Color) {
    /** Plain white, the stock Silo highlight. */
    Default("default", "White", SiloOnSurface),

    // Golds
    Gold("gold", "Gold", Color(0xFFCA942B)),
    Mustard("bright_gold", "Mustard", Color(0xFFBB9B00)),
    OldGold("old_gold", "Old Gold", Color(0xFFC9A227)),
    Amber("amber", "Amber", Color(0xFFD9820B)),
    Bronze("bronze", "Bronze", Color(0xFFB07A2E)),

    // Oranges and reds
    Orange("orange", "Orange", Color(0xFFF97A00)),
    Red("crimson", "Red", Color(0xFFEB4052)),
    FireRed("fire_red", "Fire Red", Color(0xFFFF0000)),
    Scarlet("scarlet", "Scarlet", Color(0xFFE50914)),

    // Pinks and purples
    Pink("pink", "Pink", Color(0xFFFF6FAE)),
    Orchid("orchid", "Orchid", Color(0xFFAA5CC3)),
    Purple("royal_purple", "Purple", Color(0xFF8A4FFF)),
    Indigo("indigo", "Indigo", Color(0xFF5B6CFF)),

    // Blues, light to dark
    Cerulean("cerulean", "Cerulean", Color(0xFF02A9F7)),
    Azure("azure", "Azure", Color(0xFF00A8E1)),
    Ocean("ocean", "Ocean", Color(0xFF00A4DC)),
    Blue("blue", "Blue", Color(0xFF3D8BFF)),
    Cobalt("cobalt", "Cobalt", Color(0xFF0064FF)),
    Denim("denim", "Denim", Color(0xFF3F68A8)),
    SteelBlue("steel_blue", "Steel Blue", Color(0xFF4682B4)),
    RoyalBlue("royal_blue", "Royal Blue", Color(0xFF113CCF)),
    Navy("navy", "Navy", Color(0xFF1F3A6E)),
    MidnightBlue("midnight_blue", "Midnight Blue", Color(0xFF1E2A5E)),
    PetrolBlue("petrol_blue", "Petrol Blue", Color(0xFF02577A)),
    DeepSea("deep_sea", "Deep Sea", Color(0xFF01303F)),

    // Teals and greens
    Teal("teal", "Teal", Color(0xFF29B0A3)),
    Mint("mint", "Mint", Color(0xFF12B886)),
    Green("emerald", "Green", Color(0xFF2FB376)),

    // Greys
    Graphite("graphite", "Graphite", Color(0xFF6B6F76)),
    Slate("slate", "Slate", Color(0xFF5F6B7C)),
    Charcoal("charcoal", "Charcoal", Color(0xFF3C4048)),
    ;

    companion object {
        fun fromId(id: String?): AccentPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The accent chosen on this TV. Device-local: it is stored in
 * [SharedPreferences] and read once at launch, so the first frame already
 * uses the saved colour.
 */
object TvAccent {
    val DefaultPreset = AccentPreset.Gold

    /** [selectedId] when the viewer chose their own colour. */
    const val CustomId = "custom"

    private const val PrefsName = "tv_appearance"
    private const val KeySelectedId = "accent_id"
    private const val KeyCustomColor = "accent_custom_argb"

    private var prefs: SharedPreferences? = null

    /** Id of the selected [AccentPreset], or [CustomId]. */
    var selectedId by mutableStateOf(DefaultPreset.id)
        private set

    /** The viewer's own colour; shown as the accent when [selectedId] is [CustomId]. */
    var customColor by mutableStateOf(AccentPreset.Purple.color)
        private set

    val color: Color
        get() = if (selectedId == CustomId) {
            customColor
        } else {
            AccentPreset.fromId(selectedId)?.color ?: DefaultPreset.color
        }

    /** Loads the saved choice. Call once from [android.app.Application.onCreate]. */
    fun init(context: Context) {
        val stored = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        prefs = stored
        if (stored.contains(KeyCustomColor)) {
            customColor = Color(stored.getInt(KeyCustomColor, customColor.toArgb()))
        }
        val savedId = stored.getString(KeySelectedId, null)
        if (savedId != null && (savedId == CustomId || AccentPreset.fromId(savedId) != null)) {
            selectedId = savedId
        }
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

/** The current accent, for focus rings and loading indicators. */
val FocusAccent: Color
    get() = TvAccent.color
