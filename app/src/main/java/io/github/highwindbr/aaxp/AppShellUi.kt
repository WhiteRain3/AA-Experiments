package io.github.highwindbr.aaxp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.AvailableUpdate

/** A feature with a page of its own, reachable from Home, Look or Settings. */
internal enum class FeaturePage(val title: String, val subtitle: String, val icon: ImageVector) {
    THEMES("Colours", "Recolour the car screen", Icons.Default.Palette),
    WALLPAPER("Wallpaper", "Your own image on the car screen", Icons.Default.Wallpaper),
    ICONS("App icons", "Icons on the car screen", Icons.Default.Apps),
    STATUS_BAR("Status bar", "Clock, signal and battery", Icons.Default.Schedule),
    LOOK_FILE("Style packs and backup", "Save or load the whole look as one file", Icons.Default.Inventory2),
    PRESETS("Flag presets", "Named sets of flag overrides", Icons.Default.Bookmarks),
    QUICK_SWITCHES("Quick switches", "Several flags behind one switch", Icons.Default.Tune),
    CONTROLS("Restart", "When Android Auto restarts", Icons.Default.Tune),
    UPDATES("Updates", "Get the newest build of this app", Icons.Default.SystemUpdate),
    LAYOUT_CAPTURE("Screen layout capture", "Record how the car screen is built", Icons.Default.GridView),
    SYSTEM("System status", "Module, resolver and runtime details", Icons.Default.MonitorHeart)
}

@Composable private fun IconBadge(icon: ImageVector, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.52f).dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** A full-width row leading to a feature: icon, name, current state, chevron. */
@Composable internal fun FeatureTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SectionHeading(text: String) =
    Text(text, Modifier.padding(start = 4.dp, top = 6.dp), style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

/** What each look feature is currently set to, read fresh whenever the screen is shown. */
private class LookState(model: ManagerViewModel, connected: Boolean) {
    val colours = if (connected) model.themeColorsMap().size else 0
    val wallpaper = connected && model.carWallpaperEnabled()
    val glyphs = connected && model.glyphIconColors() != null
    val statusBar = connected && model.statusBarConfig() != null
    /** One short word or phrase per page: "Default", "3 colours" or "On". */
    fun stateOf(page: FeaturePage): String = when (page) {
        FeaturePage.THEMES -> if (colours == 0) "Default" else "$colours colours"
        FeaturePage.WALLPAPER -> if (wallpaper) "On" else "Default"
        FeaturePage.ICONS -> if (glyphs) "On" else "Default"
        FeaturePage.STATUS_BAR -> if (statusBar) "On" else "Default"
        else -> ""
    }
}

/** A small picture of what a look page is set to: a colour strip, the wallpaper, an icon, a status bar. */
@Composable private fun LookThumb(page: FeaturePage, model: ManagerViewModel, connected: Boolean, look: LookState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        when {
            page == FeaturePage.THEMES && look.colours > 0 -> {
                val roles = remember(look.colours) { model.themeColorsMap() }
                Row(Modifier.fillMaxSize()) {
                    listOf("Background", "Primary", "Secondary", "Tertiary").forEach { role ->
                        Box(Modifier.weight(1f).fillMaxSize().background(androidx.compose.ui.graphics.Color(roles[role] ?: roles["Surface"] ?: 0xFF808080.toInt())))
                    }
                }
            }
            page == FeaturePage.WALLPAPER && look.wallpaper -> {
                val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { CarWallpaperStudio.loadSource(context)?.let { android.graphics.Bitmap.createScaledBitmap(it, 88, 88, true) } }.getOrNull()
                    }
                }
                bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    ?: IconBadge(page.icon, 44)
            }
            page == FeaturePage.ICONS && look.glyphs -> {
                val colours = remember { io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(model.glyphIconColors() ?: "") ?: intArrayOf(-1, 0) }
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A3839)).background(androidx.compose.ui.graphics.Color(colours[1])),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Apps, null, Modifier.size(24.dp), tint = androidx.compose.ui.graphics.Color(colours[0]))
                }
            }
            page == FeaturePage.STATUS_BAR && look.statusBar -> {
                val config = remember { io.github.highwindbr.aaxp.runtime.StatusBarTweaks.parse(model.statusBarConfig()) }
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF121212)), contentAlignment = Alignment.Center) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(14.dp)
                        .background(if (config?.noPlate == true) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color(config?.plateColor ?: 0xFF303030.toInt()), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.size(10.dp, 4.dp).background(androidx.compose.ui.graphics.Color(config?.clockColor ?: 0xFFF2F0F0.toInt())))
                        Box(Modifier.size(8.dp, 4.dp).background(androidx.compose.ui.graphics.Color(config?.iconColor ?: 0xFFF2F0F0.toInt())))
                    }
                }
            }
            else -> IconBadge(page.icon, 44)
        }
    }
}

