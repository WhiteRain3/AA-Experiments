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
    var batteryPercent by remember(stored) { mutableStateOf(stored?.batteryPercent ?: false) }
    var batteryColor by remember(stored) { mutableStateOf(stored?.batteryColor) }
    var batterySize by remember(stored) { mutableStateOf(stored?.batterySize ?: StatusBarTweaks.DEFAULT_BATTERY_SIZE) }
    var clockSize by remember(stored) { mutableStateOf(stored?.clockSize ?: StatusBarTweaks.DEFAULT_CLOCK_SIZE) }
    var clockBold by remember(stored) { mutableStateOf(stored?.clockBold ?: false) }
    var plateColor by remember(stored) { mutableStateOf(stored?.plateColor) }
    var plateRadius by remember(stored) { mutableStateOf(stored?.plateRadius) }
    var iconSize by remember(stored) { mutableStateOf(stored?.iconSize ?: StatusBarTweaks.DEFAULT_ICON_SIZE) }
    var byLevel by remember(stored) { mutableStateOf(stored?.batteryByLevel ?: false) }
    var levelHigh by remember(stored) { mutableStateOf(stored?.batteryHigh ?: StatusBarTweaks.DEFAULT_BATTERY_HIGH) }
    var levelLow by remember(stored) { mutableStateOf(stored?.batteryLow ?: StatusBarTweaks.DEFAULT_BATTERY_LOW) }
    var highColor by remember(stored) { mutableStateOf(stored?.batteryHighColor ?: StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR) }
    var lowColor by remember(stored) { mutableStateOf(stored?.batteryLowColor ?: StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR) }
    var midColor by remember(stored) { mutableStateOf(stored?.batteryMidColor) }
    var chargeColor by remember(stored) { mutableStateOf(stored?.batteryChargeColor) }
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
        switchRow("Show battery as a percentage", batteryPercent) { batteryPercent = it }
        if (batteryPercent) {
            Text("Percentage size: $batterySize% of the clock", style = MaterialTheme.typography.labelLarge)
            androidx.compose.material3.Slider(batterySize.toFloat(), { batterySize = (it / 5).toInt() * 5 },
                valueRange = StatusBarTweaks.MIN_BATTERY_SIZE.toFloat()..StatusBarTweaks.MAX_BATTERY_SIZE.toFloat())
            colorRow("Percentage colour", batteryColor) { batteryColor = it }
            switchRow("Colour by battery level", byLevel) { byLevel = it }
            if (byLevel) {
                Text("High: $levelHigh% and above", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(levelHigh.toFloat(), { levelHigh = ((it / 5).toInt() * 5).coerceAtLeast(levelLow + 5) },
                    valueRange = 10f..100f)
                colorRow("Colour when high", highColor) { highColor = it ?: StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR }
                colorRow("Colour in between (Default: the percentage colour)", midColor) { midColor = it }
                Text("Low: below $levelLow%", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(levelLow.toFloat(), { levelLow = ((it / 5).toInt() * 5).coerceAtMost(levelHigh - 5) },
                    valueRange = 0f..90f)
                colorRow("Colour when low", lowColor) { lowColor = it ?: StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR }
                colorRow("Colour while charging (optional)", chargeColor) { chargeColor = it }
            }
        }
        Text("Background plate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        switchRow("Remove the background behind the status bar", noPlate) { noPlate = it }
        if (!noPlate) {
            colorRow("Plate colour (lower the opacity in the picker for see-through)", plateColor) { plateColor = it }
            // Android Auto 18.0's plate only takes a corner radius together with a colour the module draws.
            if (plateColor == null) {
                plateRadius = null
                Text("Pick a plate colour to also set how round its corners are.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else switchRow("Set the plate's corner roundness", plateRadius != null) { plateRadius = if (it) 12 else null }
            plateRadius?.let { radius ->
                Text("Corners: $radius (0 is square)", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(radius.toFloat(), { plateRadius = (it / 2).toInt() * 2 },
                    valueRange = 0f..StatusBarTweaks.MAX_PLATE_RADIUS.toFloat())
                Text("Not tried on a car yet. Android Auto may still draw its own outline in the old shape on top.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Clock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Clock size: $clockSize%", style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(clockSize.toFloat(), { clockSize = (it / 5).toInt() * 5 },
            valueRange = StatusBarTweaks.MIN_CLOCK_SIZE.toFloat()..StatusBarTweaks.MAX_CLOCK_SIZE.toFloat())
        switchRow("Bold clock", clockBold) { clockBold = it }
        colorRow("Clock colour", clockColor) { clockColor = it }
        Text("Signal and battery icons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Icon size: $iconSize%", style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(iconSize.toFloat(), { iconSize = (it / 5).toInt() * 5 },
            valueRange = StatusBarTweaks.MIN_ICON_SIZE.toFloat()..StatusBarTweaks.MAX_ICON_SIZE.toFloat())
        colorRow("Icon colour", iconColor) { iconColor = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                // Keep the order stable so the stored text does not change needlessly.
                val hide = LinkedHashSet(StatusBarTweaks.HIDEABLE.filter { it in hidden })
                val builder = StatusBarTweaks.Builder()
                builder.hide = hide; builder.clockColor = clockColor; builder.iconColor = iconColor; builder.noPlate = noPlate
                builder.batteryPercent = batteryPercent; builder.batteryColor = batteryColor; builder.batterySize = batterySize
                builder.clockSize = clockSize; builder.clockBold = clockBold
                builder.plateColor = plateColor; builder.plateRadius = plateRadius; builder.iconSize = iconSize
                builder.batteryByLevel = byLevel; builder.batteryHigh = levelHigh; builder.batteryLow = levelLow
                builder.batteryHighColor = highColor; builder.batteryLowColor = lowColor
                builder.batteryMidColor = midColor; builder.batteryChargeColor = chargeColor
                model.setStatusBarConfig(StatusBarTweaks.encode(builder.build()))
            }, Modifier.weight(1f), enabled = connected) { Text("Save") }
            OutlinedButton({
                hidden = emptySet(); clockColor = null; iconColor = null; noPlate = false; batteryPercent = false
                batteryColor = null; batterySize = StatusBarTweaks.DEFAULT_BATTERY_SIZE
                clockSize = StatusBarTweaks.DEFAULT_CLOCK_SIZE; clockBold = false
                plateColor = null; plateRadius = null; iconSize = StatusBarTweaks.DEFAULT_ICON_SIZE
                byLevel = false; levelHigh = StatusBarTweaks.DEFAULT_BATTERY_HIGH; levelLow = StatusBarTweaks.DEFAULT_BATTERY_LOW
                highColor = StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR; lowColor = StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR
                midColor = null; chargeColor = null
                model.setStatusBarConfig(null)
            }, Modifier.weight(1f), enabled = connected && stored != null) { Text("Turn off") }
        }
    } }
}

private fun iconChoiceText(choice: String): String = when (choice) {
    io.github.highwindbr.aaxp.runtime.GlyphIcons.ORIGINAL -> "Its own icon"
    io.github.highwindbr.aaxp.runtime.GlyphIcons.GLYPH -> "Glyph, main colours"
    else -> "Glyph, own colours"
}

/** A different icon choice for single apps: its own icon, a glyph, or a glyph in colours of its own. */
@Composable internal fun PerAppIconsCard(model: ManagerViewModel, connected: Boolean) {
    var choices by remember(connected) { mutableStateOf(if (connected) model.perAppIcons() else emptyMap()) }
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
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Single apps", fontWeight = FontWeight.Bold)
        Text("Give one app a different icon from the rest: its own icon, a glyph, or a glyph in colours of its own. This works with glyph icons on or off.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HowThisWorks("Not tried on a real car yet. It applies to app icons in the launcher, the rail and on cards; small icons inside notifications are not covered. Phone, calendar and settings shortcuts belong to the phone's own dialer, calendar and settings apps, so choose those. Changes show after Android Auto restarts.")
        choices.forEach { (pkg, choice) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(names[pkg] ?: pkg, style = MaterialTheme.typography.bodyMedium)
                    Text(iconChoiceText(choice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ editing = pkg }, enabled = connected) { Text("Change") }
                TextButton({ store(choices - pkg) }, enabled = connected) { Text("Remove") }
            }
        }
        OutlinedButton({ adding = true }, Modifier.fillMaxWidth(),
            enabled = connected && choices.size < io.github.highwindbr.aaxp.runtime.GlyphIcons.MAX_PER_APP) { Text("Add an app") }
    } }
}

/** Colour of the rail strip and how strongly the wallpaper shows there. */
@Composable internal fun RailLookCard(model: ManagerViewModel, connected: Boolean) {
    val stored = remember(connected) { io.github.highwindbr.aaxp.runtime.CarWallpaper.parseRailLook(if (connected) model.railLook() else null) }
    var color by remember(stored) { mutableStateOf(stored.color) }
    var strength by remember(stored) { mutableStateOf(stored.strength) }
    var picking by remember { mutableStateOf(false) }
    if (picking) ColorPickerDialog("Rail background", color ?: io.github.highwindbr.aaxp.runtime.CarWallpaper.DEFAULT_RAIL_COLOR,
        onDismiss = { picking = false }) { color = it; picking = false }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rail strip", fontWeight = FontWeight.Bold)
        Text("The strip with the clock and app shortcuts. Pick its background colour, and how strongly the wallpaper shows over that colour when the wallpaper covers the whole screen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HowThisWorks("Not tried on a real car yet. Android Auto's own launcher shows the wallpaper at 80% over dark grey, which is the starting point here. A colour without a whole-screen wallpaper paints the strip in that colour. Changes show after Android Auto restarts.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorSwatch(color, onClick = { picking = true })
            Text("Background colour", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (color != null) TextButton({ color = null }) { Text("Default") }
        }
        Text("Wallpaper strength: $strength%", style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(strength.toFloat(), { strength = (it / 5).toInt() * 5 }, valueRange = 0f..100f)
        Button({
            model.setRailLook(io.github.highwindbr.aaxp.runtime.CarWallpaper.encodeRailLook(
                io.github.highwindbr.aaxp.runtime.CarWallpaper.RailLook(color, strength)))
        }, Modifier.fillMaxWidth(), enabled = connected) { Text("Save") }
    } }
}

/** Changes to the launcher, the grid of app icons. */
@Composable internal fun LauncherCard(model: ManagerViewModel, connected: Boolean) {
    val stored = remember(connected) {
        if (connected) io.github.highwindbr.aaxp.runtime.LauncherTweaks.parse(model.launcherConfig()) else null
    }
    var hideNames by remember(stored) { mutableStateOf(stored?.hideNames ?: false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Show only the icons in the launcher, without the app names under them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HowThisWorks("Not tried on a real car yet. The names are hidden, not removed; whether the rows of icons move closer together or keep an empty gap where the names were is not known until it is seen on the car. Changes show after Android Auto restarts.")
        Row(Modifier.fillMaxWidth().toggleable(value = hideNames, enabled = connected, role = Role.Switch, onValueChange = {
            hideNames = it
            model.setLauncherConfig(io.github.highwindbr.aaxp.runtime.LauncherTweaks.encode(
                io.github.highwindbr.aaxp.runtime.LauncherTweaks.Config(it)))
        }), verticalAlignment = Alignment.CenterVertically) {
            Text("Icons only (hide app names)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hideNames, onCheckedChange = null, enabled = connected)
        }
    } }
}

private val EXTRA_COLOURS = listOf(
    io.github.highwindbr.aaxp.runtime.ResourceColors.TEXT to "Labels, clock and rail icons",
    io.github.highwindbr.aaxp.runtime.ResourceColors.LAUNCHER_BACKGROUND to "Launcher background",
    io.github.highwindbr.aaxp.runtime.ResourceColors.MAP_FOCUS to "Focus border around the map",
    io.github.highwindbr.aaxp.runtime.ResourceColors.FOCUS_RING to "Knob's focus ring in the launcher (may do nothing)")

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
        Text("The focus ring is a guess: Android Auto may work its colour out from the theme's Primary colour instead, and then this setting does nothing. If so, change Primary in the theme.",
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
