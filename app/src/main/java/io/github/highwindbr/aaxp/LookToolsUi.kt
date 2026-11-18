package io.github.highwindbr.aaxp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

private fun iconChoiceText(choice: String): String = when (choice) {
    io.github.highwindbr.aaxp.runtime.GlyphIcons.ORIGINAL -> "Its own icon"
    io.github.highwindbr.aaxp.runtime.GlyphIcons.GLYPH -> "Glyph"
    else -> "Glyph, own colours"
}

/** A different icon choice for single apps: its own icon, a glyph, or a glyph in colours of its own. */
@Composable internal fun PerAppIconsCard(model: ManagerViewModel, connected: Boolean, resetSignal: Int = 0) {
    var choices by remember(connected) { mutableStateOf(if (connected) model.perAppIcons() else emptyMap()) }
    // The page menu's reset has already written the default; this only brings the rows back to it.
    LaunchedEffect(resetSignal) { if (resetSignal != 0) choices = emptyMap() }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = model.launchableApps() }
    val names = remember(apps) { apps.toMap() }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    fun store(next: Map<String, String>) { choices = next; model.setPerAppIcons(next) }

    if (adding) {
        var search by remember { mutableStateOf("") }
        val shown = apps.filter { it.first !in choices && (search.isBlank() || it.second.contains(search, true) || it.first.contains(search, true)) }
        androidx.compose.material3.AlertDialog(onDismissRequest = { adding = false },
            confirmButton = { TextButton({ adding = false }) { Text("Close") } },
            title = { Text("Choose an app") },
            text = { Column {
                androidx.compose.material3.OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(shown.size) { index ->
                        val (pkg, label) = shown[index]
                        TextButton({ adding = false; editing = pkg }, Modifier.fillMaxWidth()) {
                            Text(label, Modifier.fillMaxWidth())
                        }
                    }
                }
            } })
    }
    editing?.let { pkg ->
        val current = choices[pkg]
        val startColors = io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(current?.takeIf { it != io.github.highwindbr.aaxp.runtime.GlyphIcons.ORIGINAL && it != io.github.highwindbr.aaxp.runtime.GlyphIcons.GLYPH }
            ?: model.glyphIconColors() ?: io.github.highwindbr.aaxp.runtime.GlyphIcons.DEFAULT_COLORS) ?: intArrayOf(-1, 0xFF000000.toInt())
        var kind by remember(pkg) { mutableStateOf(when (current) { null, io.github.highwindbr.aaxp.runtime.GlyphIcons.ORIGINAL -> 0; io.github.highwindbr.aaxp.runtime.GlyphIcons.GLYPH -> 1; else -> 2 }) }
        var glyph by remember(pkg) { mutableStateOf(startColors[0]) }
        var background by remember(pkg) { mutableStateOf(startColors[1]) }
        var picking by remember(pkg) { mutableStateOf(0) }
        if (picking != 0) ColorPickerDialog(if (picking == 1) "Glyph colour" else "Background colour",
            if (picking == 1) glyph else background, onDismiss = { picking = 0 }) { if (picking == 1) glyph = it else background = it; picking = 0 }
        else androidx.compose.material3.AlertDialog(onDismissRequest = { editing = null },
            confirmButton = { TextButton({
                val choice = when (kind) {
                    0 -> io.github.highwindbr.aaxp.runtime.GlyphIcons.ORIGINAL
                    1 -> io.github.highwindbr.aaxp.runtime.GlyphIcons.GLYPH
                    else -> "${io.github.highwindbr.aaxp.core.ColorMath.toHex(glyph)},${io.github.highwindbr.aaxp.core.ColorMath.toHex(background)}"
                }
                store(choices + (pkg to choice)); editing = null
            }, enabled = connected) { Text("Save") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
            title = { Text(names[pkg] ?: pkg) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Its own icon", "Glyph in the main colours", "Glyph in its own colours").forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().toggleable(value = kind == index, role = Role.RadioButton, onValueChange = { kind = index }),
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = kind == index, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (kind == 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorSwatch(glyph, onClick = { picking = 1 }); Text("Glyph", Modifier.weight(1f))
                    ColorSwatch(background, onClick = { picking = 2 }); Text("Background", Modifier.weight(1f))
                }
            } })
    }
    SettingsSection("Single apps", tip = "Give one app a different icon from the rest: its own icon, a glyph, or a glyph in colours of its own. " +
        "This works with single-colour icons on or off. It applies to app icons in the launcher, the rail and on cards; small icons inside " +
        "notifications are not covered. Phone, calendar and settings shortcuts belong to the phone's own dialer, calendar and settings apps, " +
        "so choose those. Not tried on a real car yet.") {
        choices.forEach { (pkg, choice) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = connected) { editing = pkg },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(names[pkg] ?: pkg, style = MaterialTheme.typography.bodyLarge)
                    Text(iconChoiceText(choice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ResetIcon { store(choices - pkg) }
            }
            RowDivider()
        }
        LinkRow("Add an app", onClick = { if (connected && choices.size < io.github.highwindbr.aaxp.runtime.GlyphIcons.MAX_PER_APP) adding = true })
    }
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
            confirmButton = { TextButton({ model.undoThen("loading a look file") { val before = model.wallpaperStampNow(); model.importLook(text) { model.markWallpaperAppliedIfChanged(before) } }; pending = null }) { Text("Load") } },
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
