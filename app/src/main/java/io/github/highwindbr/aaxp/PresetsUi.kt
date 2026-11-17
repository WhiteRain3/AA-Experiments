package io.github.highwindbr.aaxp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.Preset
import io.github.highwindbr.aaxp.core.PresetDiff
import io.github.highwindbr.aaxp.core.Presets
import io.github.highwindbr.aaxp.core.TypedOverride
import io.github.highwindbr.aaxp.discovery.SafetyPolicy

private const val MAX_PRESET_FILE_BYTES = 1024 * 1024

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri).use { input ->
        val bytes = checkNotNull(input) { "Cannot read the file" }.readBytes()
        require(bytes.size <= MAX_PRESET_FILE_BYTES) { "File is too large to be a presets file" }
        String(bytes, Charsets.UTF_8)
    }

/** Named sets of normal overrides: save the current set, switch between sets, export and import. */
@Composable
internal fun PresetsCard(model: ManagerViewModel, current: Map<String, TypedOverride>, connected: Boolean) {
    val context = LocalContext.current
    // Re-read whenever the overrides change or an action bumps the counter.
    var revision by remember { mutableStateOf(0) }
    val presets = remember(revision, current) { model.presets() }
    var naming by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { out ->
                checkNotNull(out) { "Cannot write the file" }.write(model.exportPresetsJson().toByteArray(Charsets.UTF_8))
            }
        }.fold({ model.reportMessage("Presets exported.") }, { model.reportMessage("Presets not exported: ${it.message}") })
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { readText(context, uri) }
                .fold({ model.importPresetsJson(it) }, { model.reportMessage("Presets not imported: ${it.message}") })
            revision++
        }
    }

    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presets", fontWeight = FontWeight.Bold)
        Text("Save the current normal overrides under a name and switch between sets. Applying a preset replaces the current set; experiments it does not mention go back to default.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (presets.isEmpty()) Text("No presets yet.", style = MaterialTheme.typography.bodyMedium)
        presets.forEach { preset ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(preset.name, fontWeight = FontWeight.SemiBold)
                    val pending = model.presetDiff(preset).size
                    Text("${preset.overrides.size} override(s) · " + (if (pending == 0) "active now" else "$pending change(s) if applied"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button({ applying = preset }, enabled = connected) { Text("Apply") }
                TextButton({ deleting = preset }) { Text("Delete") }
            }
        }
        HorizontalDivider()
        Button({ naming = true }, Modifier.fillMaxWidth()) { Text("Save current overrides as preset") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ exporter.launch("aa-experiments-presets.json") }, Modifier.weight(1f), enabled = presets.isNotEmpty()) { Text("Export") }
            OutlinedButton({ importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, Modifier.weight(1f)) { Text("Import") }
        }
    } }

    if (naming) {
        var name by remember { mutableStateOf("") }
        val clean = Presets.cleanName(name)
        val replaces = clean != null && presets.any { it.name.equals(clean, true) }
        AlertDialog(onDismissRequest = { naming = false },
            title = { Text("Save preset") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${current.size} override(s) will be saved.")
                OutlinedTextField(name, { name = it.take(Presets.MAX_NAME_LENGTH) }, label = { Text("Name") }, singleLine = true)
                if (replaces) Text("A preset with this name exists and will be replaced.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton({ if (model.savePreset(name)) { naming = false; revision++ } }, enabled = clean != null) { Text("Save") } },
            dismissButton = { TextButton({ naming = false }) { Text("Cancel") } })
    }

    applying?.let { preset ->
        val diff = remember(preset, current) { model.presetDiff(preset) }
        AlertDialog(onDismissRequest = { applying = null },
            title = { Text("Apply \"${preset.name}\"?") },
            text = { PresetDiffList(diff) },
            confirmButton = { TextButton({ model.applyPreset(preset); applying = null; revision++ }, enabled = !diff.isEmpty) { Text("Apply ${diff.size} change(s)") } },
            dismissButton = { TextButton({ applying = null }) { Text("Cancel") } })
    }

    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Delete \"${preset.name}\"?") },
            text = { Text("The preset is removed. Your current overrides are not changed.") },
            confirmButton = { TextButton({ model.deletePreset(preset.name); deleting = null; revision++ }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

/** Exactly what applying a preset will change, one line per experiment. */
@Composable
private fun PresetDiffList(diff: PresetDiff) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (diff.isEmpty) { Text("Nothing to change: the current overrides already match this preset."); return@Column }
        @Composable fun line(key: String, detail: String) {
            Column {
                Text(key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
                if (SafetyPolicy.elevatedRiskReason(key) != null)
                    Text("Elevated parked-mode risk. Apply only while parked.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        diff.added.forEach { (key, value) -> line(key, "Set to ${Presets.displayValue(value)}") }
        diff.changed.forEach { (key, values) -> line(key, "${Presets.displayValue(values.first)}  →  ${Presets.displayValue(values.second)}") }
        diff.removed.forEach { (key, value) -> line(key, "Back to default (was ${Presets.displayValue(value)})") }
        Text("Restart Android Auto afterwards for the changes to show.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
