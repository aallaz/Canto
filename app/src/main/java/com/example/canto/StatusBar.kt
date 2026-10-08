package com.example.canto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

data class StatusBarState(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val volume: Int,
    /** Volume maximal autorisé par les réglages : borne haute de la barre de volume. */
    val volumeLimit: Int,
    val wifiConnected: Boolean,
    val transferActive: Boolean,
    val bluetoothConnected: Boolean,
    val updateAvailable: Boolean,
    val isDarkMode: Boolean
)

// Lues à chaque dessin : suivent le style courant.
private val IconColor: Color get() = CantoColors.Text.copy(alpha = 0.85f)
private val DimColor: Color get() = CantoColors.Secondary

/** Barre d'état du menu principal : batterie, mode clair/sombre, Wi-Fi, enceinte, volume, écran noir, mise à jour. */
@Composable
fun StatusBar(state: StatusBarState, actions: StatusBarActions, modifier: Modifier = Modifier) {
    var showBatteryPercent by remember { mutableStateOf(false) }
    if (showBatteryPercent) {
        LaunchedEffect(Unit) {
            delay(3000)
            showBatteryPercent = false
        }
    }

    // Trois groupes : indicateurs à gauche, volume au centre, actions à droite.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Batterie : icône seule, le pourcentage s'affiche 3 s au toucher.
            StatusButton({ showBatteryPercent = !showBatteryPercent }, width = if (showBatteryPercent) 96.dp else 44.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BatteryIcon(state.batteryLevel, state.isCharging)
                    if (showBatteryPercent) {
                        Text(
                            if (state.batteryLevel >= 0) "${state.batteryLevel}%" else "?",
                            color = IconColor,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            // Soleil = mode clair, lune = mode sombre ; un toucher change de mode.
            StatusButton(actions.onToggleDarkMode) { if (state.isDarkMode) MoonIcon() else SunIcon() }
            // Wi-Fi en couleur d'accent quand le transfert est actif.
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                WifiIcon(state.wifiConnected, active = state.transferActive)
            }
            if (state.bluetoothConnected) BluetoothIcon()
        }

        // Volume réglable par l'enfant, de 0 à la limite choisie dans les réglages.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val limit = state.volumeLimit.coerceAtLeast(1)
            SpeakerIcon(state.volume.toFloat() / limit)
            Slider(
                value = state.volume.coerceIn(0, limit).toFloat(),
                onValueChange = { actions.onVolumeChange(Math.round(it)) },
                valueRange = 0f..limit.toFloat(),
                // Même couleur que les icônes de la barre.
                colors = SliderDefaults.colors(
                    thumbColor = IconColor,
                    activeTrackColor = IconColor,
                    inactiveTrackColor = CantoColors.Secondary
                ),
                modifier = Modifier.width(170.dp)
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.updateAvailable) {
                Text("MAJ", color = CantoColors.Amber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            }
            // Écran noir : un toucher n'importe où le rallume.
            StatusButton(actions.onScreenOff) { BulbIcon() }
        }
    }
}

/** Icône seule (pas de bouton), avec une zone de toucher confortable. */
@Composable
private fun StatusButton(onClick: () -> Unit, width: Dp = 44.dp, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = 44.dp)
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
private fun BatteryIcon(level: Int, charging: Boolean) {
    Canvas(modifier = Modifier.size(width = 26.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        val tip = 3.dp.toPx()
        val bodyWidth = size.width - tip
        drawRect(IconColor, Offset(stroke / 2, stroke / 2), Size(bodyWidth - stroke, size.height - stroke), style = Stroke(stroke))
        drawRect(IconColor, Offset(bodyWidth, size.height * 0.3f), Size(tip, size.height * 0.4f))
        val inner = bodyWidth - 3 * stroke
        val fraction = level.coerceIn(0, 100) / 100f
        drawRect(
            if (level in 0..15 && !charging) CantoColors.Warning else IconColor,
            Offset(1.5f * stroke, 1.5f * stroke),
            Size(inner * fraction, size.height - 3 * stroke)
        )
        if (charging) {
            // Éclair découpé dans la jauge.
            val bolt = Path().apply {
                moveTo(bodyWidth * 0.55f, size.height * 0.12f)
                lineTo(bodyWidth * 0.36f, size.height * 0.56f)
                lineTo(bodyWidth * 0.5f, size.height * 0.56f)
                lineTo(bodyWidth * 0.44f, size.height * 0.9f)
                lineTo(bodyWidth * 0.66f, size.height * 0.42f)
                lineTo(bodyWidth * 0.52f, size.height * 0.42f)
                close()
            }
            drawPath(bolt, CantoColors.Background)
        }
    }
}

@Composable
private fun SunIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
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

/** Ampoule : éteindre l'écran. */
@Composable
private fun BulbIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawCircle(IconColor, radius = w * 0.3f, center = Offset(w / 2, h * 0.38f), style = Stroke(stroke))
        drawLine(IconColor, Offset(w * 0.38f, h * 0.74f), Offset(w * 0.62f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(IconColor, Offset(w * 0.41f, h * 0.88f), Offset(w * 0.59f, h * 0.88f), stroke, StrokeCap.Round)
        drawLine(IconColor, Offset(w * 0.4f, h * 0.62f), Offset(w * 0.4f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(IconColor, Offset(w * 0.6f, h * 0.62f), Offset(w * 0.6f, h * 0.74f), stroke, StrokeCap.Round)
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
            drawLine(CantoColors.Warning, Offset(w * 0.62f, h * 0.35f), Offset(w * 0.92f, h * 0.65f), stroke, StrokeCap.Round)
            drawLine(CantoColors.Warning, Offset(w * 0.92f, h * 0.35f), Offset(w * 0.62f, h * 0.65f), stroke, StrokeCap.Round)
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
            drawLine(CantoColors.Warning, Offset(size.width * 0.15f, size.height * 0.15f), Offset(size.width * 0.85f, size.height * 0.9f), stroke, StrokeCap.Round)
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