/** A row on a look card or the Look tab: thumbnail, name, one-word state, chevron. */
@Composable private fun LookRow(page: FeaturePage, model: ManagerViewModel, connected: Boolean, look: LookState, onOpen: (FeaturePage) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(page) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LookThumb(page, model, connected, look)
        Spacer(Modifier.width(14.dp))
        Text(page.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(look.stateOf(page), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val LOOK_PAGES = listOf(FeaturePage.THEMES, FeaturePage.WALLPAPER, FeaturePage.ICONS, FeaturePage.STATUS_BAR)

/**
 * Home: whether everything is working, one button to restart Android Auto, and the look and flag
 * pages with their current state. Technical detail lives behind "System status".
 */
@Composable internal fun HomeScreen(
    model: ManagerViewModel, connected: Boolean, ready: Boolean, checking: Boolean,
    headline: String, detail: String, modifiedFlags: Int, overrides: Map<String, io.github.highwindbr.aaxp.core.TypedOverride>,
    onOpen: (FeaturePage) -> Unit, onFlags: () -> Unit
) {
    val look = remember(connected) { LookState(model, connected) }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    LaunchedEffect(Unit) { if (model.checkUpdatesOnOpen()) model.checkForUpdate(quiet = true) { found, _ -> update = found } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SafeModeBanner(model, connected) }
        item { ChangesWaitingBanner(model, connected) }
        item {
            val colors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)))
                .clickable { onOpen(FeaturePage.SYSTEM) }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (checking) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        else Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, Modifier.size(32.dp),
                            tint = if (ready) colors.primary else colors.error)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text("Details", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        Icon(Icons.Default.ChevronRight, null, tint = colors.onSurfaceVariant)
                    }
                    Button({ model.requestAndroidAutoControl(restart = true, headUnit = model.headUnitAfterRestart()) },
                        Modifier.fillMaxWidth().height(52.dp), enabled = connected) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text(model.restartResult ?: "Restart Android Auto", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        update?.let { found ->
            item { FeatureTile(Icons.Default.SystemUpdate, "Update available", "${found.versionName} is ready to install") { onOpen(FeaturePage.UPDATES) } }
        }
        if (model.canUndo) item {
            FeatureTile(Icons.AutoMirrored.Filled.Undo, "Undo last change", "Put the look back to how it was before ${model.undoLabel}") { model.undoLastChange() }
        }
        item {
            Column {
                SectionHeading("Your look")
                Surface(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LOOK_PAGES.forEachIndexed { index, page ->
                            if (index > 0) RowDivider()
                            LookRow(page, model, connected, look, onOpen)
                        }
                    }
                }
            }
        }
        item {
            val switchesOn = io.github.highwindbr.aaxp.core.FlagSwitches.ALL.count { switch ->
                io.github.highwindbr.aaxp.core.FlagSwitches.isOn(switch, overrides.mapValues { io.github.highwindbr.aaxp.core.DynamicOverrideCodec.serialize(it.value) })
            }
            val presetCount = remember(overrides) { model.presets().size }
            Column {
                SectionHeading("Flags")
                Surface(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        LinkRow("Browse flags", if (modifiedFlags == 0) "None changed" else "$modifiedFlags changed", onFlags)
                        RowDivider()
                        LinkRow(FeaturePage.QUICK_SWITCHES.title, "$switchesOn of ${io.github.highwindbr.aaxp.core.FlagSwitches.ALL.size} on", { onOpen(FeaturePage.QUICK_SWITCHES) })
                        RowDivider()
                        LinkRow(FeaturePage.PRESETS.title, if (presetCount == 0) "None" else "$presetCount saved", { onOpen(FeaturePage.PRESETS) })
                    }
                }
            }
        }
    }
}

/** The look pages with a thumbnail and a one-word state each, plus the keep and share row. */
@Composable internal fun LookScreen(model: ManagerViewModel, connected: Boolean, onOpen: (FeaturePage) -> Unit) {
    val look = remember(connected) { LookState(model, connected) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            Text("Look", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("How the car screen looks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LOOK_PAGES.forEachIndexed { index, page ->
                        if (index > 0) RowDivider()
                        LookRow(page, model, connected, look, onOpen)
                    }
                }
            }
        }
        item { SectionHeading("Share and keep") }
        item { FeatureTile(FeaturePage.LOOK_FILE.icon, FeaturePage.LOOK_FILE.title, FeaturePage.LOOK_FILE.subtitle) { onOpen(FeaturePage.LOOK_FILE) } }
    }
}

/** Shown on feature pages and Home while a saved look change is waiting for Android Auto to restart. */
@Composable internal fun ChangesWaitingBanner(model: ManagerViewModel, connected: Boolean) {
    if (!model.changesWaiting) return
    Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Changes saved. Restart Android Auto to see them.", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            TextButton({ model.requestAndroidAutoControl(restart = true, headUnit = model.headUnitAfterRestart()) }, enabled = connected) { Text("Restart") }
        }
    }
}

// ---- Shared components for the feature pages (see docs/DESIGN_SPEC.md) ----

/**
 * A "?" that opens a short explanation in a bottom sheet. The useful sentence goes first and the
 * caveat second; a caveat is moved here, never deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TipIcon(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton({ open = true }, Modifier.size(48.dp)) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, "About $title", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) ModalBottomSheet({ open = false }) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton({ open = false }, Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}

/** The old collapsed note, now a "?" like every other explanation. */
@Composable internal fun HowThisWorks(text: String) = TipIcon("How this works", text)

