package io.github.highwindbr.aaxp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.highwindbr.aaxp.core.CarPreviewColors
import io.github.highwindbr.aaxp.core.CarTheme
import io.github.highwindbr.aaxp.core.ColorMath
import io.github.highwindbr.aaxp.core.Presets
import io.github.highwindbr.aaxp.core.Themes

private val QUICK_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFE10600, 0xFFFF2D95, 0xFFFF8A3D, 0xFFFFB000,
    0xFF5BD68A, 0xFF2DE2E6, 0xFF00B4D8, 0xFF4285F4, 0xFF8E5BFF
).map { it.toInt() }

/** A colour chip. Transparent colours are shown over a light/dark split so opacity is visible. */
@Composable
internal fun ColorSwatch(color: Int?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.size(36.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(Color.White, Color.White, Color(0xFF303030), Color(0xFF303030))))
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        if (color != null) Box(Modifier.fillMaxSize().background(Color(color)))
        else Text("–", Modifier.align(Alignment.Center), color = Color(0xFF808080))
    }
}

/**
 * Colour picker: a saturation/brightness field, a hue bar, an opacity slider, a hex field and
 * quick colours. [onPick] receives the chosen ARGB colour.
 */
@Composable
internal fun ColorPickerDialog(
    title: String, initial: Int, allowAlpha: Boolean = true,
    onDismiss: () -> Unit, onPick: (Int) -> Unit
) {
    val start = remember(initial) { ColorMath.colorToHsv(initial) }
    var hue by remember(initial) { mutableStateOf(start[0]) }
    var saturation by remember(initial) { mutableStateOf(start[1]) }
    var value by remember(initial) { mutableStateOf(start[2]) }
    var alpha by remember(initial) { mutableStateOf(if (allowAlpha) ColorMath.alpha(initial) else 255) }
    val color = ColorMath.hsvToColor(hue, saturation, value, alpha)
    var hex by remember(initial) { mutableStateOf(ColorMath.toHex(color)) }
    fun fromColor(picked: Int) {
        val hsv = ColorMath.colorToHsv(picked)
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
        alpha = if (allowAlpha) ColorMath.alpha(picked) else 255
        hex = ColorMath.toHex(ColorMath.hsvToColor(hue, saturation, value, alpha))
    }
    fun syncHex() { hex = ColorMath.toHex(ColorMath.hsvToColor(hue, saturation, value, alpha)) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Saturation (left to right) and brightness (bottom to top) for the chosen hue.
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun update(position: Offset) {
                            saturation = (position.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (position.y / size.height).coerceIn(0f, 1f)
                            syncHex()
                        }
                        val down = awaitFirstDown(); update(down.position)
                        drag(down.id) { change -> update(change.position); change.consume() }
                    }
                }) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(ColorMath.hsvToColor(hue, 1f, 1f)))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val thumb = Offset(saturation * size.width, (1f - value) * size.height)
                drawCircle(Color.White, 9.dp.toPx(), thumb, style = Stroke(2.5.dp.toPx()))
                drawCircle(Color.Black, 10.5.dp.toPx(), thumb, style = Stroke(1.dp.toPx()))
            }
            // Hue.
            Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun update(position: Offset) { hue = (position.x / size.width).coerceIn(0f, 1f) * 360f; syncHex() }
                        val down = awaitFirstDown(); update(down.position)
                        drag(down.id) { change -> update(change.position); change.consume() }
                    }
                }) {
                drawRect(Brush.horizontalGradient((0..6).map { Color(ColorMath.hsvToColor(it * 60f, 1f, 1f)) }))
                val x = hue / 360f * size.width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
                drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
            }
            if (allowAlpha) {
                Text("Opacity ${(alpha * 100 / 255)}%", style = MaterialTheme.typography.labelLarge)
                Slider(alpha.toFloat(), { alpha = it.toInt(); syncHex() }, valueRange = 0f..255f)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSwatch(color, Modifier.size(48.dp))
                OutlinedTextField(hex, { typed ->
                    hex = typed.take(9)
                    ColorMath.parseHex(hex)?.let { parsed ->
                        val hsv = ColorMath.colorToHsv(parsed)
                        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                        alpha = if (allowAlpha) ColorMath.alpha(parsed) else 255
                    }
                }, Modifier.weight(1f), label = { Text(if (allowAlpha) "RRGGBB or AARRGGBB" else "RRGGBB") },
                    singleLine = true, isError = ColorMath.parseHex(hex) == null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_COLORS.forEach { quick -> ColorSwatch(quick, onClick = { fromColor(ColorMath.withAlpha(quick, alpha)) }) }
            }
        } },
        confirmButton = { TextButton({ onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri).use { input ->
        val bytes = checkNotNull(input) { "Cannot read the file" }.readBytes()
        require(bytes.size <= 1024 * 1024) { "File is too large to be a themes file" }
        String(bytes, Charsets.UTF_8)
    }

/** Four colours that give an impression of a theme. */
@Composable
private fun ThemeStrip(roles: Map<String, Int>) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
        listOf("Background", "Primary", "Secondary", "Tertiary").forEach { role ->
            Box(Modifier.weight(1f).height(26.dp).background(Color(roles[role] ?: roles["Surface"] ?: 0xFF808080.toInt())))
        }
    }
}

