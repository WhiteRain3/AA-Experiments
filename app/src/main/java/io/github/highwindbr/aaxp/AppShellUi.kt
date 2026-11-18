package io.github.highwindbr.aaxp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
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
    THEMES("Themes and colours", "Recolour Android Auto's interface", Icons.Default.Palette),
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

/** A collapsed "How this works" section for the long caveats that most people do not need each time. */
@Composable internal fun HowThisWorks(text: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton({ open = !open }) { Text(if (open) "How this works ▲" else "How this works ▼") }
        if (open) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The frame around a feature's page: back arrow, title, and the app's messages at the bottom. */
@Composable internal fun FeaturePageScaffold(
    page: FeaturePage, messages: SnackbarHostState, model: ManagerViewModel, connected: Boolean, onBack: () -> Unit, scrolls: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(messages) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconBadge(page.icon, 40)
            }
            if (scrolls) LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item { ChangesWaitingBanner(model, connected); content() }
            } else Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
