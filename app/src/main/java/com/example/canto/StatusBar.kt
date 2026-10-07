package com.example.canto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class StatusBarState(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val volume: Int,
    /** Volume maximal autorisé par les réglages : borne haute de la barre de volume. */
    val volumeLimit: Int,
    val wifiConnected: Boolean,
    val transferActive: Boolean,
    val bluetoothConnected: Boolean,
    val updateAvailable: Boolean
)

private val IconColor = CantoColors.Text.copy(alpha = 0.75f)
private val DimColor = CantoColors.Frame

/** Barre d'état : batterie, Wi-Fi (+ transfert), enceinte, volume, mise à jour, réglages. */
@Composable
fun StatusBar(
    state: StatusBarState,
    onOpenSettings: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onScreenOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem(if (state.batteryLevel >= 0) "${state.batteryLevel}%${if (state.isCharging) " ⚡" else ""}" else "?") {
            BatteryIcon(state.batteryLevel)
        }
        // Wi-Fi en orange quand le transfert est actif.
        WifiIcon(state.wifiConnected, active = state.transferActive)
        if (state.bluetoothConnected) BluetoothIcon()

        Spacer(modifier = Modifier.weight(1f))

        // Volume réglable par l'enfant, de 0 à la limite choisie dans les réglages.
        val limit = state.volumeLimit.coerceAtLeast(1)
        SpeakerIcon(state.volume.toFloat() / limit)
        Slider(
            value = state.volume.coerceIn(0, limit).toFloat(),
            onValueChange = { onVolumeChange(Math.round(it)) },
            valueRange = 0f..limit.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = CantoColors.Amber,
                activeTrackColor = CantoColors.Amber,
                inactiveTrackColor = CantoColors.Frame
            ),
            modifier = Modifier.width(170.dp)
        )

        // Écran noir : un toucher n'importe où le rallume.
        StatusButton(onScreenOff) { MoonIcon() }

        if (state.updateAvailable) {
            Text("MAJ", color = CantoColors.Amber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
        StatusButton(onOpenSettings) { GearIcon() }
    }
}

/** Icône seule (pas de bouton), avec une zone de toucher confortable. */
@Composable
private fun StatusButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun MoonIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val radius = size.minDimension * 0.42f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(IconColor, radius = radius, center = center)
        // Croissant : un disque de la couleur du fond masque une partie de la lune.
        drawCircle(CantoColors.Background, radius = radius * 0.85f, center = center + Offset(radius * 0.55f, -radius * 0.35f))
    }
}

@Composable
private fun StatusItem(text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        if (text.isNotEmpty()) {
            Text(
                text,
                color = IconColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GearIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.3f
        val toothWidth = size.minDimension * 0.16f
        val toothLength = size.minDimension * 0.16f
        repeat(8) { index ->
            rotate(index * 45f, center) {
                drawRect(
                    IconColor,
                    Offset(center.x - toothWidth / 2, center.y - radius - toothLength),
                    Size(toothWidth, toothLength + 2)
                )
            }
        }
        drawCircle(IconColor, radius = radius, center = center, style = Stroke(size.minDimension * 0.16f))
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
private fun WifiIcon(connected: Boolean, active: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val color = when {
            active -> CantoColors.Amber
            connected -> IconColor
            else -> DimColor
        }
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
