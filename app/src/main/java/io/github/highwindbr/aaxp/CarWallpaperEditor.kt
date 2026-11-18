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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    fun loadSource(context: Context): Bitmap? = loadFrom(sourceFile(context))

    fun loadFrom(file: File): Bitmap? {
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
                     val separatorThickness: Int = 0, val separatorColor: String = "#FFFFFF",
                     val dim: Int = 0, val blurLevel: Int = 0, val railShiftX: Int = 0, val railShiftY: Int = 0)

    fun settingsJson(saved: Saved): org.json.JSONObject = org.json.JSONObject()
        .put("width", saved.screen.width).put("height", saved.screen.height)
        .put("rail", saved.screen.rail.name).put("railSize", saved.screen.railSize)
        .put("coverage", saved.coverage.name)
        .put("zoom", saved.placement.zoom.toDouble()).put("offsetX", saved.placement.offsetX.toDouble())
        .put("offsetY", saved.placement.offsetY.toDouble()).put("turns", saved.placement.quarterTurns)
        .put("separatorThickness", saved.separatorThickness).put("separatorColor", saved.separatorColor)
        .put("dim", saved.dim).put("blurFine", saved.blurLevel)
        .put("railShiftX", saved.railShiftX).put("railShiftY", saved.railShiftY)

    /** Editor settings from a look file; anything missing or out of range falls back to a default. */
    fun settingsFrom(json: org.json.JSONObject?): Saved {
        val j = json ?: org.json.JSONObject()
        return Saved(
            CarScreen(j.optInt("width", 800).coerceIn(200, 8000), j.optInt("height", 480).coerceIn(200, 8000),
                runCatching { RailSide.valueOf(j.optString("rail", "LEFT")) }.getOrDefault(RailSide.LEFT),
                j.optInt("railSize", 72).coerceIn(0, 2000)),
            runCatching { WallpaperCoverage.valueOf(j.optString("coverage", "WHOLE_SCREEN")) }.getOrDefault(WallpaperCoverage.WHOLE_SCREEN),
            WallpaperPlacement(j.optDouble("zoom", 1.0).toFloat().coerceIn(0.1f, 8f), j.optDouble("offsetX", 0.0).toFloat(),
                j.optDouble("offsetY", 0.0).toFloat(), j.optInt("turns", 0).mod(4)),
            j.optInt("separatorThickness", 0).coerceIn(0, 24), j.optString("separatorColor", "#FFFFFF"),
            j.optInt("dim", 0).coerceIn(0, 80),
            io.github.highwindbr.aaxp.core.BlurLevel.fromStored(
                if (j.has("blurFine")) j.optInt("blurFine") else null, if (j.has("blur")) j.optInt("blur") else null),
            j.optInt("railShiftX", 0).coerceIn(-20, 20), j.optInt("railShiftY", 0).coerceIn(-20, 20))
    }

    /** The part of a rendered whole-screen image that belongs behind the rail; null when there is none. */
    /**
     * [shiftX]/[shiftY] (screen pixels) move the window the slice is cut from, to line the rail up
     * with the main area on head units that place the two a few pixels apart.
     */
    fun railSlice(rendered: Bitmap, screen: CarScreen, coverage: WallpaperCoverage, shiftX: Int = 0, shiftY: Int = 0): Bitmap? {
        val rect = WallpaperGeometry.railRect(screen, coverage) ?: return null
        val k = rendered.width.toFloat() / WallpaperGeometry.canvas(screen, coverage).width
        val width = ((rect.right - rect.left) * k).toInt().coerceAtLeast(1)
        val height = ((rect.bottom - rect.top) * k).toInt().coerceAtLeast(1)
        // Drawn rather than cropped, so a shift past the image's edge leaves black instead of failing.
        val slice = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(slice).apply {
            drawColor(android.graphics.Color.BLACK)
            drawBitmap(rendered, -(rect.left + shiftX) * k, -(rect.top + shiftY) * k, null)
        }
        return slice
    }

    /** The image Android Auto receives for the stored source and settings, and the rail's slice of it. */
    fun renderSaved(context: Context): Pair<Bitmap, Bitmap?>? = renderFrom(sourceFile(context), load(context))

    /** The image Android Auto would receive for the image in [file] and [saved] settings, without touching what is stored. */
    fun renderFrom(file: File, saved: Saved): Pair<Bitmap, Bitmap?>? {
        val source = loadFrom(file) ?: return null
        val color = WallpaperGeometry.parseColor(saved.separatorColor)
        val rendered = render(softened(source, saved.blurLevel), WallpaperGeometry.canvas(saved.screen, saved.coverage), saved.placement,
            if (color != null) WallpaperGeometry.separator(saved.screen, saved.coverage, saved.separatorThickness) else null,
            color ?: 0, saved.dim)
        return rendered to railSlice(rendered, saved.screen, saved.coverage, saved.railShiftX, saved.railShiftY)
    }

    /**
     * [source] blurred by [blur] steps (0 = unchanged), at the same size. The radius grows with the
     * image, so a level looks the same on any size. Large radii are blurred at a reduced size and
     * scaled back up, which is safe because the picture is already blurred by then.
     */
    fun softened(source: Bitmap, blur: Int): Bitmap {
        if (blur <= 0) return source
        val radius = io.github.highwindbr.aaxp.core.BlurLevel.radius(blur, maxOf(source.width, source.height))
        val scale = if (radius > 4f) 4f / radius else 1f
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val work = if (scale < 1f) Bitmap.createScaledBitmap(source, w, h, true) else source
        val pixels = IntArray(w * h).also { work.getPixels(it, 0, w, 0, 0, w, h) }
        val blurred = io.github.highwindbr.aaxp.core.ImageBlur.blur(pixels, w, h, radius * scale)
        val small = Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888)
        return if (scale < 1f) Bitmap.createScaledBitmap(small, source.width, source.height, true) else small
    }

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
            p.getInt("separator_thickness", 0), p.getString("separator_color", null) ?: "#FFFFFF",
            p.getInt("dim", 0).coerceIn(0, 80),
            io.github.highwindbr.aaxp.core.BlurLevel.fromStored(
                if (p.contains("blur_fine")) p.getInt("blur_fine", 0) else null, if (p.contains("blur")) p.getInt("blur", 0) else null),
            p.getInt("rail_shift_x", 0).coerceIn(-20, 20), p.getInt("rail_shift_y", 0).coerceIn(-20, 20))
    }

    fun save(context: Context, saved: Saved) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("width", saved.screen.width).putInt("height", saved.screen.height)
            .putString("rail", saved.screen.rail.name).putInt("rail_size", saved.screen.railSize)
            .putString("coverage", saved.coverage.name)
            .putFloat("zoom", saved.placement.zoom).putFloat("offset_x", saved.placement.offsetX)
            .putFloat("offset_y", saved.placement.offsetY).putInt("turns", saved.placement.quarterTurns)
            .putInt("separator_thickness", saved.separatorThickness).putString("separator_color", saved.separatorColor)
            .putInt("dim", saved.dim).putInt("blur_fine", saved.blurLevel).remove("blur")
            .putInt("rail_shift_x", saved.railShiftX).putInt("rail_shift_y", saved.railShiftY)
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
               separator: io.github.highwindbr.aaxp.core.FrameRect? = null, separatorColor: Int = 0,
               dim: Int = 0): Bitmap {
        val k = WallpaperGeometry.outputScale(canvasSpec, CarWallpaper.MAX_EDGE)
        val output = Bitmap.createBitmap((canvasSpec.width * k).toInt().coerceAtLeast(1),
            (canvasSpec.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.translate(canvasSpec.frameLeft * k, canvasSpec.frameTop * k)
        canvas.drawBitmap(source,
            matrix(source, canvasSpec.frameWidth * k, canvasSpec.frameHeight * k, placement, k),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (dim > 0) canvas.drawColor(android.graphics.Color.argb(dim.coerceIn(0, 100) * 255 / 100, 0, 0, 0))
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
internal fun CarWallpaperEditor(source: Bitmap, onCancel: () -> Unit, onSave: (Bitmap, Bitmap?, CarWallpaperStudio.Saved) -> Unit) {
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
    var dim by remember { mutableStateOf(initial.dim) }
    var blur by remember { mutableStateOf(initial.blurLevel) }
    var railShiftX by remember { mutableStateOf(initial.railShiftX) }
    var railShiftY by remember { mutableStateOf(initial.railShiftY) }
    // The blur is recomputed when the slider is released, not on every step.
    var blurApplied by remember { mutableStateOf(initial.blurLevel) }
    // Blurred off the main thread; the previous picture stays until the new one is ready.
    val blurredPicture by produceState(Triple(source, -1, source), source, blurApplied) {
        value = Triple(source, blurApplied, withContext(Dispatchers.Default) { CarWallpaperStudio.softened(source, blurApplied) })
    }
    val shown = blurredPicture.third

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
        val settings = CarWallpaperStudio.Saved(screen, coverage, placement,
            separatorThickness, separatorColorText, dim, blurApplied, railShiftX, railShiftY)
        // Save the picture that matches the saved setting, even if the preview is still catching up.
        val picture = if (blurredPicture.second == blurApplied) shown else CarWallpaperStudio.softened(source, blurApplied)
        val rendered = CarWallpaperStudio.render(picture, spec, placement,
            if (separatorColor != null) separator else null, separatorColor ?: 0, dim)
        onSave(rendered, CarWallpaperStudio.railSlice(rendered, screen, coverage, railShiftX, railShiftY), settings)
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
                        it.nativeCanvas.drawBitmap(shown,
                            CarWallpaperStudio.matrix(shown, size.width, size.height, placement, perPixel), paint)
                    }
                    if (dim > 0) drawRect(Color.Black.copy(alpha = dim / 100f))
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

                if (coverage == WallpaperCoverage.WHOLE_SCREEN && rail != RailSide.NONE) {
                    Text("Rail alignment: $railShiftX px across, $railShiftY px down", style = MaterialTheme.typography.labelLarge)
                    Text("If the picture behind the rail does not line up with the rest, nudge it here. It is not shown in the preview.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(railShiftX.toFloat(), { railShiftX = it.toInt() }, valueRange = -20f..20f, steps = 39)
                    Slider(railShiftY.toFloat(), { railShiftY = it.toInt() }, valueRange = -20f..20f, steps = 39)
                }

                Text(if (dim == 0) "Darken: off" else "Darken: $dim%", style = MaterialTheme.typography.labelLarge)
                Slider(dim.toFloat(), { dim = it.toInt() }, valueRange = 0f..80f)
                Text(io.github.highwindbr.aaxp.core.BlurLevel.label(blur), style = MaterialTheme.typography.labelLarge)
                Slider(blur.toFloat(), { blur = it.toInt() }, valueRange = 0f..40f, steps = 39, onValueChangeFinished = { blurApplied = blur })

                Text("Cover", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(coverage == WallpaperCoverage.WHOLE_SCREEN, { coverage = WallpaperCoverage.WHOLE_SCREEN },
                        { Text("Whole screen, behind the rail too") })
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
                var pickingLine by remember { mutableStateOf(false) }
                if (pickingLine) ColorPickerDialog("Line colour", separatorColor ?: -1, onDismiss = { pickingLine = false }) {
                    separatorColorText = "#" + io.github.highwindbr.aaxp.core.ColorMath.toHex(it); pickingLine = false
                }
                OutlinedButton({ pickingLine = true }, Modifier.fillMaxWidth()) { Text("Pick a line colour…") }
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
