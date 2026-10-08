package com.example.canto

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val SCREEN_FADE_OUT_MS = 1500
const val SCREEN_FADE_IN_MS = 500

data class PlayerUiState(
    val story: StoryFolder,
    val currentAudioName: String,
    val currentIndex: Int,
    val audioCount: Int,
    val isPlaying: Boolean
)

data class UpdateUiState(
    val currentVersion: String = "",
    val availableVersion: String? = null,
    val isBusy: Boolean = false,
    val message: String = ""
)

data class SettingsUiState(
    val batteryLevel: Int,
    val brightness: Float,
    val volume: Int,
    val volumeLimit: Int,
    val screenOffDelaySeconds: Int,
    val maxVolume: Int,
    val styleName: String,
    val storiesRoots: List<StorageLocator.StoriesRoot>,
    val needsAllFilesAccess: Boolean,
    val wifiUrl: String?,
    val bluetooth: BluetoothUiState,
    val update: UpdateUiState,
    val info: String
)

interface SettingsActions {
    fun hasPin(): Boolean
    fun checkPin(pin: String): Boolean
    fun savePin(pin: String)
    fun onBrightnessChange(value: Float)
    fun onVolumeChange(value: Int)
    fun onVolumeLimitChange(value: Int)
    fun onScreenOffDelayChange(seconds: Int)
    fun onStyleChange(name: String)
    fun onRescan()
    fun onRequestAllFilesAccess()
    fun onToggleWifi()
    fun onBluetoothEnable()
    fun onBluetoothScan()
    fun onBluetoothConnect(address: String)
    fun onBluetoothDisconnect(address: String)
    fun onOpenBluetoothSettings()
    fun onCheckUpdate()
    fun onInstallUpdate()
    fun onPowerOff()
    fun onExitApp()
    fun onClose()
}

/** Actions de la barre du haut. */
class StatusBarActions(
    val onOpenSettings: () -> Unit,
    val onVolumeChange: (Int) -> Unit,
    val onScreenOff: () -> Unit,
    val onToggleDarkMode: () -> Unit
)

@Composable
fun CantoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CantoColors.Amber,
            onPrimary = CantoColors.OnAccent,
            background = CantoColors.Background,
            onBackground = CantoColors.Text,
            surface = CantoColors.Surface,
            onSurface = CantoColors.Text
        ),
        content = content
    )
}

@Composable
fun AppScreen(
    storyFolders: List<StoryFolder>,
    isScanning: Boolean,
    message: String,
    status: StatusBarState,
    statusActions: StatusBarActions,
    player: PlayerUiState?,
    settings: SettingsUiState?,
    settingsActions: SettingsActions,
    onSelectStory: (StoryFolder) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isScreenDark: Boolean,
    onScreenWake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CantoColors.Background)
    ) {
        if (player != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                StatusBar(status, statusActions, Modifier.padding(start = 18.dp, end = 16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    PlayerScreen(
                        state = player,
                        onBack = onBack,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext
                    )
                }
            }
        } else {
            GalleryScreen(
                storyFolders = storyFolders,
                isScanning = isScanning,
                message = message,
                status = status,
                statusActions = statusActions,
                onSelectStory = onSelectStory
            )
        }
    }

    if (settings != null) {
        SettingsOverlay(state = settings, actions = settingsActions)
    }

    // Écran noir par-dessus tout ; un toucher n'importe où le rallume.
    // Fondu : extinction douce, rallumage plus rapide.
    val darkness by animateFloatAsState(
        targetValue = if (isScreenDark) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScreenDark) SCREEN_FADE_OUT_MS else SCREEN_FADE_IN_MS),
        label = "écran noir"
    )
    if (darkness > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = darkness))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onScreenWake
                )
        )
    }
}

