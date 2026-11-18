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
