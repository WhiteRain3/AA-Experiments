package io.github.highwindbr.aaxp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.runtime.StatusBarTweaks
import kotlinx.coroutines.delay

/** Shown only after crash safe mode switched the look off inside Android Auto. */
@Composable internal fun SafeModeBanner(model: ManagerViewModel, connected: Boolean) {
    var report by remember { mutableStateOf(model.safeModeReport()) }
    val text = report ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Look changes are switched off", fontWeight = FontWeight.Bold)
            Text("Android Auto crashed twice within a few minutes while the wallpaper, glyph icons, colours or status bar changes were on, so they were left out ($text). " +
                "Flag overrides are not affected. Changing any look setting, or the button below, switches them back on at the next restart.",
                style = MaterialTheme.typography.bodySmall)
            Button({ model.resetSafeMode { report = model.safeModeReport() } }, Modifier.fillMaxWidth(), enabled = connected) {
                Text("Switch look changes back on")
            }
        }
    }
}

private val STATUS_BAR_PARTS = listOf(
    "clock" to "Clock", "signal_icon" to "Signal", "battery_level" to "Battery",
    "dnd_icon" to "Do not disturb icon", "notification_count_wrapper" to "Notification count")

/** Hide or recolour parts of Android Auto's status bar. */
@Composable internal fun StatusBarCard(model: ManagerViewModel, connected: Boolean) {
    val stored = remember(connected) { if (connected) StatusBarTweaks.parse(model.statusBarConfig()) else null }
    var hidden by remember(stored) { mutableStateOf(stored?.hide?.toSet().orEmpty()) }
    var clockColor by remember(stored) { mutableStateOf(stored?.clockColor) }
    var iconColor by remember(stored) { mutableStateOf(stored?.iconColor) }
    var noPlate by remember(stored) { mutableStateOf(stored?.noPlate ?: false) }
    var picking by remember { mutableStateOf<Pair<String, (Int) -> Unit>?>(null) }
    picking?.let { (title, apply) ->
        ColorPickerDialog(title, 0xFFFFFFFF.toInt(), onDismiss = { picking = null }) { apply(it); picking = null }
    }
    @Composable fun switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = null)
        }
    }
    @Composable fun colorRow(title: String, color: Int?, onChange: (Int?) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorSwatch(color, onClick = { picking = title to { picked -> onChange(picked) } })
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (color != null) TextButton({ onChange(null) }) { Text("Default") }
        }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hide or recolour the clock, signal and battery.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HowThisWorks("Not tried on a real car yet: Android Auto may repaint the colours when it switches between day and night. Changes show after Android Auto restarts.")
        Text("Hide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        STATUS_BAR_PARTS.forEach { (id, label) ->
            switchRow(label, id in hidden) { hidden = if (it) hidden + id else hidden - id }
        }
        switchRow("Remove the background behind the status bar", noPlate) { noPlate = it }
        colorRow("Clock colour", clockColor) { clockColor = it }
        colorRow("Icon colour", iconColor) { iconColor = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                // Keep the order stable so the stored text does not change needlessly.
                val hide = LinkedHashSet(StatusBarTweaks.HIDEABLE.filter { it in hidden })
                model.setStatusBarConfig(StatusBarTweaks.encode(StatusBarTweaks.Config(hide, clockColor, iconColor, noPlate)))
            }, Modifier.weight(1f), enabled = connected) { Text("Save") }
            OutlinedButton({
                hidden = emptySet(); clockColor = null; iconColor = null; noPlate = false
                model.setStatusBarConfig(null)
            }, Modifier.weight(1f), enabled = connected && stored != null) { Text("Turn off") }
        }
    } }
}

private val EXTRA_COLOURS = listOf(
    io.github.highwindbr.aaxp.runtime.ResourceColors.TEXT to "Labels, clock and rail icons",
    io.github.highwindbr.aaxp.runtime.ResourceColors.LAUNCHER_BACKGROUND to "Launcher background",
    io.github.highwindbr.aaxp.runtime.ResourceColors.MAP_FOCUS to "Focus border around the map")

/** Colours that the palette roles do not reach, because Android Auto reads them from fixed resources. */
@Composable internal fun ExtraColorsCard(model: ManagerViewModel, connected: Boolean) {
    val stored = remember(connected) { if (connected) model.resourceColors() else emptyMap() }
    var colors by remember(stored) { mutableStateOf(stored) }
    var picking by remember { mutableStateOf<Pair<String, String>?>(null) }
    picking?.let { (group, label) ->
        ColorPickerDialog(label, colors[group] ?: 0xFFF2F0F0.toInt(), onDismiss = { picking = null }) {
            colors = colors + (group to it); picking = null
        }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("More colours (experimental)", fontWeight = FontWeight.Bold)
        Text("A few things ignore the theme because Android Auto colours them from a fixed list: app labels, the clock, the rail's own icons, the launcher background and the border around the map. Set them here. Not yet tried on a car; these colours are used in many places, so other text may change too.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EXTRA_COLOURS.forEach { (group, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSwatch(colors[group], onClick = { picking = group to label })
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (colors[group] != null) TextButton({ colors = colors - group }) { Text("Android Auto's own") }
            }
        }
        Text("The launcher's focus ring is not here: it appears to follow the theme's Primary colour as a lighter tint.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({ model.setResourceColors(colors) }, Modifier.fillMaxWidth(), enabled = connected) { Text("Save") }
    } }
}