@Composable
private fun GalleryScreen(
    storyFolders: List<StoryFolder>,
    isScanning: Boolean,
    message: String,
    status: StatusBarState,
    statusActions: StatusBarActions,
    onSelectStory: (StoryFolder) -> Unit
) {
    if (storyFolders.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(status, statusActions, Modifier.padding(start = 18.dp, end = 16.dp))
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CantoColors.Amber)
                }
            } else {
                BrutalFrame(
                    color = CantoColors.Surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 25.dp, top = 12.dp)
                ) {
                    Text(
                        message.ifBlank {
                            "Ajoute des dossiers dans Histoires avec des fichiers audio et une image cover.jpg."
                        },
                        color = CantoColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        // Marge en bas et à droite pour que l'ombre des tuiles ne touche pas le bord.
        contentPadding = PaddingValues(start = 18.dp, top = 0.dp, end = 25.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // La barre fait partie de la grille : elle monte et disparaît quand on fait défiler.
        item(span = { GridItemSpan(maxLineSpan) }) {
            StatusBar(status, statusActions)
        }
        items(storyFolders) { story ->
            StoryTile(story = story, onClick = { onSelectStory(story) })
        }
    }
}

@Composable
private fun StoryTile(story: StoryFolder, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(6.dp, 6.dp)
                .background(CantoColors.Shadow)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CantoColors.Surface)
                .border(4.dp, CantoColors.Frame)
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StoryCover(
                path = story.coverPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Text(
                text = story.displayTitle().uppercase(),
                color = CantoColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, end = 23.dp, bottom = 23.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrutalFrame(
            color = CantoColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pochette carrée qui s'adapte à la hauteur disponible.
                    StoryCover(
                        path = state.story.coverPath,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .border(2.dp, CantoColors.Frame)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            state.story.displayTitle().uppercase(),
                            color = CantoColors.Amber,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.currentAudioName,
                            color = CantoColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "${state.currentIndex + 1} / ${state.audioCount}",
                    color = CantoColors.Text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }

        // Grandes icônes dessinées, lisibles par un enfant.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalIconButton(CantoColors.Moss, onPrevious, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Previous) }
            BrutalIconButton(CantoColors.Ember, onTogglePlayPause, Modifier.weight(1f)) {
                PlayerIcon(if (state.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play)
            }
            BrutalIconButton(CantoColors.Moss, onNext, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Next) }
            BrutalIconButton(CantoColors.Amber, onBack, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Home) }
        }
    }
}

private enum class PlayerGlyph { Previous, Play, Pause, Next, Home }

