package org.siloserver.silo.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.ui.focus.TvFocusAcquisitionBudgetMillis
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.components.rememberTvDialogInitialFocus
import org.siloserver.silo.tv.ui.components.tvDialogSurface
import org.siloserver.silo.tv.ui.theme.AccentPreset
import org.siloserver.silo.tv.ui.theme.FocusAccent
import org.siloserver.silo.tv.ui.theme.FocusedContainer
import org.siloserver.silo.tv.ui.theme.FocusedContent
import org.siloserver.silo.tv.ui.theme.SelectedContent
import org.siloserver.silo.tv.ui.theme.SelectedFill
import org.siloserver.silo.tv.ui.theme.SelectedStroke
import org.siloserver.silo.tv.ui.theme.TvAccent
import org.siloserver.silo.tv.ui.theme.TvSelected
import org.siloserver.silo.tv.ui.theme.TvThemeLook
import org.siloserver.silo.tv.ui.theme.TvThemeLooks
import org.siloserver.silo.tv.ui.theme.glassSheen

/** Which of the two lists in the panel a pick or a custom grid belongs to. */
private enum class ThemeColumn { Accent, Selected }

/** One row in a colour list. [color] is null for the "Custom…" row, which shows a rainbow. */
private data class ThemeRow(
    val id: String,
    val label: String,
    val color: Color?,
    val note: String? = null,
)

private const val CustomRowLabel = "Custom…"

/** The "Custom…" row: a rainbow until a custom colour is in use, then that colour and its name. */
private fun customRow(id: String, active: Boolean, color: Color) = ThemeRow(
    id = id,
    label = CustomRowLabel,
    color = color.takeIf { active },
    note = if (active) TvColorPalette.nameOf(color) else null,
)

private fun accentRows(): List<ThemeRow> =
    AccentPreset.entries.map { ThemeRow(it.id, it.label, it.color) } +
        customRow(
            id = TvAccent.CustomId,
            active = TvAccent.selectedId == TvAccent.CustomId,
            color = TvAccent.customColor,
        )

private fun selectedRows(): List<ThemeRow> =
    listOf(ThemeRow(TvSelected.MatchId, "Same as accent", TvAccent.color)) +
        AccentPreset.entries.map { ThemeRow(it.id, it.label, it.color) } +
        customRow(
            id = TvSelected.CustomId,
            active = TvSelected.selectedId == TvSelected.CustomId,
            color = TvSelected.customColor,
        )

/**
 * Settings → General → Cards & Posters → Theme Colors. A row of ready-made
 * looks, then two independent lists, Accent and Selected, offering the same
 * colours. A pick applies at once and the panel stays open, with a live preview
 * underneath, until Back.
 */
@Composable
internal fun TvThemeColorsPanel(onDismiss: () -> Unit) {
    var gridFor by remember { mutableStateOf<ThemeColumn?>(null) }
    var lastColumn by remember { mutableStateOf(ThemeColumn.Accent) }

    when (gridFor) {
        ThemeColumn.Accent -> TvColorGrid(
            title = "Custom Accent",
            currentColor = TvAccent.customColor.takeIf { TvAccent.selectedId == TvAccent.CustomId },
            onSelect = { color ->
                TvAccent.selectCustom(color)
                lastColumn = ThemeColumn.Accent
                gridFor = null
            },
            onDismiss = { gridFor = null },
        )
        ThemeColumn.Selected -> TvColorGrid(
            title = "Custom Selected",
            currentColor = TvSelected.customColor.takeIf { TvSelected.selectedId == TvSelected.CustomId },
            onSelect = { color ->
                TvSelected.selectCustom(color)
                lastColumn = ThemeColumn.Selected
                gridFor = null
            },
            onDismiss = { gridFor = null },
        )
        null -> ThemePanel(
            initialColumn = lastColumn,
            onOpenGrid = { column ->
                lastColumn = column
                gridFor = column
            },
            onDismiss = onDismiss,
        )
    }
}

private val ListHeight = 204.dp

private const val FocusRetryMillis = 60L

/**
 * Puts focus on [target] once its row exists, even if the focus system has
 * already placed it on another row of the new window, and stops as soon as the
 * target holds focus.
 */
@Composable
private fun FocusTargetEffect(target: FocusRequester, targetFocused: () -> Boolean) {
    LaunchedEffect(target) {
        requestFocusUntilObserved(
            maxAttempts = (TvFocusAcquisitionBudgetMillis / FocusRetryMillis).toInt(),
            awaitAttempt = { delay(FocusRetryMillis) },
            requestFocus = target::requestFocus,
            isFocused = targetFocused,
        )
    }
}