/** Check this fork's GitHub releases for a newer build and install it. */
@Composable internal fun UpdateCard(model: ManagerViewModel) {
    val installed = remember { model.installedVersionName() }
    var available by remember { mutableStateOf<io.github.highwindbr.aaxp.core.AvailableUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf<Int?>(null) }
    var onOpen by remember { mutableStateOf(model.checkUpdatesOnOpen()) }
    // Result of the last check made from this card: null until one has finished.
    var upToDate by remember { mutableStateOf<Boolean?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (onOpen) model.checkForUpdate(quiet = true) { update, _ -> available = update } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Installed: $installed. Checking contacts github.com to read this fork's releases; nothing about you or the phone is sent.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        available?.let { update ->
            Text("${update.versionName} is available" + if (update.sizeBytes > 0) " (${update.sizeBytes / (1024 * 1024)} MB)" else "", fontWeight = FontWeight.SemiBold)
            if (update.notes.isNotBlank()) Text(update.notes.trim(), style = MaterialTheme.typography.bodySmall)
            Button({ busy = true; percent = 0
                model.installUpdate(update, { percent = it }) { busy = false; percent = null } },
                Modifier.fillMaxWidth(), enabled = !busy) { Text(percent?.let { "Downloading… $it%" } ?: "Download and install") }
        }
        if (available == null && upToDate == true) Text("No newer build is published (checked ${
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}).", style = MaterialTheme.typography.bodyMedium)
        failure?.let { Text("The check failed: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        OutlinedButton({ busy = true; upToDate = null; failure = null
            model.checkForUpdate { update, error -> available = update; failure = error; upToDate = update == null && error == null; busy = false } }, Modifier.fillMaxWidth(), enabled = !busy) {
            Text(if (busy && percent == null) "Checking…" else "Check for updates")
        }
        Row(Modifier.fillMaxWidth().toggleable(value = onOpen, role = Role.Switch, onValueChange = { onOpen = it; model.setCheckUpdatesOnOpen(it) }),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Check each time the app opens", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = onOpen, onCheckedChange = null)
        }
    } }
}

/** Save the whole look to one file, or load one: a style pack to share and a backup in one. */
@Composable internal fun LookFileCard(model: ManagerViewModel, connected: Boolean) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { out ->
                checkNotNull(out) { "Cannot write the file" }.write(model.exportLook().toByteArray(Charsets.UTF_8))
            }
        }.fold({ model.reportMessage("Look saved to file.") }, { model.reportMessage("Look not saved: ${it.message}") })
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                val bytes = checkNotNull(input) { "Cannot read the file" }.readBytes()
                require(bytes.size <= 24 * 1024 * 1024) { "File is too large to be a look file" }
                String(bytes, Charsets.UTF_8)
            }
        }.fold({ pending = it }, { model.reportMessage("Look not loaded: ${it.message}") })
    }
    pending?.let { text ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Load this look?") },
            text = { Text("This replaces the colours, glyph icon colours and status bar changes in use now, and the car wallpaper if the file has one. " +
                "Themes and flag presets from the file are added to yours; flag presets are not applied. Save your current look to a file first if you want to come back to it.") },
            confirmButton = { TextButton({ model.importLook(text); pending = null }) { Text("Load") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("One file with the whole look: colours, glyph icons, status bar changes, the car wallpaper with its placement, your themes and your flag presets. Use it as a backup or to pass a style on.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ saver.launch("aa-look.json") }, Modifier.weight(1f), enabled = connected) { Text("Save look to file") }
            OutlinedButton({ opener.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, Modifier.weight(1f), enabled = connected) { Text("Load a look") }
        }
    } }
}

/**
 * Asks Android Auto to describe what it is showing (which views, where, what colours; never any
 * text) and lets the user save that description to a file to send along with a screenshot.
 */
@Composable internal fun ScreenLayoutCard(model: ManagerViewModel, connected: Boolean) {
    val context = LocalContext.current
    var captures by remember { mutableStateOf(model.screenLayoutCaptures()) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        if (waiting) { delay(3_000); captures = model.screenLayoutCaptures(); waiting = false }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { out ->
                checkNotNull(out) { "Cannot write the file" }.write(model.screenLayoutText().toByteArray(Charsets.UTF_8))
            }
        }.fold({ model.reportMessage("Screen layout saved.") }, { model.reportMessage("Screen layout not saved: ${it.message}") })
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("While Android Auto is on the car screen, this records how that screen is built: each element's type, position, size and colours, plus the screen's size and density. It never records text such as messages or song titles. The file stays on this phone until you save and share it yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (captures.isEmpty()) "Nothing captured yet." else "Captured: " + captures.joinToString { "${it.first} (${it.second / 1024} KB)" },
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ model.requestScreenLayout(); waiting = true }, Modifier.weight(1f), enabled = connected && !waiting) {
                Text(if (waiting) "Capturing…" else "Capture now")
            }
            OutlinedButton({ saver.launch("aa-screen-layout.txt") }, Modifier.weight(1f), enabled = captures.isNotEmpty()) { Text("Save to file") }
        }
        if (captures.isNotEmpty()) TextButton({ model.clearScreenLayouts(); captures = model.screenLayoutCaptures() }) { Text("Delete captures") }
    } }
}
