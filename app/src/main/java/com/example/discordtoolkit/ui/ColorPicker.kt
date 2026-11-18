package com.example.discordtoolkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.ui.theme.DcColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun hsvToRgb(h: Float, s: Float, v: Float): Int = Color.hsv(h, s, v).toArgb() and 0xFFFFFF

private fun hexOf(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

/**
 * Selector de color con rueda (tono en el ángulo, saturación en el radio), barra de brillo
 * y campo para escribir el código hexadecimal.
 */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember {
        val hsv = FloatArray(3)
        if (initial != 0) {
            android.graphics.Color.colorToHSV(initial or 0xFF000000.toInt(), hsv)
        } else {
            hsv[0] = 235f
            hsv[1] = 0.6f
            hsv[2] = 1f
        }
        hsv
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(hsvToRgb(start[0], start[1], start[2]))) }

    val rgb = hsvToRgb(hue, sat, value)
    val current = Color(0xFF000000.toInt() or rgb)

    fun refreshHex() {
        hex = hexOf(hsvToRgb(hue, sat, value))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text("Elegí un color") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier
                        .size(240.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun pick(p: Offset) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = min(cx, cy)
                                    val dx = p.x - cx
                                    val dy = p.y - cy
                                    sat = min(hypot(dx, dy) / radius, 1f)
                                    var deg = (atan2(dy, dx) * 180f / PI.toFloat())
                                    if (deg < 0f) deg += 360f
                                    hue = deg
                                    refreshHex()
                                }
                                pick(down.position)
                                drag(down.id) { change ->
                                    pick(change.position)
                                    change.consume()
                                }
                            }
                        }
                ) {
                    val radius = min(size.width, size.height) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            ),
                            center = center
                        ),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                    // El brillo oscurece toda la rueda.
                    drawCircle(
                        color = Color.Black.copy(alpha = 1f - value),
                        radius = radius,
                        center = center
                    )
                    // Marcador de la posición elegida.
                    val rad = hue * PI.toFloat() / 180f
                    val marker = Offset(
                        center.x + cos(rad) * sat * radius,
                        center.y + sin(rad) * sat * radius
                    )
                    drawCircle(color = current, radius = 11.dp.toPx(), center = marker)
                    drawCircle(
                        color = Color.White,
                        radius = 11.dp.toPx(),
                        center = marker,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Brillo", color = DcColors.TextMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        refreshHex()
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = current,
                        activeTrackColor = current
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(current)
                            .border(1.dp, DcColors.Border, RoundedCornerShape(10.dp))
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text.take(7)
                            val clean = text.removePrefix("#")
                            if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                val parsed = clean.toInt(16)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed or 0xFF000000.toInt(), hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("Código hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Negro puro (0) en Discord significa "sin color", así que se evita con el valor más cercano.
                onPick(if (rgb == 0) 1 else rgb)
            }) { Text("Listo") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(0) }) { Text("Sin color") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
