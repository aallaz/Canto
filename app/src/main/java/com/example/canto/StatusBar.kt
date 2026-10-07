package com.example.canto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class StatusBarState(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val brightness: Float,
    val volume: Int,
    val maxVolume: Int,
    val wifiConnected: Boolean,
    val transferActive: Boolean,
    val bluetoothConnected: Boolean,
    val updateAvailable: Boolean
)

private val IconColor = CantoColors.Text.copy(alpha = 0.75f)
private val DimColor = CantoColors.Frame

/** Barre d'état fixe : batterie, luminosité, volume, Wi-Fi (+ transfert), enceinte, mise à jour, réglages. */
@Composable
fun StatusBar(state: StatusBarState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 18.dp, end = 24.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem(if (state.batteryLevel >= 0) "${state.batteryLevel}%${if (state.isCharging) " ⚡" else ""}" else "?") {
            BatteryIcon(state.batteryLevel)
        }
        StatusItem("${(state.brightness * 100).toInt()}%") { SunIcon() }
        val volumeFraction = if (state.maxVolume > 0) state.volume.toFloat() / state.maxVolume else 0f
        StatusItem("${(volumeFraction * 100).toInt()}%") { SpeakerIcon(volumeFraction) }
        StatusItem(if (state.transferActive) "transfert" else "", highlight = state.transferActive) {
            WifiIcon(state.wifiConnected)
        }
        if (state.bluetoothConnected) {
            StatusItem("") { BluetoothIcon() }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.updateAvailable) {
            Text("⬆ MAJ", color = CantoColors.Amber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
        BrutalButton("⚙", CantoColors.Frame, onOpenSettings, Modifier.width(60.dp), CantoColors.Text)
    }
}

@Composable
private fun StatusItem(text: String, highlight: Boolean = false, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        if (text.isNotEmpty()) {
            Text(
                text,
                color = if (highlight) CantoColors.Amber else IconColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BatteryIcon(level: Int) {
    Canvas(modifier = Modifier.size(width = 26.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        val tip = 3.dp.toPx()
        val bodyWidth = size.width - tip
        drawRect(IconColor, Offset(stroke / 2, stroke / 2), Size(bodyWidth - stroke, size.height - stroke), style = Stroke(stroke))
        drawRect(IconColor, Offset(bodyWidth, size.height * 0.3f), Size(tip, size.height * 0.4f))
        val inner = bodyWidth - 3 * stroke
        val fraction = level.coerceIn(0, 100) / 100f
        drawRect(
            if (level in 0..15) CantoColors.Ember else IconColor,
            Offset(1.5f * stroke, 1.5f * stroke),
            Size(inner * fraction, size.height - 3 * stroke)
        )
    }
}

@Composable
private fun SunIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(IconColor, radius = size.minDimension * 0.2f, center = center)
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                IconColor,
                center + direction * (size.minDimension * 0.32f),
                center + direction * (size.minDimension * 0.48f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SpeakerIcon(level: Float) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.25f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.18f)
            lineTo(w * 0.48f, h * 0.82f)
            lineTo(w * 0.25f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.62f)
            close()
        }
        drawPath(body, IconColor)
        if (level <= 0f) {
            drawLine(CantoColors.Ember, Offset(w * 0.62f, h * 0.35f), Offset(w * 0.92f, h * 0.65f), stroke, StrokeCap.Round)
            drawLine(CantoColors.Ember, Offset(w * 0.92f, h * 0.35f), Offset(w * 0.62f, h * 0.65f), stroke, StrokeCap.Round)
            return@Canvas
        }
        val waves = when {
            level < 0.34f -> 1
            level < 0.67f -> 2
            else -> 3
        }
        repeat(waves) { index ->
            val radius = w * (0.18f + index * 0.14f)
            drawArc(
                IconColor,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.48f - radius, h / 2 - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun WifiIcon(connected: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val color = if (connected) IconColor else DimColor
        val center = Offset(size.width / 2, size.height * 0.85f)
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val radius = size.height * fraction
            drawArc(
                color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        drawCircle(color, radius = stroke, center = center)
        if (!connected) {
            drawLine(CantoColors.Ember, Offset(size.width * 0.15f, size.height * 0.15f), Offset(size.width * 0.85f, size.height * 0.9f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun BluetoothIcon() {
    Canvas(modifier = Modifier.size(width = 14.dp, height = 22.dp)) {
        val w = size.width
        val h = size.height
        val rune = Path().apply {
            moveTo(w * 0.1f, h * 0.28f)
            lineTo(w * 0.9f, h * 0.7f)
            lineTo(w * 0.5f, h * 0.95f)
            lineTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.9f, h * 0.3f)
            lineTo(w * 0.1f, h * 0.72f)
        }
        drawPath(rune, CantoColors.Teal, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
