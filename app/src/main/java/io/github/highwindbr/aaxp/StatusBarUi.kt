package io.github.highwindbr.aaxp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.highwindbr.aaxp.core.StatusBarView
import io.github.highwindbr.aaxp.runtime.StatusBarTweaks

internal val STATUS_BAR_PARTS = listOf(
    "clock" to "Clock", "signal_icon" to "Signal", "battery_level" to "Battery",
    "dnd_icon" to "Do not disturb icon", "notification_count_wrapper" to "Notification count")

internal const val STATUS_BAR_PAGE_TIP = "Hide, resize or recolour the clock, signal and battery. Not tried on a real car yet: " +
    "Android Auto may repaint the colours when it switches between day and night."

/** A section whose rows can be folded away; the heading shows a chevron. */
@Composable
internal fun CollapsibleSection(title: String, tip: String? = null, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            if (tip != null) TipIcon(title, tip)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Fold $title" else "Open $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

/** A mock bar that follows the settings: clock, signal and battery on the background. */
@Composable
private fun StatusBarMock(
    hidden: Set<String>, clockColor: Int?, iconColor: Int?, noPlate: Boolean, plateColor: Int?, plateRadius: Int?,
    clockSize: Int, clockBold: Boolean, batteryPercent: Boolean, batteryColor: Int?, iconSize: Int
) {
    val clock = Color(clockColor ?: 0xFFF2F0F0.toInt())
    val icons = Color(iconColor ?: 0xFFF2F0F0.toInt())
    val plate = if (noPlate) Color.Transparent else Color(plateColor ?: 0xFF303030.toInt())
    val corner = (plateRadius ?: 12).dp
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF121212)).padding(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(corner)).background(plate).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (StatusBarView.isShown(hidden, "clock")) Text("12:34", color = clock, fontSize = (14 * clockSize / 100f).sp,
                fontWeight = if (clockBold) FontWeight.Bold else FontWeight.Normal)
            Box(Modifier.weight(1f))
            val iconDp = (14 * iconSize / 100f).dp
            if (StatusBarView.isShown(hidden, "signal_icon")) Box(Modifier.size(iconDp).background(icons, RoundedCornerShape(2.dp)))
            if (StatusBarView.isShown(hidden, "battery_level")) {
                if (batteryPercent) Text("74%", color = Color(batteryColor ?: clockColor ?: 0xFFF2F0F0.toInt()), fontSize = 11.sp)
                Box(Modifier.size(iconDp * 1.6f, iconDp).background(icons, RoundedCornerShape(3.dp)))
            }
            if (StatusBarView.isShown(hidden, "dnd_icon")) Box(Modifier.size(iconDp).background(icons, CircleShape))
        }
    }
}

/**
 * The Status bar page. Parts are shown with switches (the stored setting is the opposite, a hide
 * list); everything applies as soon as it is changed, and sliders when released.
 */
