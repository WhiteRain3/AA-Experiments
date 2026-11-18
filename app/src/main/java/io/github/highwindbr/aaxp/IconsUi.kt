package io.github.highwindbr.aaxp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.ColorMath
import io.github.highwindbr.aaxp.core.LauncherView
import io.github.highwindbr.aaxp.runtime.GlyphIcons
import io.github.highwindbr.aaxp.runtime.LauncherTweaks

/**
 * The App icons page: single-colour icons (switch, colours, size), icons for single apps, and the
 * launcher's app names. Everything applies as soon as it is changed (sliders on release); only
 * the per-app dialog keeps its Save.
 */
@Composable
internal fun AppIconsPage(model: ManagerViewModel, connected: Boolean, resetSignal: Int) {
    val stored = remember(connected) { if (connected) model.glyphIconColors() else null }
    var enabled by remember(connected) { mutableStateOf(stored != null) }
    val start = remember(connected) { GlyphIcons.parseColors(stored ?: GlyphIcons.DEFAULT_COLORS) ?: intArrayOf(-1, 0) }
    var glyph by remember(connected) { mutableStateOf(start[0]) }
    var background by remember(connected) { mutableStateOf(start[1]) }
    var figureSize by remember(connected) { mutableStateOf(if (connected) model.glyphIconSize() else GlyphIcons.DEFAULT_SIZE) }
    val launcher = remember(connected) { if (connected) LauncherTweaks.parse(model.launcherConfig()) else null }
    var hideNames by remember(launcher) { mutableStateOf(launcher?.hideNames ?: false) }
    var picking by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }
    val defaults = remember { GlyphIcons.parseColors(GlyphIcons.DEFAULT_COLORS) ?: intArrayOf(-1, 0) }

    fun colours() = "${ColorMath.toHex(glyph)},${ColorMath.toHex(background)}"
    // Writes the icon colours and size; only while the switch is on.
    fun commit() { if (enabled) model.setGlyphIcons(colours(), figureSize) }
    // The page menu's reset has already written the default; this only brings the rows back to it.
    LaunchedEffect(resetSignal) {
        if (resetSignal != 0) {
            enabled = false; glyph = defaults[0]; background = defaults[1]; figureSize = GlyphIcons.DEFAULT_SIZE; hideNames = false
        }
    }
    picking?.let { (title, start0, apply) ->
        ColorPickerDialog(title, start0, onDismiss = { picking = null }) { picked -> apply(picked); picking = null; commit() }
    }

    Column {
        PreviewCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(Icons.Default.Call, Icons.Default.Map, Icons.Default.MusicNote, Icons.Default.Textsms, Icons.Default.Settings).forEach { icon ->
                    val plate = if (enabled) Color(background) else Color(0xFF3A3839)
                    val figure = if (enabled) Color(glyph) else Color(0xFF9A9A9A)
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1B)).background(plate), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size((28 * (if (enabled) figureSize else GlyphIcons.DEFAULT_SIZE) / 100f).dp), tint = figure)
                    }
                }
            }
            Text(if (enabled) "Single-colour icons" else "Normal icons", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection("Glyph icons", tip = "Show apps as single-colour icons, using each app's own themed-icon layer. Apps without a themed icon keep their normal one.") {
            SwitchRow("Single-colour icons", enabled, {
                enabled = it
                if (it) model.setGlyphIcons(colours(), figureSize) else model.setGlyphIcons(null)
            }, enabled = connected)
            if (enabled) {
                RowDivider()
                ColourRow("Icon colour", glyph, { picking = Triple("Icon colour", glyph) { glyph = it } }, { glyph = defaults[0]; commit() }, enabled = connected)
                RowDivider()
                ColourRow("Background colour", background, { picking = Triple("Background colour", background) { background = it } },
                    { background = defaults[1]; commit() },
                    tip = "Opacity 0% on the background shows the glyph alone.", enabled = connected)
                RowDivider()
                SliderRow("Figure size", "$figureSize%", figureSize.toFloat(), GlyphIcons.MIN_SIZE.toFloat()..GlyphIcons.MAX_SIZE.toFloat(),
                    onChangeFinished = { figureSize = (it / 5).toInt() * 5; commit() },
                    onReset = if (figureSize != GlyphIcons.DEFAULT_SIZE) ({ figureSize = GlyphIcons.DEFAULT_SIZE; commit() }) else null,
                    tip = "How big the figure is inside its plate. Not tried on a car yet: above 100% parts of some figures may be cut off at the plate's edge.",
                    enabled = connected)
            }
        }

        PerAppIconsCard(model, connected, resetSignal)

        SettingsSection("Launcher") {
            SwitchRow("Show app names", LauncherView.namesShown(hideNames), {
                hideNames = LauncherView.hideNamesFor(it)
                model.setLauncherConfig(LauncherTweaks.encode(LauncherTweaks.Config(hideNames)))
            }, tip = "Hides the app names under the launcher icons when off. The names are hidden, not removed. Not tried on a car yet: " +
                "the rows of icons may move closer together or keep an empty gap where the names were.", enabled = connected)
        }
    }
}
