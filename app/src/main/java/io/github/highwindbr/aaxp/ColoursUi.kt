package io.github.highwindbr.aaxp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.DynamicOverrideCodec
import io.github.highwindbr.aaxp.core.MapBorderFlag
import io.github.highwindbr.aaxp.core.Themes
import io.github.highwindbr.aaxp.core.TypedOverride
import io.github.highwindbr.aaxp.runtime.ResourceColors

private const val STYLES_TIP = "A style sets the colours, the extra text and background colours, the icons, the status bar's " +
    "colours and background, and the wallpaper's darken and blur. Your hidden status bar parts, battery number and clock size stay. " +
    "The status bar and extra-colour parts are not yet confirmed on the car. You can undo from Home."

/**
 * The Colours page: the saved colours with their preview, ready-made styles, the colours the
 * colour slots do not reach, and the border around the map. Everything applies as soon as it is
 * chosen; the banner then offers the restart.
 */
@Composable
internal fun ColoursPage(
    model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean,
    revision: Int, onChanged: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var confirmDefault by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }
    var local by remember { mutableStateOf(0) }
    val roleClass = remember(connected, revision, local) { if (connected) model.themeRoleClass() else null }
    val roles = remember(connected, revision, local) { if (connected) model.themeColorsMap() else emptyMap() }
    val glyph = remember(connected, revision, local) { if (connected) model.glyphIconColors() else null }
    var extra by remember(connected, revision) { mutableStateOf(if (connected) model.resourceColors() else emptyMap()) }
    fun setExtra(next: Map<String, Int>) { extra = next; model.setResourceColors(next) }
    fun extraRow(group: String) = extra[group]

    if (editing) ThemeEditor(model) { editing = false; local++ }
    picking?.let { (title, start, apply) ->
        ColorPickerDialog(title, start, onDismiss = { picking = null }) { picked -> apply(picked); picking = null }
    }
    if (confirmDefault) AlertDialog(onDismissRequest = { confirmDefault = false },
        title = { Text("Put the colours back to default?") },
        text = { Text("The colour slots go back to Android Auto's own. You can undo from Home.") },
        confirmButton = { TextButton({ confirmDefault = false; model.setThemeColors("") { local++ } }) { Text("Default") } },
        dismissButton = { TextButton({ confirmDefault = false }) { Text("Cancel") } })

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // 1. What is saved now.
        if (roleClass == null) {
            PreviewCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Colours need a one-time scan", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TipIcon("One-time scan", "Reads the installed Android Auto to find its colour slots. Takes a little while.")
                }
                Button({ searching = true; model.locateThemeRoles { searching = false; local++ } },
                    Modifier.fillMaxWidth(), enabled = connected && !searching) { Text(if (searching) "Scanning…" else "Scan") }
            }
        } else {
            PreviewCard {
                CarScreenMock(roles, glyph, Modifier.fillMaxWidth(), showCaption = false)
                Text(if (roles.isEmpty()) "Default" else "${roles.size} colours set", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ editing = true }, Modifier.weight(1f), enabled = connected) { Text("Edit colours") }
                    TextButton({ confirmDefault = true }, enabled = connected && roles.isNotEmpty()) { Text("Default") }
                }
            }
        }

        // 2. Styles.
        Row(Modifier.padding(start = 4.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Styles", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            TipIcon("Styles", STYLES_TIP)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.BUILT_IN.forEach { look ->
                Card(Modifier.width(220.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CarScreenMock(look.roles, look.glyphColors, Modifier.fillMaxWidth(), showCaption = false)
                        Text(look.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedButton({ model.applyLook(look); local++ }, Modifier.fillMaxWidth(), enabled = connected) { Text("Apply") }
                    }
                }
            }
        }

        // 3. Colours the colour slots do not reach.
        SettingsSection("Text and backgrounds") {
            ColourRow("Labels and rail icons", extraRow(ResourceColors.TEXT),
                { picking = Triple("Labels and rail icons", extraRow(ResourceColors.TEXT) ?: 0xFFF2F0F0.toInt()) { setExtra(extra + (ResourceColors.TEXT to it)) } },
                { setExtra(extra - ResourceColors.TEXT) },
                tip = "App labels, the clock and the rail's own icons take their colour from a fixed list Android Auto reads, not from the colour slots. " +
                    "That list is used in many places, so other text may change too. Not tried on a car yet.")
            RowDivider()
            ColourRow("Launcher background", extraRow(ResourceColors.LAUNCHER_BACKGROUND),
                { picking = Triple("Launcher background", extraRow(ResourceColors.LAUNCHER_BACKGROUND) ?: 0xFF252626.toInt()) { setExtra(extra + (ResourceColors.LAUNCHER_BACKGROUND to it)) } },
                { setExtra(extra - ResourceColors.LAUNCHER_BACKGROUND) },
                tip = "The colour behind the app grid. Not tried on a car yet.")
            RowDivider()
            ColourRow("Knob focus ring", extraRow(ResourceColors.FOCUS_RING),
                { picking = Triple("Knob focus ring", extraRow(ResourceColors.FOCUS_RING) ?: 0xFFF2F0F0.toInt()) { setExtra(extra + (ResourceColors.FOCUS_RING to it)) } },
                { setExtra(extra - ResourceColors.FOCUS_RING) },
                tip = "The ring around the item the knob is on in the launcher. This is a guess: Android Auto may work its colour out from the " +
                    "theme's Primary colour instead, and then this does nothing. If so, change Primary in the theme. Not tried on a car yet.")
        }

        // 4. Border around the map.
        val status by produceState(MapBorderFlag.Status.UNKNOWN, connected) { value = model.mapBorderStatus() }
        val width = MapBorderFlag.current(overrides.mapValues { DynamicOverrideCodec.serialize(it.value) })
        SettingsSection("Map border", tip = "A border around the navigation window. The unit of the width is not known (it may not be dp). " +
            "Not tried on a car yet.") {
            ColourRow("Border colour", extraRow(ResourceColors.MAP_FOCUS),
                { picking = Triple("Border colour", extraRow(ResourceColors.MAP_FOCUS) ?: 0xFFF2F0F0.toInt()) { setExtra(extra + (ResourceColors.MAP_FOCUS to it)) } },
                { setExtra(extra - ResourceColors.MAP_FOCUS) })
            RowDivider()
            SliderRow("Border width", width?.toString() ?: "Default", (width ?: 0).toFloat(),
                MapBorderFlag.MIN.toFloat()..MapBorderFlag.MAX.toFloat(),
                onChangeFinished = { model.setMapBorderWidth(it.toInt()) },
                onReset = if (width != null) ({ model.setMapBorderWidth(null) }) else null,
                enabled = connected && status == MapBorderFlag.Status.OK, steps = MapBorderFlag.MAX - 1)
            val note = when (status) {
                MapBorderFlag.Status.MISSING -> "Not in this Android Auto version"
                MapBorderFlag.Status.NOT_INTEGER -> "Can't be changed in this version"
                MapBorderFlag.Status.UNKNOWN -> "Checking…"
                MapBorderFlag.Status.OK -> null
            }
            if (note != null) Text(note, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = if (status == MapBorderFlag.Status.UNKNOWN) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }
}
