package io.github.highwindbr.aaxp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.highwindbr.aaxp.core.CarScreen
import io.github.highwindbr.aaxp.core.RailSide
import io.github.highwindbr.aaxp.core.WallpaperCanvas
import io.github.highwindbr.aaxp.core.WallpaperCoverage
import io.github.highwindbr.aaxp.core.WallpaperGeometry
import io.github.highwindbr.aaxp.core.WallpaperPlacement
import io.github.highwindbr.aaxp.runtime.CarWallpaper
import java.io.File

/** Loading, remembering and rendering the car wallpaper being edited. */
internal object CarWallpaperStudio {
    private const val PREFS = "car_wallpaper_editor"
    private const val SOURCE = "car_wallpaper_source"

    fun sourceFile(context: Context): File = File(context.filesDir, SOURCE)

    /** Keeps a private copy of the picked image so it can be edited again later. */
    fun importSource(context: Context, uri: Uri) {
        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Image could not be opened" }
            sourceFile(context).outputStream().use { input.copyTo(it) }
        }
    }

    /** Decodes the kept copy at a size that is comfortable to edit and still sharp when stored. */
    fun loadSource(context: Context): Bitmap? {
        val file = sourceFile(context)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = CarWallpaper.sampleSize(bounds.outWidth, bounds.outHeight, CarWallpaper.MAX_EDGE)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    data class Saved(val screen: CarScreen, val coverage: WallpaperCoverage, val placement: WallpaperPlacement,
                     val separatorThickness: Int = 0, val separatorColor: String = "#FFFFFF")

    fun load(context: Context): Saved {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Saved(
            CarScreen(p.getInt("width", 800), p.getInt("height", 480),
                runCatching { RailSide.valueOf(p.getString("rail", null) ?: "LEFT") }.getOrDefault(RailSide.LEFT),
                p.getInt("rail_size", 72)),
            runCatching { WallpaperCoverage.valueOf(p.getString("coverage", null) ?: "WHOLE_SCREEN") }
                .getOrDefault(WallpaperCoverage.WHOLE_SCREEN),
            WallpaperPlacement(p.getFloat("zoom", 1f), p.getFloat("offset_x", 0f), p.getFloat("offset_y", 0f),
                p.getInt("turns", 0)),
            p.getInt("separator_thickness", 0), p.getString("separator_color", null) ?: "#FFFFFF")
    }

    fun save(context: Context, saved: Saved) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("width", saved.screen.width).putInt("height", saved.screen.height)
            .putString("rail", saved.screen.rail.name).putInt("rail_size", saved.screen.railSize)
            .putString("coverage", saved.coverage.name)
            .putFloat("zoom", saved.placement.zoom).putFloat("offset_x", saved.placement.offsetX)
            .putFloat("offset_y", saved.placement.offsetY).putInt("turns", saved.placement.quarterTurns)
            .putInt("separator_thickness", saved.separatorThickness).putString("separator_color", saved.separatorColor)
            .apply()
    }

    /**
     * Matrix placing [source] in a frame of [frameWidth] x [frameHeight] pixels: centred, rotated
     * in quarter turns, scaled to cover the frame times the zoom, then moved by the offset.
     */
    fun matrix(source: Bitmap, frameWidth: Float, frameHeight: Float, placement: WallpaperPlacement,
               unitsPerFramePixel: Float): Matrix {
        val (rw, rh) = WallpaperGeometry.rotatedSize(source.width, source.height, placement.quarterTurns)
        // "Cover" scale for a frame measured in arbitrary units (preview pixels or stored pixels).
        val base = maxOf(frameWidth / rw, frameHeight / rh)
        val scale = base * placement.zoom
        return Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(90f * placement.quarterTurns)
            postScale(scale, scale)
            postTranslate(frameWidth / 2f + placement.offsetX * unitsPerFramePixel,
                frameHeight / 2f + placement.offsetY * unitsPerFramePixel)
        }
    }

    /** Renders the image Android Auto will receive. [separator] is in frame pixels. */
    fun render(source: Bitmap, canvasSpec: WallpaperCanvas, placement: WallpaperPlacement,
               separator: io.github.highwindbr.aaxp.core.FrameRect? = null, separatorColor: Int = 0): Bitmap {
        val k = WallpaperGeometry.outputScale(canvasSpec, CarWallpaper.MAX_EDGE)
        val output = Bitmap.createBitmap((canvasSpec.width * k).toInt().coerceAtLeast(1),
            (canvasSpec.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.translate(canvasSpec.frameLeft * k, canvasSpec.frameTop * k)
        canvas.drawBitmap(source,
            matrix(source, canvasSpec.frameWidth * k, canvasSpec.frameHeight * k, placement, k),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (separator != null) {
            canvas.drawRect(separator.left * k, separator.top * k, separator.right * k, separator.bottom * k,
                Paint().apply { color = separatorColor })
        }
        return output
    }
}

/**
 * Full-screen editor: drag to move, pinch or use the slider to zoom, rotate, and choose whether
 * the image covers the whole car screen or only the area beside the rail. The preview is the car
 * screen; the shaded strip is the rail.
 */
@Composable
internal fun CarWallpaperEditor(source: Bitmap, onCancel: () -> Unit, onSave: (Bitmap) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initial = remember { CarWallpaperStudio.load(context) }
    var widthText by remember { mutableStateOf(initial.screen.width.toString()) }
    var heightText by remember { mutableStateOf(initial.screen.height.toString()) }
    var railSizeText by remember { mutableStateOf(initial.screen.railSize.toString()) }
    var rail by remember { mutableStateOf(initial.screen.rail) }
    var coverage by remember { mutableStateOf(initial.coverage) }
    var placement by remember { mutableStateOf(initial.placement) }
    var saving by remember { mutableStateOf(false) }
    var separatorThickness by remember { mutableStateOf(initial.separatorThickness) }
    var separatorColorText by remember { mutableStateOf(initial.separatorColor) }

    val screen = CarScreen(
        widthText.toIntOrNull()?.coerceIn(200, 8000) ?: 800,
        heightText.toIntOrNull()?.coerceIn(200, 8000) ?: 480,
        rail, railSizeText.toIntOrNull()?.coerceIn(0, 2000) ?: 72)
    val spec = WallpaperGeometry.canvas(screen, coverage)
    val separator = WallpaperGeometry.separator(screen, coverage, separatorThickness)
    val separatorColor = WallpaperGeometry.parseColor(separatorColorText)
    val (rotatedWidth, rotatedHeight) = WallpaperGeometry.rotatedSize(source.width, source.height, placement.quarterTurns)
    // Zoom at which the whole image is visible, relative to the "cover" zoom of 1.
    val fitZoom = WallpaperGeometry.fitScale(rotatedWidth, rotatedHeight, spec.frameWidth, spec.frameHeight) /
        WallpaperGeometry.coverScale(rotatedWidth, rotatedHeight, spec.frameWidth, spec.frameHeight)

    val saveNow = {
        saving = true
        CarWallpaperStudio.save(context, CarWallpaperStudio.Saved(screen, coverage, placement,
            separatorThickness, separatorColorText))
        onSave(CarWallpaperStudio.render(source, spec, placement,
            if (separatorColor != null) separator else null, separatorColor ?: 0))
    }
    Dialog(onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
          // Keep clear of the status bar, navigation bar and keyboard, and keep Save always in reach.
          Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Car wallpaper", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onCancel, enabled = !saving) { Text("Cancel") }
                Button(saveNow, enabled = !saving) { Text(if (saving) "Saving…" else "Save") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Drag to move, pinch to zoom. The preview is the car screen; the shaded strip is the rail.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
                Canvas(Modifier.fillMaxWidth()
                    .aspectRatio(spec.frameWidth.toFloat() / spec.frameHeight)
                    .clipToBounds()
                    .pointerInput(spec.frameWidth, spec.frameHeight) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val perPixel = size.width.toFloat() / spec.frameWidth
                            placement = placement.copy(
                                zoom = (placement.zoom * zoom).coerceIn(0.1f, 8f),
                                offsetX = placement.offsetX + pan.x / perPixel,
                                offsetY = placement.offsetY + pan.y / perPixel)
                        }
                    }) {
                    val perPixel = size.width / spec.frameWidth
                    drawRect(Color.Black)
                    drawIntoCanvas {
                        it.nativeCanvas.drawBitmap(source,
                            CarWallpaperStudio.matrix(source, size.width, size.height, placement, perPixel), paint)
                    }
                    if (coverage == WallpaperCoverage.WHOLE_SCREEN && rail != RailSide.NONE) {
                        val strip = screen.railSize * perPixel
                        val (topLeft, stripSize) = when (rail) {
                            RailSide.LEFT -> Offset(0f, 0f) to Size(strip, size.height)
                            RailSide.RIGHT -> Offset(size.width - strip, 0f) to Size(strip, size.height)
                            else -> Offset(0f, size.height - strip) to Size(size.width, strip)
                        }
                        drawRect(Color.Black.copy(alpha = 0.45f), topLeft, stripSize)
                        drawRect(Color.White.copy(alpha = 0.7f), topLeft, stripSize, style = Stroke(1.dp.toPx()))
                    }
                    if (separator != null && separatorColor != null) {
                        drawRect(Color(separatorColor), Offset(separator.left * perPixel, separator.top * perPixel),
                            Size((separator.right - separator.left) * perPixel, (separator.bottom - separator.top) * perPixel))
                    }
                    drawRect(Color.White.copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))
                }

                Text("Zoom ${"%.2f".format(placement.zoom)}x", style = MaterialTheme.typography.labelLarge)
                Slider(placement.zoom, { placement = placement.copy(zoom = it) }, valueRange = 0.1f..4f)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ placement = placement.copy(zoom = 1f, offsetX = 0f, offsetY = 0f) }, Modifier.weight(1f)) { Text("Fill") }
                    OutlinedButton({ placement = placement.copy(zoom = fitZoom.coerceIn(0.1f, 8f), offsetX = 0f, offsetY = 0f) }, Modifier.weight(1f)) { Text("Fit") }
                    OutlinedButton({ placement = placement.copy(offsetX = 0f, offsetY = 0f) }, Modifier.weight(1f)) { Text("Centre") }
                    OutlinedButton({ placement = placement.copy(quarterTurns = (placement.quarterTurns + 1) % 4) }, Modifier.weight(1f)) { Text("Rotate") }
                }

                Text("Cover", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(coverage == WallpaperCoverage.WHOLE_SCREEN, { coverage = WallpaperCoverage.WHOLE_SCREEN },
                        { Text("Whole screen, under the rail") })
                    FilterChip(coverage == WallpaperCoverage.MAIN_AREA, { coverage = WallpaperCoverage.MAIN_AREA },
                        { Text("Main area only") })
                }

                Text(if (separatorThickness == 0) "Rail separation: off" else "Rail separation: $separatorThickness px",
                    style = MaterialTheme.typography.labelLarge)
                Text("A line where the main area meets the rail, on whichever side the rail is.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(separatorThickness.toFloat(), { separatorThickness = it.toInt() }, valueRange = 0f..24f, steps = 23,
                    enabled = rail != RailSide.NONE)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("White" to "#FFFFFF", "Black" to "#000000", "Grey" to "#808080", "Red" to "#D7000F").forEach { (name, hex) ->
                        FilterChip(separatorColorText.equals(hex, true), { separatorColorText = hex }, { Text(name) })
                    }
                }
                OutlinedTextField(separatorColorText, { separatorColorText = it.take(9) }, Modifier.fillMaxWidth(),
                    label = { Text("Line colour (#RRGGBB or #AARRGGBB)") }, singleLine = true,
                    isError = separatorColor == null,
                    supportingText = if (separatorColor == null) ({ Text("Not a colour; the line will not be drawn") }) else null)

                Text("Car screen", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(widthText, { widthText = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                        label = { Text("Width px") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(heightText, { heightText = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                        label = { Text("Height px") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(railSizeText, { railSizeText = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                        label = { Text("Rail px") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RailSide.values().forEach { side ->
                        FilterChip(rail == side, { rail = side },
                            { Text("Rail " + side.name.lowercase().replace("none", "hidden")) })
                    }
                }
                Text("Defaults match the 800x480 DHU profile with the rail on the left (72 px). Set your car's values if they differ.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Generous clearance: some devices do not report bar and keyboard insets to dialogs,
                // and without it the last controls stay under the navigation bar or keyboard.
                Spacer(Modifier.height(320.dp))
            }
          }
        }
    }
}