@Composable
private fun ThemePanel(
    initialColumn: ThemeColumn,
    onOpenGrid: (ThemeColumn) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    var targetFocused by remember { mutableStateOf(false) }
    FocusTargetEffect(initialFocus) { targetFocused }
    val accentRows = accentRows()
    val selectedRows = selectedRows()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .tvDialogSurface()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .then(rememberTvDialogInitialFocus(initialFocus)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Theme Colors",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 21.sp, lineHeight = 25.sp),
                    color = Color.White,
                )
                ThemeLooksRow()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ThemeColorList(
                        title = "Accent",
                        rows = accentRows,
                        currentId = TvAccent.selectedId,
                        onPick = { row ->
                            val preset = AccentPreset.fromId(row.id)
                            if (preset != null) TvAccent.select(preset) else onOpenGrid(ThemeColumn.Accent)
                        },
                        initialFocus = initialFocus.takeIf { initialColumn == ThemeColumn.Accent },
                        onTargetFocused = { targetFocused = true },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeColorList(
                        title = "Selected",
                        rows = selectedRows,
                        currentId = TvSelected.selectedId,
                        onPick = { row ->
                            val preset = AccentPreset.fromId(row.id)
                            when {
                                row.id == TvSelected.MatchId -> TvSelected.matchAccent()
                                preset != null -> TvSelected.select(preset)
                                else -> onOpenGrid(ThemeColumn.Selected)
                            }
                        },
                        initialFocus = initialFocus.takeIf { initialColumn == ThemeColumn.Selected },
                        onTargetFocused = { targetFocused = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                ThemePreview()
            }
        }
    }
}

/** The ready-made looks, each a tile showing its selected and accent colours, then the Glass switch. */
@Composable
private fun ThemeLooksRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TvThemeLooks.all.forEach { look ->
            ThemeLookTile(look, modifier = Modifier.weight(1f))
        }
        ThemeGlassTile(modifier = Modifier.weight(1f))
    }
}

/** Switches the selected colour between a solid fill and a glass pane. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeGlassTile(modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val on = TvSelected.glass
    val shape = RoundedCornerShape(8.dp)
    val foreground = if (isFocused) FocusedContent else Color.White

    Surface(
        onClick = { TvSelected.setGlassEnabled(!on) },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = themeItemColors(on),
        border = themeItemBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.semantics { selected = on },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = "Glass",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = foreground,
                maxLines = 1,
            )
            Text(
                text = if (on) "On" else "Off",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = foreground.copy(alpha = 0.62f),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeLookTile(look: TvThemeLook, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val current = look.isCurrent
    val shape = RoundedCornerShape(8.dp)

    Surface(
        onClick = look::select,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = themeItemColors(current),
        border = themeItemBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.semantics { selected = current },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LookDot((look.selected ?: look.accent).color)
                LookDot(look.accent.color)
            }
            Text(
                text = look.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = if (isFocused) FocusedContent else Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LookDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
    )
}

/** Container and content colours shared by the rows and tiles of the panel. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun themeItemColors(current: Boolean) = ClickableSurfaceDefaults.colors(
    containerColor = Color.White.copy(alpha = if (current) 0.12f else 0.05f),
    contentColor = Color.White,
    focusedContainerColor = FocusedContainer,
    focusedContentColor = FocusedContent,
    pressedContainerColor = FocusedContainer,
    pressedContentColor = FocusedContent,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun themeItemBorder(shape: RoundedCornerShape) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(border = BorderStroke(2.dp, FocusAccent), shape = shape),
)

@Composable
private fun ThemeColorList(
    title: String,
    rows: List<ThemeRow>,
    currentId: String,
    onPick: (ThemeRow) -> Unit,
    initialFocus: FocusRequester?,
    onTargetFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = rows.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.height(ListHeight),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                ThemeColorRow(
                    row = row,
                    current = row.id == currentId,
                    onClick = { onPick(row) },
                    modifier = if (initialFocus != null && index == currentIndex) {
                        Modifier
                            .focusRequester(initialFocus)
                            .onFocusChanged { if (it.isFocused) onTargetFocused() }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private val RainbowBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF4D4D),
        Color(0xFFFFB84D),
        Color(0xFFFFEB4D),
        Color(0xFF4DDB7A),
        Color(0xFF4DB8FF),
        Color(0xFF9B6BFF),
        Color(0xFFFF4D4D),
    ),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeColorRow(
    row: ThemeRow,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val foreground = if (isFocused) FocusedContent else Color.White

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = themeItemColors(current),
        border = themeItemBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .semantics { selected = current },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (row.color != null) {
                TvColorDot(row.color)
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(RainbowBrush, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
                )
            }
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = foreground,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            row.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = foreground.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            if (current) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(foreground, CircleShape),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

private enum class PreviewTabState { Selected, Focused, Idle }

private val PreviewGround = Color(0xFF0B0C10)

/** A top-bar tab drawn in its selected, focused or idle state, in the live colours. */
@Composable
private fun PreviewTab(label: String, state: PreviewTabState, compact: Boolean = false) {
    val shape = CircleShape
    val fill = when (state) {
        PreviewTabState.Selected -> SelectedFill
        PreviewTabState.Focused -> Color.White
        PreviewTabState.Idle -> Color.Transparent
    }
    val textColor = when (state) {
        PreviewTabState.Selected -> SelectedContent
        PreviewTabState.Focused -> FocusedContent
        PreviewTabState.Idle -> Color.White.copy(alpha = 0.62f)
    }
    val borderColor = when (state) {
        PreviewTabState.Selected -> SelectedStroke.copy(alpha = 0.55f)
        PreviewTabState.Focused -> FocusAccent
        PreviewTabState.Idle -> Color.Transparent
    }
    val borderWidth = if (state == PreviewTabState.Focused) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .height(if (compact) 22.dp else 28.dp)
            .background(fill, shape)
            .glassSheen(shape, visible = state == PreviewTabState.Selected)
            .border(borderWidth, borderColor, shape)
            .padding(horizontal = if (compact) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 14.sp else 17.sp,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewSeasonChip(label: String, selected: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(if (selected) SelectedFill else Color.Transparent, shape)
            .glassSheen(shape, visible = selected)
            .border(1.dp, if (selected) SelectedStroke else Color.White.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = if (selected) SelectedContent else Color.White,
            maxLines = 1,
        )
    }
}

/** Sample tabs, a focused poster, season chips and a loading spinner in the live colours. */
@Composable
private fun ThemePreview() {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PreviewGround, shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewTab("Home", PreviewTabState.Selected)
            PreviewTab("Movies", PreviewTabState.Focused)
            PreviewTab("Series", PreviewTabState.Idle)
            PreviewTab("Calendar", PreviewTabState.Idle)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val posterShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .size(width = 30.dp, height = 42.dp)
                    .background(Color.White.copy(alpha = 0.08f), posterShape)
                    .border(2.dp, FocusAccent, posterShape),
            )
            PreviewSeasonChip("Season 1", selected = true)
            PreviewSeasonChip("Season 2", selected = false)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Loading spinner",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
            )
            CircularProgressIndicator(
                color = FocusAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Settings row
// ---------------------------------------------------------------------------

private val PairTileShape = RoundedCornerShape(4.dp)

/** A small tile previewing one colour. */
@Composable
internal fun ThemeSwatchTile(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 30.dp, height = 14.dp)
            .background(color, PairTileShape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), PairTileShape),
    )
}

