package org.siloserver.silo.tv.ui.theme

/**
 * A ready-made accent and selected colour that look good together. A null
 * [selected] makes the selected colour follow the accent.
 */
class TvThemeLook(
    val label: String,
    val accent: AccentPreset,
    val selected: AccentPreset? = null,
) {
    /** Whether the colours in use are exactly this look. */
    val isCurrent: Boolean
        get() = TvAccent.selectedId == accent.id &&
            TvSelected.selectedId == (selected?.id ?: TvSelected.MatchId)

    fun select() {
        TvAccent.select(accent)
        if (selected == null) TvSelected.matchAccent() else TvSelected.select(selected)
    }
}

/** The looks offered at the top of the Theme Colors panel. */
object TvThemeLooks {
    val all: List<TvThemeLook> = listOf(
        TvThemeLook("Gold", AccentPreset.Gold),
        TvThemeLook("Fire", AccentPreset.FireRed),
        TvThemeLook("Gold & Grey", AccentPreset.Gold, AccentPreset.Graphite),
        TvThemeLook("Ocean", AccentPreset.Ocean),
        TvThemeLook("Mint", AccentPreset.Mint),
        TvThemeLook("Violet", AccentPreset.Purple),
    )
}
