package io.github.highwindbr.aaxp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.DynamicOverrideCodec
import io.github.highwindbr.aaxp.core.FlagSwitch
import io.github.highwindbr.aaxp.core.FlagSwitches
import io.github.highwindbr.aaxp.core.TypedOverride

/** One switch row: title, an honest note, and the switch itself. */
@Composable
private fun FlagSwitchRow(model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean, switch: FlagSwitch, note: String) {
    val missing by produceState<Set<String>?>(null, switch, connected) { value = model.flagSwitchMissing(switch) }
    val on = FlagSwitches.isOn(switch, overrides.mapValues { DynamicOverrideCodec.serialize(it.value) })
    val unavailable = missing?.isNotEmpty() == true
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(switch.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Switch(on, { model.setFlagSwitch(switch, it) }, enabled = connected && (on || !unavailable))
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (unavailable) Text("Not in this Android Auto version: " + missing!!.joinToString(", "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Switches that change several flags at once. They use the normal flag overrides, so they also show on the Flags page. */
@Composable
internal fun QuickSwitchesCard(model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlagSwitchRow(model, overrides, connected, FlagSwitches.PARKED_APPS,
                "Asks Android Auto to show phone apps and allow video apps while parked. Needs an Android Auto restart. " +
                    "Not known whether Android Auto 18.0 honours this, or whether your car reports \"parked\" through the wireless adapter.")
            FlagSwitchRow(model, overrides, connected, FlagSwitches.SHARP_PICTURE,
                "Asks the wireless video for a sharper picture. It may use more Wi-Fi bandwidth and may stutter. " +
                    "The best values for your adapter are not known. Turning it off puts your earlier values back. Needs an Android Auto restart.")
        }
    }
}

/** Width of the border around the navigation window, through the normal flag override path. */
@Composable
internal fun MapBorderCard(model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean) {
    val status by produceState(io.github.highwindbr.aaxp.core.MapBorderFlag.Status.UNKNOWN, connected) { value = model.mapBorderStatus() }
    val current = io.github.highwindbr.aaxp.core.MapBorderFlag.current(overrides.mapValues { DynamicOverrideCodec.serialize(it.value) })
    var width by remember(current) { mutableStateOf((current ?: 0).toFloat()) }
    val usable = status == io.github.highwindbr.aaxp.core.MapBorderFlag.Status.OK
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Map border width (experimental)", fontWeight = FontWeight.Bold)
            Text("A border around the navigation window. Not tried on a car yet, and the unit is not known (it may not be dp). Needs an Android Auto restart.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Now: " + io.github.highwindbr.aaxp.core.MapBorderFlag.label(current), style = MaterialTheme.typography.bodyMedium)
            when (status) {
                io.github.highwindbr.aaxp.core.MapBorderFlag.Status.MISSING -> Text("This Android Auto version does not have the setting.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                io.github.highwindbr.aaxp.core.MapBorderFlag.Status.NOT_INTEGER -> Text("The setting is not a whole number in this Android Auto version, so it cannot be changed here.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                io.github.highwindbr.aaxp.core.MapBorderFlag.Status.UNKNOWN -> Text("Checking the installed Android Auto…", style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            if (usable) {
                Text("Width: ${width.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(width, { width = it }, valueRange = io.github.highwindbr.aaxp.core.MapBorderFlag.MIN.toFloat()..io.github.highwindbr.aaxp.core.MapBorderFlag.MAX.toFloat(),
                    steps = io.github.highwindbr.aaxp.core.MapBorderFlag.MAX - 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ model.setMapBorderWidth(width.toInt()) }, Modifier.weight(1f), enabled = connected) { Text("Apply") }
                    OutlinedButton({ model.setMapBorderWidth(null) }, Modifier.weight(1f), enabled = connected && current != null) { Text("Android Auto's own") }
                }
            }
        }
    }
}
