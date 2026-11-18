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

/** One switch row: the title, with the honest note behind a "?"; a red line only for live state. */
@Composable
private fun FlagSwitchRow(model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean, switch: FlagSwitch, note: String) {
    val missing by produceState<Set<String>?>(null, switch, connected) { value = model.flagSwitchMissing(switch) }
    val on = FlagSwitches.isOn(switch, overrides.mapValues { DynamicOverrideCodec.serialize(it.value) })
    val unavailable = missing?.isNotEmpty() == true
    SwitchRow(switch.title, on, { model.setFlagSwitch(switch, it) }, tip = note, enabled = connected && (on || !unavailable))
    if (unavailable) Text("Not in this Android Auto version: " + missing!!.joinToString(", "), Modifier.padding(bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** Switches that change several flags at once. They use the normal flag overrides, so they also show on the Flags page. */
@Composable
internal fun QuickSwitchesCard(model: ManagerViewModel, overrides: Map<String, TypedOverride>, connected: Boolean) {
    SettingsSection("Quick switches", tip = "These use the normal flag overrides, so they also show in Flags.") {
        FlagSwitchRow(model, overrides, connected, FlagSwitches.PARKED_APPS,
            "Asks Android Auto to show phone apps and allow video apps while parked. Needs an Android Auto restart. " +
                "Not known whether Android Auto 18.0 honours this, or whether your car reports \"parked\" through the wireless adapter.")
        RowDivider()
        FlagSwitchRow(model, overrides, connected, FlagSwitches.SHARP_PICTURE,
            "Asks the wireless video for a sharper picture. It may use more Wi-Fi bandwidth and may stutter. " +
                "The best values for your adapter are not known. Turning it off puts your earlier values back. Needs an Android Auto restart.")
    }
}