/**
 * The live part of the Theme Colors Settings row: sample tabs, then the selected
 * and accent colours as two tiles in the tabs' order. The tabs sit on their own dark ground so
 * they read the same whether or not the row has focus.
 */
@Composable
internal fun TvThemeColorsRowSamples() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PreviewGround)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PreviewTab("Home", PreviewTabState.Selected, compact = true)
            PreviewTab("Movies", PreviewTabState.Focused, compact = true)
            PreviewTab("Series", PreviewTabState.Idle, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ThemeSwatchTile(TvSelected.swatch)
            ThemeSwatchTile(FocusAccent)
        }
    }
}

// ---------------------------------------------------------------------------
// Custom colour grid
// ---------------------------------------------------------------------------

private val SwatchSize = 30.dp
private val SwatchGap = 6.dp
private val GridHeight: Dp =
    SwatchSize * TvColorPalette.Rows + SwatchGap * (TvColorPalette.Rows - 1) + 8.dp

@Composable
private fun TvColorGrid(
    title: String,
    currentColor: Color?,
    onSelect: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val initialFocus = remember { FocusRequester() }
    val palette = TvColorPalette.colors
    // Start on the colour already in use when it is part of the grid.
    val initialIndex = palette.indexOfFirst { it.color == currentColor }.coerceAtLeast(0)
    var focusedName by remember { mutableStateOf(palette[initialIndex].name) }
    var initialFocused by remember { mutableStateOf(false) }
    FocusTargetEffect(initialFocus) { initialFocused }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .tvDialogSurface()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .then(rememberTvDialogInitialFocus(initialFocus)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 21.sp, lineHeight = 25.sp),
                        color = Color.White,
                    )
                    Text(
                        text = focusedName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        color = Color.White.copy(alpha = 0.60f),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(TvColorPalette.Columns),
                    // Rows of SwatchSize plus the gaps and padding; the grid would otherwise fill the screen.
                    modifier = Modifier.height(GridHeight),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(SwatchGap),
                    horizontalArrangement = Arrangement.spacedBy(SwatchGap),
                ) {
                    items(palette.size, key = { it }) { index ->
                        val entry = palette[index]
                        ColorSwatch(
                            color = entry.color,
                            selected = entry.color == currentColor,
                            onFocused = {
                                focusedName = entry.name
                                if (index == initialIndex) initialFocused = true
                            },
                            onClick = { onSelect(entry.color) },
                            modifier = if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One grid cell. Focus is a white ring so it reads against any colour,
 * including the accent that is currently applied.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onFocused() }
    Box(
        modifier = modifier
            .size(SwatchSize)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .padding(4.dp)
            .background(color, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = if (selected) "Selected color" else "Color" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** A small filled circle showing [color], used before a colour option's label. */
@Composable
internal fun TvColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(color, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
    )
}