/** A section heading: the same height with or without a "?", 24dp above and 8dp below. */
@Composable internal fun SectionHeader(title: String, tip: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp).height(40.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        if (tip != null) TipIcon(title, tip)
        trailing()
    }
}

/** A heading with an optional "?" and one card holding its rows. */
@Composable internal fun SettingsSection(
    title: String, tip: String? = null, content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title, tip)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

/** A thin line between two rows of a section. */
@Composable internal fun RowDivider() =
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

/** The width reserved for [ResetIcon] in a row, so rows line up whether or not a value is set. */
internal val RESET_SLOT = 64.dp

/** The one reset control: a small "Reset" button, shown only when a row differs from default. */
@Composable internal fun ResetIcon(onReset: () -> Unit) {
    TextButton(onReset, Modifier.width(RESET_SLOT).heightIn(min = 48.dp).semantics { contentDescription = "Back to default" },
        contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("Reset", style = MaterialTheme.typography.labelMedium)
    }
}

/** Label left, switch right; the whole row toggles. */
@Composable internal fun SwitchRow(
    label: String, checked: Boolean, onChange: (Boolean) -> Unit, tip: String? = null, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically) {
        // One weighted label area, so every switch sits at the same distance from the right edge.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
            if (tip != null) TipIcon(label, tip)
        }
        androidx.compose.material3.Switch(checked, onChange, enabled = enabled)
    }
}

/** Swatch, label, the value ("RRGGBB" or "Default") and a reset icon when a colour is set. */
@Composable internal fun ColourRow(
    label: String, colour: Int?, onPick: () -> Unit, onReset: () -> Unit, tip: String? = null, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorSwatch(colour, Modifier.size(40.dp))
        // One cell for the label (with its "?") and the value under it, so the label only wraps at spaces and never in a narrow column.
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
                if (tip != null) TipIcon(label, tip)
            }
            Text(io.github.highwindbr.aaxp.core.ColourLabel.of(colour), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The reset slot is always reserved, so rows line up with or without a value set.
        if (colour != null) ResetIcon(onReset) else Spacer(Modifier.width(RESET_SLOT))
    }
}

/**
 * Label and value on one line with the slider below. [onChangeFinished] receives the value when
 * the slider is released; the reset icon shows while the value is not [default].
 */
@Composable internal fun SliderRow(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChangeFinished: (Float) -> Unit,
    onReset: (() -> Unit)? = null, tip: String? = null, enabled: Boolean = true, steps: Int = 0
) {
    var shown by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (tip != null) TipIcon(label, tip)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onReset != null) ResetIcon(onReset) else Spacer(Modifier.width(RESET_SLOT))
        }
        androidx.compose.material3.Slider(shown, { shown = it }, valueRange = range, steps = steps, enabled = enabled,
            onValueChangeFinished = { onChangeFinished(shown) })
    }
}

/** A row that opens a sub-screen or dialog: label, a short state, a chevron. */
@Composable internal fun LinkRow(label: String, state: String? = null, onClick: () -> Unit, tip: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
            if (tip != null) TipIcon(label, tip)
        }
        if (state != null) Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The card at the top of a page that shows the current saved result. */
@Composable internal fun PreviewCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** What a page's "more" menu holds: the page-wide reset and any page-specific items. */
internal class PageMenu(
    val pageName: String, val onReset: (() -> Unit)? = null, val extra: List<Pair<String, () -> Unit>> = emptyList(),
    /** Said in the reset confirmation, for anything the reset leaves alone. */
    val resetNote: String? = null
)

/** The frame around a feature's page: back arrow, title, and the app's messages at the bottom. */
@Composable internal fun FeaturePageScaffold(
    page: FeaturePage, messages: SnackbarHostState, model: ManagerViewModel, connected: Boolean, onBack: () -> Unit, scrolls: Boolean = true,
    menu: PageMenu? = null, tip: String? = null, content: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset && menu?.onReset != null) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Put everything on this page back to default?") },
        text = menu.resetNote?.let { note -> { Text(note) } },
        confirmButton = { TextButton({ confirmReset = false; menu.onReset.invoke() }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } })
    Scaffold(snackbarHost = { SnackbarHost(messages) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (tip != null) TipIcon(page.title, tip)
                    }
                    Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (menu != null) Box {
                    IconButton({ menuOpen = true }, Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "More") }
                    androidx.compose.material3.DropdownMenu(menuOpen, { menuOpen = false }) {
                        menu.extra.forEach { (label, action) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(label) }, { menuOpen = false; action() })
                        }
                        if (menu.onReset != null) androidx.compose.material3.DropdownMenuItem(
                            { Text("Reset ${menu.pageName} to default") }, { menuOpen = false; confirmReset = true })
                    }
                } else IconBadge(page.icon, 40)
            }
            if (scrolls) LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item { ChangesWaitingBanner(model, connected); content() }
            } else Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
