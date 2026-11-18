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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.AvailableUpdate

/** A feature with a page of its own, reachable from Home, Look or Settings. */
internal enum class FeaturePage(val title: String, val subtitle: String, val icon: ImageVector) {
    THEMES("Colours", "Recolour the car screen", Icons.Default.Palette),
    WALLPAPER("Car wallpaper", "Your own image on the car screen", Icons.Default.Wallpaper),
    ICONS("App icons", "Single-colour glyph icons", Icons.Default.Apps),
    STATUS_BAR("Status bar", "Clock, signal and battery", Icons.Default.Schedule),
    LAUNCHER("Launcher", "The grid of app icons", Icons.Default.ViewModule),
    LOOK_FILE("Style packs and backup", "Save or load the whole look as one file", Icons.Default.Inventory2),
    PRESETS("Flag presets", "Named sets of flag overrides", Icons.Default.Bookmarks),
    QUICK_SWITCHES("Quick switches", "Several flags behind one switch", Icons.Default.Tune),
    CONTROLS("Restart behaviour", "Automatic restart and Desktop Head Unit mode", Icons.Default.Tune),
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

/** A square tile for the Home grid. */
@Composable private fun GridTile(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, 40)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    fun themes() = if (colours == 0) "Android Auto's own colours" else "$colours colours set"
    fun wallpaperText() = if (wallpaper) "On" else "Off"
    fun icons() = if (glyphs) "Glyph icons on" else "Normal icons"
    fun statusBarText() = if (statusBar) "Changed" else "Android Auto's own"
    val launcher = connected && model.launcherConfig() != null
    fun launcherText() = if (launcher) "Icons only" else "Icons with names"
}

/**
 * Home: whether everything is working, one button to restart Android Auto, and shortcuts to the
 * things people come here to do. Technical detail lives behind "System status".
 */
@Composable internal fun HomeScreen(
    model: ManagerViewModel, connected: Boolean, ready: Boolean, checking: Boolean,
    headline: String, detail: String, modifiedFlags: Int,
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
                    }
                    Button({ model.requestAndroidAutoControl(restart = true, headUnit = model.headUnitAfterRestart()) },
                        Modifier.fillMaxWidth().height(52.dp), enabled = connected) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text("Restart Android Auto", fontWeight = FontWeight.SemiBold)
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
        item { SectionHeading("Look") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridTile(Modifier.weight(1f), FeaturePage.THEMES.icon, "Themes", look.themes()) { onOpen(FeaturePage.THEMES) }
            GridTile(Modifier.weight(1f), FeaturePage.WALLPAPER.icon, "Wallpaper", look.wallpaperText()) { onOpen(FeaturePage.WALLPAPER) }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridTile(Modifier.weight(1f), FeaturePage.ICONS.icon, "App icons", look.icons()) { onOpen(FeaturePage.ICONS) }
            GridTile(Modifier.weight(1f), FeaturePage.STATUS_BAR.icon, "Status bar", look.statusBarText()) { onOpen(FeaturePage.STATUS_BAR) }
        } }
        item { SectionHeading("Flags") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridTile(Modifier.weight(1f), Icons.Default.Science, "Browse flags",
                if (modifiedFlags == 0) "None changed" else "$modifiedFlags changed", onFlags)
            GridTile(Modifier.weight(1f), FeaturePage.PRESETS.icon, "Presets", "Saved sets") { onOpen(FeaturePage.PRESETS) }
        } }
        item { FeatureTile(FeaturePage.QUICK_SWITCHES.icon, FeaturePage.QUICK_SWITCHES.title, FeaturePage.QUICK_SWITCHES.subtitle) { onOpen(FeaturePage.QUICK_SWITCHES) } }
        item { SectionHeading("More") }
        item { FeatureTile(FeaturePage.SYSTEM.icon, FeaturePage.SYSTEM.title, FeaturePage.SYSTEM.subtitle) { onOpen(FeaturePage.SYSTEM) } }
    }
}

/** Everything that changes how the car screen looks, each with its current state. */
@Composable internal fun LookScreen(model: ManagerViewModel, connected: Boolean, onOpen: (FeaturePage) -> Unit) {
    val look = remember(connected) { LookState(model, connected) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            Text("Look", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("How Android Auto looks on the car screen. Changes show after it restarts.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { FeatureTile(FeaturePage.THEMES.icon, FeaturePage.THEMES.title, look.themes()) { onOpen(FeaturePage.THEMES) } }
        item { FeatureTile(FeaturePage.WALLPAPER.icon, FeaturePage.WALLPAPER.title, look.wallpaperText()) { onOpen(FeaturePage.WALLPAPER) } }
        item { FeatureTile(FeaturePage.ICONS.icon, FeaturePage.ICONS.title, look.icons()) { onOpen(FeaturePage.ICONS) } }
        item { FeatureTile(FeaturePage.STATUS_BAR.icon, FeaturePage.STATUS_BAR.title, look.statusBarText()) { onOpen(FeaturePage.STATUS_BAR) } }
        item { FeatureTile(FeaturePage.LAUNCHER.icon, FeaturePage.LAUNCHER.title, look.launcherText()) { onOpen(FeaturePage.LAUNCHER) } }
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

/** A heading with an optional "?" and one card holding its rows. */
@Composable internal fun SettingsSection(
    title: String, tip: String? = null, content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            if (tip != null) TipIcon(title, tip)
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

/** A thin line between two rows of a section. */
@Composable internal fun RowDivider() =
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

/** The one reset control: shown only when a row differs from default. */
@Composable internal fun ResetIcon(onReset: () -> Unit) {
    IconButton(onReset, Modifier.size(48.dp)) { Icon(Icons.Default.Restore, "Back to default", Modifier.size(22.dp)) }
}

/** Label left, switch right; the whole row toggles. */
@Composable internal fun SwitchRow(
    label: String, checked: Boolean, onChange: (Boolean) -> Unit, tip: String? = null, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
        if (tip != null) TipIcon(label, tip)
        Spacer(Modifier.weight(1f))
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
        Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
        if (tip != null) TipIcon(label, tip)
        Spacer(Modifier.weight(1f))
        Text(colour?.let { "#" + io.github.highwindbr.aaxp.core.ColorMath.toHex(it) } ?: "Default",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (colour != null) ResetIcon(onReset) else Spacer(Modifier.width(48.dp))
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
            if (onReset != null) ResetIcon(onReset) else Spacer(Modifier.width(48.dp))
        }
        androidx.compose.material3.Slider(shown, { shown = it }, valueRange = range, steps = steps, enabled = enabled,
            onValueChangeFinished = { onChangeFinished(shown) })
    }
}

/** A row that opens a sub-screen or dialog: label, a short state, a chevron. */
@Composable internal fun LinkRow(label: String, state: String? = null, onClick: () -> Unit, tip: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
        if (tip != null) TipIcon(label, tip)
        Spacer(Modifier.weight(1f))
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
    val pageName: String, val onReset: (() -> Unit)? = null, val extra: List<Pair<String, () -> Unit>> = emptyList()
)

/** The frame around a feature's page: back arrow, title, and the app's messages at the bottom. */
@Composable internal fun FeaturePageScaffold(
    page: FeaturePage, messages: SnackbarHostState, model: ManagerViewModel, connected: Boolean, onBack: () -> Unit, scrolls: Boolean = true,
    menu: PageMenu? = null, content: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset && menu?.onReset != null) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Put everything on this page back to default?") },
        confirmButton = { TextButton({ confirmReset = false; menu.onReset.invoke() }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } })
    Scaffold(snackbarHost = { SnackbarHost(messages) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