@Composable
internal fun StatusBarPage(model: ManagerViewModel, connected: Boolean, resetSignal: Int) {
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
    var picking by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }

    // Writes the whole page's state; called after a switch, a slider release or a colour pick.
    fun commit() {
        val base = (stored ?: StatusBarTweaks.Config(LinkedHashSet<String>(), null, null, false)).toBuilder()
        // Keep the order stable so the stored text does not change needlessly.
        base.hide = LinkedHashSet(StatusBarTweaks.HIDEABLE.filter { it in hidden })
        base.clockColor = clockColor; base.iconColor = iconColor; base.noPlate = noPlate
        base.batteryPercent = batteryPercent; base.batteryColor = batteryColor; base.batterySize = batterySize
        base.clockSize = clockSize; base.clockBold = clockBold
        base.plateColor = plateColor; base.plateRadius = plateRadius; base.iconSize = iconSize
        base.batteryByLevel = byLevel; base.batteryHigh = levelHigh; base.batteryLow = levelLow
        base.batteryHighColor = highColor; base.batteryLowColor = lowColor
        base.batteryMidColor = midColor; base.batteryChargeColor = chargeColor
        model.setStatusBarConfig(StatusBarTweaks.encode(base.build()))
    }
    // The page menu's reset has already written the default; this only brings the rows back to it.
    LaunchedEffect(resetSignal) {
        if (resetSignal != 0) {
            hidden = emptySet(); clockColor = null; iconColor = null; noPlate = false; batteryPercent = false
            batteryColor = null; batterySize = StatusBarTweaks.DEFAULT_BATTERY_SIZE
            clockSize = StatusBarTweaks.DEFAULT_CLOCK_SIZE; clockBold = false
            plateColor = null; plateRadius = null; iconSize = StatusBarTweaks.DEFAULT_ICON_SIZE
            byLevel = false; levelHigh = StatusBarTweaks.DEFAULT_BATTERY_HIGH; levelLow = StatusBarTweaks.DEFAULT_BATTERY_LOW
            highColor = StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR; lowColor = StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR
            midColor = null; chargeColor = null
        }
    }
    picking?.let { (title, start, apply) ->
        ColorPickerDialog(title, start, onDismiss = { picking = null }) { picked -> apply(picked); picking = null; commit() }
    }
    fun pick(title: String, current: Int?, apply: (Int) -> Unit) { picking = Triple(title, current ?: 0xFFFFFFFF.toInt(), apply) }

    Column {
        PreviewCard {
            StatusBarMock(hidden, clockColor, iconColor, noPlate, plateColor, plateRadius, clockSize, clockBold, batteryPercent, batteryColor, iconSize)
        }

        SettingsSection("Show") {
            STATUS_BAR_PARTS.forEachIndexed { index, (id, label) ->
                if (index > 0) RowDivider()
                SwitchRow(label, StatusBarView.isShown(hidden, id), { hidden = StatusBarView.withShown(hidden, id, it); commit() }, enabled = connected)
            }
        }

        CollapsibleSection("Clock") {
            SliderRow("Size", "$clockSize%", clockSize.toFloat(), StatusBarTweaks.MIN_CLOCK_SIZE.toFloat()..StatusBarTweaks.MAX_CLOCK_SIZE.toFloat(),
                onChangeFinished = { clockSize = (it / 5).toInt() * 5; commit() },
                onReset = if (clockSize != StatusBarTweaks.DEFAULT_CLOCK_SIZE) ({ clockSize = StatusBarTweaks.DEFAULT_CLOCK_SIZE; commit() }) else null,
                enabled = connected)
            RowDivider()
            SwitchRow("Bold", clockBold, { clockBold = it; commit() }, enabled = connected)
            RowDivider()
            ColourRow("Colour", clockColor, { pick("Clock colour", clockColor) { clockColor = it } }, { clockColor = null; commit() },
                tip = "The clock's colour. The Colours page also has \"Labels and rail icons\", which touches the clock too; which of the two wins is not known yet.",
                enabled = connected)
        }

        CollapsibleSection("Battery", tip = "The battery number is the one status bar change that updates while Android Auto is running.") {
            SwitchRow("Show as a percentage", batteryPercent, { batteryPercent = it; commit() }, enabled = connected)
            if (batteryPercent) {
                RowDivider()
                SliderRow("Number size", "$batterySize% of the clock", batterySize.toFloat(),
                    StatusBarTweaks.MIN_BATTERY_SIZE.toFloat()..StatusBarTweaks.MAX_BATTERY_SIZE.toFloat(),
                    onChangeFinished = { batterySize = (it / 5).toInt() * 5; commit() },
                    onReset = if (batterySize != StatusBarTweaks.DEFAULT_BATTERY_SIZE) ({ batterySize = StatusBarTweaks.DEFAULT_BATTERY_SIZE; commit() }) else null,
                    enabled = connected)
                RowDivider()
                ColourRow("Number colour", batteryColor, { pick("Number colour", batteryColor) { batteryColor = it } }, { batteryColor = null; commit() },
                    tip = "Default follows the clock's colour.", enabled = connected)
                RowDivider()
                SwitchRow("Colour by level", byLevel, { byLevel = it; commit() }, enabled = connected)
                if (byLevel) {
                    RowDivider()
                    SliderRow("High from", "$levelHigh%", levelHigh.toFloat(), 10f..100f,
                        onChangeFinished = { levelHigh = ((it / 5).toInt() * 5).coerceAtLeast(levelLow + 5); commit() },
                        onReset = if (levelHigh != StatusBarTweaks.DEFAULT_BATTERY_HIGH) ({ levelHigh = StatusBarTweaks.DEFAULT_BATTERY_HIGH; commit() }) else null,
                        enabled = connected)
                    RowDivider()
                    ColourRow("High", highColor, { pick("High level colour", highColor) { highColor = it } },
                        { highColor = StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR; commit() }, enabled = connected)
                    RowDivider()
                    ColourRow("In between", midColor, { pick("In-between colour", midColor) { midColor = it } }, { midColor = null; commit() },
                        tip = "Default uses the number colour.", enabled = connected)
                    RowDivider()
                    SliderRow("Low below", "$levelLow%", levelLow.toFloat(), 0f..90f,
                        onChangeFinished = { levelLow = ((it / 5).toInt() * 5).coerceAtMost(levelHigh - 5); commit() },
                        onReset = if (levelLow != StatusBarTweaks.DEFAULT_BATTERY_LOW) ({ levelLow = StatusBarTweaks.DEFAULT_BATTERY_LOW; commit() }) else null,
                        enabled = connected)
                    RowDivider()
                    ColourRow("Low", lowColor, { pick("Low level colour", lowColor) { lowColor = it } },
                        { lowColor = StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR; commit() }, enabled = connected)
                    RowDivider()
                    ColourRow("Charging", chargeColor, { pick("Charging colour", chargeColor) { chargeColor = it } }, { chargeColor = null; commit() },
                        enabled = connected)
                }
            }
        }

        CollapsibleSection("Icons", tip = "The signal and battery icons. Not known whether a larger row is cut off by the background or touches the clock.") {
            SliderRow("Size", "$iconSize%", iconSize.toFloat(), StatusBarTweaks.MIN_ICON_SIZE.toFloat()..StatusBarTweaks.MAX_ICON_SIZE.toFloat(),
                onChangeFinished = { iconSize = (it / 5).toInt() * 5; commit() },
                onReset = if (iconSize != StatusBarTweaks.DEFAULT_ICON_SIZE) ({ iconSize = StatusBarTweaks.DEFAULT_ICON_SIZE; commit() }) else null,
                enabled = connected)
            RowDivider()
            ColourRow("Colour", iconColor, { pick("Icon colour", iconColor) { iconColor = it } }, { iconColor = null; commit() }, enabled = connected)
        }

        CollapsibleSection("Background") {
            SwitchRow("Show background", StatusBarView.backgroundShown(noPlate), { noPlate = StatusBarView.noPlateFor(it); commit() }, enabled = connected)
            if (StatusBarView.backgroundShown(noPlate)) {
                RowDivider()
                ColourRow("Colour", plateColor, { pick("Background colour", plateColor) { plateColor = it } },
                    { plateColor = null; plateRadius = null; commit() },
                    tip = "Lower the opacity in the picker for a see-through background.", enabled = connected)
                RowDivider()
                SliderRow("Corner roundness", plateRadius?.toString() ?: "Default", (plateRadius ?: 0).toFloat(), 0f..StatusBarTweaks.MAX_PLATE_RADIUS.toFloat(),
                    onChangeFinished = { plateRadius = (it / 2).toInt() * 2; commit() },
                    onReset = if (plateRadius != null) ({ plateRadius = null; commit() }) else null,
                    tip = "0 is square. Not tried on a car yet: Android Auto may still draw its own outline in the old shape on top.",
                    enabled = connected && plateColor != null)
                if (plateColor == null) Text("Pick a colour first", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
