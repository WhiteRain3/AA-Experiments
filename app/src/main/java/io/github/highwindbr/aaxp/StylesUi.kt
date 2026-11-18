package io.github.highwindbr.aaxp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.Themes

/** Ready-made looks. One tap sets colours, extra colours, icons, status bar and wallpaper darken and blur together. */
@Composable
internal fun StylesCard(model: ManagerViewModel, connected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("One-tap styles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Each style sets everything together. The status bar and More colours parts are not yet confirmed on the car. " +
            "You can undo from Home.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Themes.BUILT_IN.forEach { look ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CarScreenMock(look.roles, look.glyphColors, Modifier.weight(1.3f), showCaption = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(look.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(look.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button({ model.applyLook(look) }, enabled = connected) { Text("Apply") }
                    }
                }
            }
        }
    }
}