/**
 * A small drawing of the car's dashboard, recoloured live. Only the seven confirmed colour slots
 * (and the icon colours when single-colour icons are on) change it; everything else is fixed.
 */
@Composable
internal fun CarScreenMock(roles: Map<String, Int>, glyphColors: String?, modifier: Modifier = Modifier) {
    val k = remember(roles.toMap(), glyphColors) { CarPreviewColors.resolve(roles, glyphColors) }
    fun col(v: Int) = Color(v)
    val offWhite = col(CarPreviewColors.RAIL_FIXED_ICON)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().aspectRatio(1200f / 723f).clip(RoundedCornerShape(10.dp)).background(col(CarPreviewColors.RAIL_BACKGROUND))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))) {
            // Rail (fixed black): clock, three app icons ringed in OutlineVariant, off-white launcher dot.
            Column(Modifier.weight(107f).fillMaxSize().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(offWhite))
                repeat(3) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(col(k.railIconBackground))
                        .border(1.5.dp, col(k.outlineVariant), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(col(k.railIcon)))
                    }
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(offWhite))
            }
            // Map (fixed) with the round floating button at its top left.
            Box(Modifier.weight(560f).fillMaxSize().padding(vertical = 5.dp).clip(RoundedCornerShape(8.dp)).background(col(CarPreviewColors.MAP_BACKGROUND))) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp).clip(CircleShape).background(col(k.surfaceHigh))
                    .border(1.5.dp, col(k.outlineVariant), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(col(k.onSurface)))
                }
            }
            // Right: a card with two rows, then the media card.
            Column(Modifier.weight(533f).fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(col(k.surface)).padding(6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Home", "Work").forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(15.dp).clip(CircleShape).background(col(k.surfaceVariant)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(col(k.onSurfaceVariant)))
                            }
                            Text(label, color = col(k.onSurface), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(col(k.onSurface)))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(col(k.onSurfaceVariant)))
                    }
                }
                Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(col(CarPreviewColors.MEDIA_BACKGROUND)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Song title", color = col(k.textPrimary), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Artist", color = col(k.textPrimary), fontSize = 7.sp, maxLines = 1)
                    }
                    Box(Modifier.size(5.dp).background(col(k.onSurfaceVariant)))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(col(CarPreviewColors.PLAY_BUTTON)))
                    Box(Modifier.size(5.dp).background(col(k.onSurfaceVariant)))
                }
            }
        }
        Text("Shows the seven colour slots confirmed so far. Other slots have not been seen in use yet." +
            if (k.usesApproximation) " Slots you have not set are approximate." else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One colour slot: swatch, name, current value (or "Default") and a clear button. */
@Composable
private fun RoleRow(role: String, current: Int?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorSwatch(current, onClick = onPick)
        Column(Modifier.weight(1f)) {
            Text(role, style = MaterialTheme.typography.bodyMedium)
            val hint = Themes.hint(role)
            Text(listOf(current?.let { ColorMath.toHex(it) } ?: "Default", hint).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current != null) IconButton(onClear, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Clear $role") }
    }
}

/**
 * Full-screen theme editor with a live mock of the car screen pinned at the top and three steps:
 * Start (themes and one colour), Colours (colour slots) and Keep and share. Nothing is applied to
 * Android Auto until "Apply to car" is pressed.
 */
@Composable
internal fun ThemeEditor(model: ManagerViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val initialRoles = remember { model.themeColorsMap() }
    val roles = remember { mutableStateMapOf<String, Int>().apply { putAll(initialRoles) } }
    // Single-colour icon colours live on the App icons page; the editor only carries them along.
    val initialGlyph = remember { model.glyphIconColors() }
    var glyphText by remember { mutableStateOf(initialGlyph) }
    var seed by remember { mutableStateOf(0xFFFF2D95.toInt()) }
    var seedDark by remember { mutableStateOf(true) }
    var savedThemes by remember { mutableStateOf(model.carThemes()) }
    var themeName by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }
    val edited = roles.toMap() != initialRoles || glyphText != initialGlyph

    fun load(theme: CarTheme) {
        roles.clear(); roles.putAll(theme.roles)
        theme.glyphColors?.let { glyphText = it }
        themeName = if (theme.builtIn) "" else theme.name
    }
    fun close() { if (edited) confirmCancel = true else onClose() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { out ->
                checkNotNull(out) { "Cannot write the file" }.write(Themes.encode(savedThemes).toByteArray(Charsets.UTF_8))
            }
        }.fold({ model.reportMessage("Themes exported.") }, { model.reportMessage("Themes not exported: ${it.message}") })
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { Themes.decode(readText(context, uri)) }
                .fold({ model.importCarThemes(it); savedThemes = model.carThemes() },
                    { model.reportMessage("Themes not imported: ${it.message}") })
        }
    }

    picking?.let { (title, start, apply) ->
        ColorPickerDialog(title, start, onDismiss = { picking = null }) { picked -> apply(picked); picking = null }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("Discard your changes?") },
        text = { Text("You have colour changes that are not applied to the car.") },
        confirmButton = { TextButton({ confirmCancel = false; onClose() }) { Text("Discard") } },
        dismissButton = { TextButton({ confirmCancel = false }) { Text("Keep editing") } })

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme editor", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(::close) { Text("Cancel") }
                Button({ model.applyCarTheme(roles.toMap(), glyphText); onClose() }) { Text("Apply to car") }
                Box {
                    IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Load role finder") }, { menu = false; roles.clear(); roles.putAll(Themes.roleFinder()); themeName = "" })
                    }
                }
            }
            // Live mock, pinned above the steps.
            Box(Modifier.padding(horizontal = 16.dp)) { CarScreenMock(roles, glyphText, Modifier.fillMaxWidth()) }
            // Single-colour icons are set on the App icons page; here they can only be switched on or off.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (glyphText != null) "Single-colour icons: on (colours set on the App icons page)" else "Single-colour icons: off",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Switch(glyphText != null, { on ->
                    glyphText = if (on) (initialGlyph ?: io.github.highwindbr.aaxp.runtime.GlyphIcons.DEFAULT_COLORS) else null
                })
            }
            TabRow(tab, Modifier.padding(top = 8.dp)) {
                listOf("Start", "Colours", "Keep and share").forEachIndexed { index, label ->
                    Tab(tab == index, { tab = index }, text = { Text(label) })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        Text("Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        (Themes.BUILT_IN + savedThemes).forEach { theme ->
                            Card(Modifier.fillMaxWidth().clickable { load(theme) }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(theme.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (!theme.builtIn) {
                                            Text("Saved", Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            TextButton({ model.deleteCarTheme(theme.name); savedThemes = model.carThemes() }) { Text("Delete") }
                                        }
                                    }
                                    if (theme.description.isNotEmpty()) Text(theme.description, style = MaterialTheme.typography.bodySmall)
                                    ThemeStrip(theme.roles)
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("Start from one colour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorSwatch(seed, onClick = { picking = Triple("Base colour", seed) { seed = ColorMath.withAlpha(it, 255) } })
                            FilterChip(seedDark, { seedDark = true }, { Text("Dark") })
                            FilterChip(!seedDark, { seedDark = false }, { Text("Light") })
                            Button({ roles.clear(); roles.putAll(Themes.generate(seed, seedDark)) }) { Text("Generate") }
                        }
                        OutlinedButton({
                            val picked = runCatching {
                                CarWallpaperStudio.loadSource(context)?.let { image ->
                                    val small = android.graphics.Bitmap.createScaledBitmap(image, 64, 64, true)
                                    val pixels = IntArray(64 * 64).also { small.getPixels(it, 0, 64, 0, 0, 64, 64) }
                                    ColorMath.dominantColor(pixels)
                                }
                            }.getOrNull()
                            if (picked != null) { seed = picked; roles.clear(); roles.putAll(Themes.generate(picked, seedDark)) }
                            else model.reportMessage("No base colour found: set a car wallpaper with some colour in it first.")
                        }, Modifier.fillMaxWidth()) { Text("Use my car wallpaper's main colour") }
                    }
                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${roles.size} colour slots set", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton({ roles.clear() }) { Text("Clear all") }
                        }
                        OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), label = { Text("Find a colour slot") }, singleLine = true)
                        val q = filter.trim()
                        fun matches(role: String) = q.isBlank() || role.contains(q, true) || Themes.hint(role).contains(q, true)
                        val confirmed = Themes.CONFIRMED_ROLES.filter(::matches)
                        if (confirmed.isNotEmpty()) {
                            Text("These change the car screen", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            confirmed.forEach { role ->
                                RoleRow(role, roles[role], { picking = Triple(role, roles[role] ?: 0xFF808080.toInt()) { roles[role] = it } }, { roles.remove(role) })
                            }
                        }
                        val unseen = Themes.UNSEEN_ROLES.filter(::matches)
                        if (unseen.isNotEmpty()) {
                            Text("Not seen in use yet", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("These may do nothing, or change something that has not been spotted yet.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            unseen.forEach { role ->
                                RoleRow(role, roles[role], { picking = Triple(role, roles[role] ?: 0xFF808080.toInt()) { roles[role] = it } }, { roles.remove(role) })
                            }
                        }
                    }
                    else -> {
                        Text("Keep this as my theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(themeName, { themeName = it.take(Presets.MAX_NAME_LENGTH) }, Modifier.weight(1f),
                                label = { Text("Theme name") }, singleLine = true)
                            Button({
                                if (model.saveCarTheme(CarTheme(themeName, "", roles.toMap(), glyphText)))
                                    savedThemes = model.carThemes()
                            }, enabled = Presets.cleanName(themeName) != null) { Text("Keep") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ exporter.launch("aa-experiments-themes.json") }, Modifier.weight(1f), enabled = savedThemes.isNotEmpty()) { Text("Export mine") }
                            OutlinedButton({ importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, Modifier.weight(1f)) { Text("Import") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({
                                val name = Presets.cleanName(themeName) ?: "Shared theme"
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(
                                    Themes.encode(listOf(CarTheme(name, "", roles.toMap(), glyphText)))))
                                model.reportMessage("Theme copied as text.")
                            }, Modifier.weight(1f), enabled = roles.isNotEmpty()) { Text("Copy as text") }
                            OutlinedButton({
                                runCatching { Themes.decode(clipboard.getText()?.text.orEmpty()).first() }
                                    .fold({ load(it) }, { model.reportMessage("The clipboard does not hold a theme.") })
                            }, Modifier.weight(1f)) { Text("Paste theme") }
                        }
                    }
                }
                Spacer(Modifier.height(200.dp))
            }
        } }
    }
}
