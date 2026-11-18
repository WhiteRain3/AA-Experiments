package io.github.highwindbr.aaxp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.RailSide
import io.github.highwindbr.aaxp.core.WallpaperCoverage
import io.github.highwindbr.aaxp.runtime.CarWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The thumbnail of the saved wallpaper laid out like the car screen, with the rail strip shaded. */
@Composable
private fun WallpaperThumbnail(bitmap: android.graphics.Bitmap, settings: CarWallpaperStudio.Saved) {
    val screen = settings.screen
    val vertical = screen.rail == RailSide.LEFT || screen.rail == RailSide.RIGHT
    val fraction = if (screen.rail == RailSide.NONE) 0f else
        (screen.railSize.toFloat() / (if (vertical) screen.width else screen.height)).coerceIn(0f, 0.5f)
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(screen.width.toFloat() / screen.height).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        val image = bitmap.asImageBitmap()
        if (settings.coverage == WallpaperCoverage.WHOLE_SCREEN) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            // The picture covers the main area only; the rail's place is left dark.
            val padding = androidx.compose.foundation.layout.PaddingValues(
                start = if (screen.rail == RailSide.LEFT) maxWidth * fraction else 0.dp,
                end = if (screen.rail == RailSide.RIGHT) maxWidth * fraction else 0.dp,
                bottom = if (screen.rail == RailSide.BOTTOM) maxHeight * fraction else 0.dp)
            Image(image, null, Modifier.fillMaxSize().padding(padding), contentScale = ContentScale.Crop)
        }
        if (fraction > 0f && settings.coverage == WallpaperCoverage.WHOLE_SCREEN) {
            val shade = Modifier.background(Color.Black.copy(alpha = 0.45f))
            when (screen.rail) {
                RailSide.LEFT -> Box(shade.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(fraction))
                RailSide.RIGHT -> Box(shade.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(fraction))
                else -> Box(shade.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction))
            }
        }
    }
}

/**
 * The Wallpaper page: the saved wallpaper (or an invitation to choose one) and the rail strip's
 * colour and strength. The rail strip applies as soon as it is changed; the picture itself is
 * edited in the wallpaper editor, which keeps its Cancel and Save.
 */
@Composable
internal fun WallpaperPage(model: ManagerViewModel, connected: Boolean, resetSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember(connected) { mutableStateOf(connected && model.carWallpaperEnabled()) }
    var hasSource by remember { mutableStateOf(CarWallpaperStudio.sourceFile(context).isFile) }
    var editing by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val thumbnail by produceState<Pair<android.graphics.Bitmap, CarWallpaperStudio.Saved>?>(null, enabled, refresh, resetSignal) {
        value = if (!enabled) null else withContext(Dispatchers.IO) {
            runCatching { CarWallpaperStudio.renderSaved(context)?.first?.let { it to CarWallpaperStudio.load(context) } }.getOrNull()
        }
    }

    val storedRail = remember(connected) { CarWallpaper.parseRailLook(if (connected) model.railLook() else null) }
    var railColor by remember(storedRail) { mutableStateOf(storedRail.color) }
    var strength by remember(storedRail) { mutableStateOf(storedRail.strength) }
    var pickingRail by remember { mutableStateOf(false) }
    fun commitRail() = model.setRailLook(CarWallpaper.encodeRailLook(CarWallpaper.RailLook(railColor, strength)))
    // The page menu's reset has already written the default; this only brings the rows back to it.
    LaunchedEffect(resetSignal) {
        if (resetSignal != 0) { enabled = false; railColor = null; strength = CarWallpaper.DEFAULT_RAIL_STRENGTH }
    }

    fun openEditor() {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { CarWallpaperStudio.loadSource(context) }.getOrNull() }
            if (bitmap == null) model.reportMessage("That image could not be read.") else editing = bitmap
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) { model.keepWallpaperImageForUndo(); runCatching { CarWallpaperStudio.importSource(context, uri) }.isSuccess }
            if (imported) { hasSource = true; openEditor() } else model.reportMessage("That image could not be opened.")
        }
    }
    editing?.let { source ->
        CarWallpaperEditor(source, onCancel = { editing = null }) { rendered, rail, settings ->
            model.storeCarWallpaper(rendered, rail, settings) { saved -> if (saved) { enabled = true; refresh++ }; editing = null }
        }
    }
    if (pickingRail) ColorPickerDialog("Strip colour", railColor ?: CarWallpaper.DEFAULT_RAIL_COLOR, onDismiss = { pickingRail = false }) {
        railColor = it; pickingRail = false; commitRail()
    }

    Column {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Car wallpaper", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            TipIcon("Car wallpaper", "Android Auto must have \"Use phone's wallpaper\" turned on in its settings; the module then hands it this image " +
                "instead of the phone's. Not yet tried on every car.")
        }
        PreviewCard {
            val shown = thumbnail
            if (enabled && shown != null) WallpaperThumbnail(shown.first, shown.second)
            else Box(Modifier.fillMaxWidth().aspectRatio(1200f / 723f).clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(enabled = connected) { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                Text(if (enabled) "Loading…" else "Choose an image", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (enabled) "Custom image" else "Not set", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasSource) Button({ openEditor() }, Modifier.weight(1f), enabled = connected) { Text("Edit") }
                OutlinedButton({ picker.launch("image/*") }, Modifier.weight(1f), enabled = connected) { Text(if (hasSource) "New image" else "Choose image") }
                TextButton({ model.clearCarWallpaper { cleared -> if (cleared) { enabled = false; refresh++ } } }, enabled = connected && enabled) { Text("Default") }
            }
        }

        SettingsSection("Rail strip", tip = "The strip with the clock and app shortcuts. Pick its background colour, and how strongly the wallpaper " +
            "shows over that colour when the wallpaper covers the whole screen. Android Auto's own launcher shows the wallpaper at 80% over dark grey, " +
            "which is the starting point here. A colour without a whole-screen wallpaper paints the strip in that colour. Not tried on a real car yet.") {
            ColourRow("Strip colour", railColor, { pickingRail = true }, { railColor = null; commitRail() }, enabled = connected)
            RowDivider()
            SliderRow("Wallpaper strength", "$strength%", strength.toFloat(), 0f..100f,
                onChangeFinished = { strength = (it / 5).toInt() * 5; commitRail() },
                onReset = if (strength != CarWallpaper.DEFAULT_RAIL_STRENGTH) ({ strength = CarWallpaper.DEFAULT_RAIL_STRENGTH; commitRail() }) else null,
                enabled = connected)
        }
    }
}
