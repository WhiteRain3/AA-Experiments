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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.MapBorderSetting
import io.github.highwindbr.aaxp.core.Themes
import io.github.highwindbr.aaxp.core.TypedOverride
import io.github.highwindbr.aaxp.runtime.MapBorder
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
    // An old override of the dead border flag, saved by an earlier version, is removed once.
    LaunchedEffect(Unit) { model.removeDeadMapBorderFlagOnce() }
    // Everything shown here is read again after any look write finished (lookRevision), including Undo from the snackbar or Home.
    val written = model.lookRevision
    val roleClass = remember(connected, revision, local, written) { if (connected) model.themeRoleClass() else null }
    val roles = remember(connected, revision, local, written) { if (connected) model.themeColorsMap() else emptyMap() }
    val glyph = remember(connected, revision, local, written) { if (connected) model.glyphIconColors() else null }
    var extra by remember(connected, revision, written) { mutableStateOf(if (connected) model.resourceColors() else emptyMap()) }
    fun setExtra(next: Map<String, Int>) { extra = next; model.setResourceColors(next) }
    fun extraRow(group: String) = extra[group]

    KeepScrollAcross(open = picking != null)
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
        SectionHeader("Styles", STYLES_TIP)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.BUILT_IN.forEach { look ->
                Card(Modifier.width(220.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CarScreenMock(look.roles, look.glyphColors, Modifier.fillMaxWidth(), showCaption = false)
                        Text(look.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        // "Applied" while the saved colours are exactly this style's; it turns back into Apply when something changes.
                        val applied = roles == look.roles
                        OutlinedButton({ model.applyLook(look) { local++ } }, Modifier.fillMaxWidth(), enabled = connected && !applied) {
                            Text(if (applied) "Applied" else "Apply")
                        }
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
        }

        // 4. The outlines the knob draws.
        var borderWidth by remember(connected, revision, written) { mutableStateOf(if (connected) model.mapBorderWidth() else null) }
        SettingsSection("Focus outlines", tip = "The outlines drawn around what the knob is on. \"Map and card outline\" is the outline around the map and the " +
            "dashboard cards (it also colours the media card's outline). \"Rail and launcher ring\" is the ring on the rail and in the launcher; on a " +
            "head unit it did not come from the theme's Primary colour. The map outline's width is in dp; Android Auto's own is 6. " +
            "Not confirmed on a car yet, and the width not on a head unit either.") {
            ColourRow("Map and card outline", extraRow(ResourceColors.MAP_FOCUS),
                { picking = Triple("Map and card outline", extraRow(ResourceColors.MAP_FOCUS) ?: 0xFFF2F0F0.toInt()) { setExtra(extra + (ResourceColors.MAP_FOCUS to it)) } },
                { setExtra(extra - ResourceColors.MAP_FOCUS) })
            RowDivider()
            ColourRow("Rail and launcher ring", extraRow(ResourceColors.FOCUS_RING),
                { picking = Triple("Rail and launcher ring", extraRow(ResourceColors.FOCUS_RING) ?: 0xFFF2F0F0.toInt()) { setExtra(extra + (ResourceColors.FOCUS_RING to it)) } },
                { setExtra(extra - ResourceColors.FOCUS_RING) })
            RowDivider()
            SliderRow("Map outline width", MapBorderSetting.label(borderWidth), MapBorderSetting.sliderStart(borderWidth).toFloat(),
                MapBorder.MIN.toFloat()..MapBorder.MAX.toFloat(),
                onChangeFinished = { borderWidth = it.toInt(); model.setMapBorderWidth(it.toInt()) },
                onReset = if (borderWidth != null) ({ borderWidth = null; model.setMapBorderWidth(null) }) else null,
                enabled = connected, steps = MapBorder.MAX - MapBorder.MIN - 1)
        }
    }
}