@Composable
private fun PlayerIcon(glyph: PlayerGlyph) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val color = CantoColors.OnAccent
        val w = size.width
        val h = size.height
        when (glyph) {
            PlayerGlyph.Previous -> drawPath(Path().apply {
                moveTo(w * 0.18f, h * 0.5f); lineTo(w * 0.78f, h * 0.15f); lineTo(w * 0.78f, h * 0.85f); close()
            }, color)
            PlayerGlyph.Play, PlayerGlyph.Next -> drawPath(Path().apply {
                moveTo(w * 0.82f, h * 0.5f); lineTo(w * 0.22f, h * 0.15f); lineTo(w * 0.22f, h * 0.85f); close()
            }, color)
            PlayerGlyph.Pause -> {
                drawRect(color, Offset(w * 0.22f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                drawRect(color, Offset(w * 0.58f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
            }
            PlayerGlyph.Home -> drawPath(Path().apply {
                moveTo(w * 0.5f, h * 0.14f); lineTo(w * 0.84f, h * 0.48f); lineTo(w * 0.84f, h * 0.86f)
                lineTo(w * 0.16f, h * 0.86f); lineTo(w * 0.16f, h * 0.48f); close()
            }, color, style = Stroke(w * 0.11f, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun StoryCover(path: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image de l’histoire",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(modifier = modifier, color = CantoColors.Secondary) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("♪", color = CantoColors.Text, style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

private enum class PinStage { Create, Confirm, Enter, Unlocked }

@Composable
private fun SettingsOverlay(state: SettingsUiState, actions: SettingsActions) {
    var stage by remember { mutableStateOf(if (actions.hasPin()) PinStage.Enter else PinStage.Create) }
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = actions::onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        BrutalFrame(
            color = CantoColors.Surface,
            modifier = Modifier
                .padding(12.dp)
                // Clavier du code : panneau étroit ; réglages : large.
                .widthIn(max = if (stage == PinStage.Unlocked) 680.dp else 360.dp)
                // Empêche un clic dans le panneau de fermer les réglages.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            when (stage) {
                PinStage.Create -> PinPad(
                    title = "Nouveau code parent",
                    error = pinError,
                    onClose = actions::onClose,
                    onComplete = { pin ->
                        firstPin = pin
                        pinError = ""
                        stage = PinStage.Confirm
                    }
                )

                PinStage.Confirm -> PinPad(
                    title = "Confirme le code",
                    error = pinError,
                    onClose = actions::onClose,
                    onComplete = { pin ->
                        if (pin == firstPin) {
                            actions.savePin(pin)
                            pinError = ""
                            stage = PinStage.Unlocked
                        } else {
                            pinError = "Les codes ne correspondent pas"
                            stage = PinStage.Create
                        }
                    }
                )

                PinStage.Enter -> PinPad(
                    title = "Code parent",
                    error = pinError,
                    onClose = actions::onClose,
                    onComplete = { pin ->
                        if (actions.checkPin(pin)) {
                            pinError = ""
                            stage = PinStage.Unlocked
                        } else {
                            pinError = "Code incorrect"
                        }
                    }
                )

                PinStage.Unlocked -> SettingsContent(
                    state = state,
                    actions = actions,
                    onChangePin = { stage = PinStage.Create }
                )
            }
        }
    }
}

/** Clavier 3 colonnes × 4 lignes ; la touche d'effacement occupe deux cases. */
@Composable
private fun PinPad(
    title: String,
    error: String,
    onClose: () -> Unit,
    onComplete: (String) -> Unit
) {
    var pin by remember(title) { mutableStateOf("") }

    fun press(digit: Char) {
        if (pin.length >= AppSettings.PIN_LENGTH) return
        pin += digit
        if (pin.length == AppSettings.PIN_LENGTH) {
            val entered = pin
            pin = ""
            onComplete(entered)
        }
    }

    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = CantoColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    error.ifEmpty { "●".repeat(pin.length) + "○".repeat(AppSettings.PIN_LENGTH - pin.length) },
                    color = if (error.isNotEmpty() && pin.isEmpty()) CantoColors.Warning else CantoColors.Amber,
                    style = if (error.isNotEmpty() && pin.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            CloseButton(onClose)
        }
        listOf("123", "456", "789").forEach { digits ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                digits.forEach { digit ->
                    BrutalButton(digit.toString(), CantoColors.Secondary, { press(digit) }, Modifier.weight(1f), CantoColors.Text, compact = true)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrutalButton("0", CantoColors.Secondary, { press('0') }, Modifier.weight(1f), CantoColors.Text, compact = true)
            BrutalButton("⌫", CantoColors.Ember, { pin = pin.dropLast(1) }, Modifier.weight(2f), compact = true)
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onChangePin: () -> Unit
) {
    var showBluetooth by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel("RÉGLAGES")
            SettingsText("Batterie ${if (state.batteryLevel >= 0) "${state.batteryLevel}%" else "?"} · version ${state.update.currentVersion}")
            CloseButton(actions::onClose)
        }

        // Volume max, luminosité et écran noir sur une ligne.
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            SettingSlider(
                label = "VOLUME MAX ${state.volumeLimit}/${state.maxVolume}",
                value = state.volumeLimit.toFloat(),
                onValueChange = { actions.onVolumeLimitChange(Math.round(it)) },
                range = 1f..state.maxVolume.coerceAtLeast(2).toFloat(),
                steps = (state.maxVolume - 2).coerceAtLeast(0)
            )
            SettingSlider(
                label = "LUMINOSITÉ ${(state.brightness * 100).toInt()}%",
                value = state.brightness,
                onValueChange = actions::onBrightnessChange,
                range = AppSettings.MIN_BRIGHTNESS..AppSettings.MAX_BRIGHTNESS
            )
            val delays = AppSettings.SCREEN_OFF_DELAYS
            val delayIndex = delays.indexOf(state.screenOffDelaySeconds).coerceAtLeast(0)
            SettingSlider(
                label = "ÉCRAN NOIR ${formatDelay(delays[delayIndex])}",
                value = delayIndex.toFloat(),
                onValueChange = { actions.onScreenOffDelayChange(delays[Math.round(it).coerceIn(0, delays.lastIndex)]) },
                range = 0f..delays.lastIndex.toFloat(),
                steps = (delays.size - 2).coerceAtLeast(0)
            )
        }

        // Style des couleurs (le mode sombre se choisit dans la barre du haut).
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsLabel("STYLE")
            Palettes.lightStyles.forEach { palette ->
                val selected = palette.name == state.styleName
                BrutalButton(
                    palette.name.uppercase(),
                    if (selected) CantoColors.Amber else CantoColors.Secondary,
                    { actions.onStyleChange(palette.name) },
                    Modifier.weight(1f),
                    if (selected) CantoColors.OnAccent else CantoColors.Text,
                    compact = true
                )
            }
        }

        // Transfert, enceinte, mise à jour : une ligne de boutons, détails en dessous.
        val update = state.update
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton(
                if (state.wifiUrl != null) "ARRÊTER TRANSFERT" else "TRANSFERT WI-FI",
                if (state.wifiUrl != null) CantoColors.Ember else CantoColors.Teal,
                actions::onToggleWifi,
                Modifier.weight(1f),
                compact = true
            )
            BrutalButton(
                "ENCEINTE",
                if (showBluetooth) CantoColors.Amber else CantoColors.Teal,
                { showBluetooth = !showBluetooth },
                Modifier.weight(1f),
                compact = true
            )
            BrutalButton(
                when {
                    update.isBusy -> "TÉLÉCHARGEMENT…"
                    update.availableVersion != null -> "INSTALLER ${update.availableVersion}"
                    else -> "MISE À JOUR"
                },
                if (update.availableVersion != null) CantoColors.Amber else CantoColors.Teal,
                {
                    when {
                        update.isBusy -> Unit
                        update.availableVersion != null -> actions.onInstallUpdate()
                        else -> actions.onCheckUpdate()
                    }
                },
                Modifier.weight(1f),
                compact = true
            )
        }
        if (state.wifiUrl != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsText("Sur un appareil du même Wi-Fi :")
                Text(state.wifiUrl, color = CantoColors.Amber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
        if (showBluetooth) BluetoothSection(state.bluetooth, actions)
        if (update.message.isNotEmpty()) SettingsText(update.message)

        // Histoires et recherche.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsLabel("HISTOIRES")
                if (state.storiesRoots.isEmpty()) {
                    SettingsText("Aucun dossier Histoires trouvé.")
                } else {
                    state.storiesRoots.forEach { root ->
                        SettingsText("${if (root.isRemovable) "Carte SD" else "Interne"} : ${root.dir.absolutePath}")
                    }
                }
            }
            if (state.needsAllFilesAccess) {
                BrutalButton("AUTORISER", CantoColors.Amber, actions::onRequestAllFilesAccess, Modifier.width(150.dp), compact = true)
            }
            BrutalButton("RECHERCHER", CantoColors.Teal, actions::onRescan, Modifier.width(150.dp), compact = true)
        }

        if (state.info.isNotEmpty()) {
            Text(state.info, color = CantoColors.Warning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton("CHANGER LE CODE", CantoColors.Secondary, onChangePin, Modifier.weight(1f), CantoColors.Text, compact = true)
            BrutalButton("ÉTEINDRE", CantoColors.Ember, actions::onPowerOff, Modifier.weight(1f), compact = true)
            BrutalButton("QUITTER L'APP", CantoColors.Amber, actions::onExitApp, Modifier.weight(1f), compact = true)
        }
    }
}

@Composable
private fun RowScope.SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(modifier = Modifier.weight(1f)) {
        SettingsLabel(label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CantoColors.Amber,
                activeTrackColor = CantoColors.Amber,
                inactiveTrackColor = CantoColors.Secondary,
                activeTickColor = CantoColors.OnAccent,
                inactiveTickColor = CantoColors.Text.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BluetoothSection(state: BluetoothUiState, actions: SettingsActions) {
    when {
        !state.isAvailable -> SettingsText("Bluetooth non disponible sur cet appareil.")
        !state.isEnabled -> BrutalButton("ACTIVER LE BLUETOOTH", CantoColors.Teal, actions::onBluetoothEnable, Modifier.fillMaxWidth(), compact = true)
        else -> {
            if (state.speakers.isEmpty()) {
                SettingsText("Aucune enceinte. Mets l'enceinte en mode appairage puis appuie sur Rechercher.")
            }
            state.speakers.forEach { speaker ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(speaker.name, color = CantoColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                speaker.isConnected -> "Connectée"
                                speaker.isBonded -> "Appairée"
                                else -> "Nouvelle"
                            },
                            color = if (speaker.isConnected) CantoColors.Moss else CantoColors.Text.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (speaker.isConnected) {
                        BrutalButton("DÉCONNECTER", CantoColors.Secondary, { actions.onBluetoothDisconnect(speaker.address) }, Modifier.width(170.dp), CantoColors.Text, compact = true)
                    } else {
                        BrutalButton(
                            if (speaker.isBonded) "CONNECTER" else "APPAIRER",
                            CantoColors.Teal,
                            { actions.onBluetoothConnect(speaker.address) },
                            Modifier.width(170.dp),
                            compact = true
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BrutalButton(
                    if (state.isScanning) "RECHERCHE…" else "RECHERCHER",
                    CantoColors.Teal,
                    actions::onBluetoothScan,
                    Modifier.weight(1f),
                    compact = true
                )
                BrutalButton("RÉGLAGES ANDROID", CantoColors.Secondary, actions::onOpenBluetoothSettings, Modifier.weight(1f), CantoColors.Text, compact = true)
            }
        }
    }
}

private fun formatDelay(seconds: Int): String = if (seconds < 60) "$seconds S" else "${seconds / 60} MIN"

@Composable
private fun SettingsLabel(text: String) {
    Text(text, color = CantoColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
}

@Composable
private fun SettingsText(text: String) {
    Text(text, color = CantoColors.Text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Text(
        text = "×",
        color = CantoColors.Text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clickable(onClick = onClose)
            .padding(horizontal = 12.dp)
    )
}

@Composable
internal fun BrutalFrame(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(7.dp, 7.dp)
                .background(CantoColors.Shadow)
        )
        Column(
            modifier = Modifier
                .background(color)
                .border(4.dp, CantoColors.Frame),
            content = content
        )
    }
}

@Composable
internal fun BrutalButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = CantoColors.OnAccent,
    compact: Boolean = false
) {
    BrutalButtonFrame(color, onClick, modifier, if (compact) 10.dp else 14.dp, fillHeight = false) {
        Text(
            text,
            color = textColor,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrutalIconButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    BrutalButtonFrame(color, onClick, modifier.fillMaxHeight(), 8.dp, fillHeight = true) { icon() }
}

@Composable
private fun BrutalButtonFrame(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    verticalPadding: Dp,
    fillHeight: Boolean,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(5.dp, 5.dp)
                .background(CantoColors.Shadow)
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(3.dp, CantoColors.Shadow),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = verticalPadding),
            modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}
