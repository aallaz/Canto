package com.example.canto

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Palette sombre : grandes surfaces foncées, couleurs vives réservées aux boutons. */
object CantoColors {
    val Background = Color(0xFF121116)
    val Surface = Color(0xFF1D1B23)
    val Frame = Color(0xFF3B3747)
    val Shadow = Color(0xFF000000)
    val Text = Color(0xFFE6DFCB)
    val OnAccent = Color(0xFF121116)
    val Amber = Color(0xFFC9972F)
    val Teal = Color(0xFF2E8F8B)
    val Moss = Color(0xFF7A9A3C)
    val Ember = Color(0xFFC2632A)
}

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
    player: PlayerUiState?,
    settings: SettingsUiState?,
    settingsActions: SettingsActions,
    onSelectStory: (StoryFolder) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    isScreenDark: Boolean,
    onScreenOff: () -> Unit,
    onScreenWake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CantoColors.Background)
    ) {
        if (player != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                StatusBar(status, onOpenSettings, onVolumeChange, onScreenOff, Modifier.padding(start = 18.dp, end = 16.dp))
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
                onOpenSettings = onOpenSettings,
                onVolumeChange = onVolumeChange,
                onScreenOff = onScreenOff,
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
    onOpenSettings: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onScreenOff: () -> Unit,
    onSelectStory: (StoryFolder) -> Unit
) {
    if (storyFolders.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(status, onOpenSettings, onVolumeChange, onScreenOff, Modifier.padding(start = 18.dp, end = 16.dp))
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
            StatusBar(status, onOpenSettings, onVolumeChange, onScreenOff, Modifier.padding(end = 0.dp))
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
                            .border(4.dp, CantoColors.Frame)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalButton("◀", CantoColors.Moss, onPrevious, Modifier.weight(1f))
            BrutalButton(if (state.isPlaying) "▮▮" else "▶", CantoColors.Ember, onTogglePlayPause, Modifier.weight(1f))
            BrutalButton("▶", CantoColors.Moss, onNext, Modifier.weight(1f))
            BrutalButton("⌂", CantoColors.Amber, onBack, Modifier.weight(1f))
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
        Surface(modifier = modifier, color = CantoColors.Frame) {
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
                .widthIn(max = 620.dp)
                // Empêche un clic dans le panneau de fermer les réglages.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            when (stage) {
                PinStage.Create -> PinPad(
                    title = "Choisis un code parent (${AppSettings.PIN_LENGTH} chiffres)",
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
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = CantoColors.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (error.isNotEmpty()) {
                    Text(error, color = CantoColors.Ember, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "●".repeat(pin.length) + "○".repeat(AppSettings.PIN_LENGTH - pin.length),
                color = CantoColors.Amber,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            CloseButton(onClose)
        }
        listOf("123456", "7890").forEachIndexed { rowIndex, digits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                digits.forEach { digit ->
                    BrutalButton(digit.toString(), CantoColors.Frame, { press(digit) }, Modifier.weight(1f), CantoColors.Text)
                }
                if (rowIndex == 1) {
                    BrutalButton("⌫", CantoColors.Ember, { pin = pin.dropLast(1) }, Modifier.weight(2f))
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onChangePin: () -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = CantoColors.Amber,
        activeTrackColor = CantoColors.Amber,
        inactiveTrackColor = CantoColors.Frame
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel("⚙ RÉGLAGES")
            SettingsText("Batterie ${if (state.batteryLevel >= 0) "${state.batteryLevel}%" else "?"} · version ${state.update.currentVersion}")
            CloseButton(actions::onClose)
        }

        SettingsLabel("🔊 VOLUME MAX ${state.volumeLimit} / ${state.maxVolume}")
        SettingsText("Limite de la barre de volume en haut de l'écran (les boutons du téléphone ne la dépassent pas).")
        Slider(
            value = state.volumeLimit.toFloat(),
            onValueChange = { actions.onVolumeLimitChange(Math.round(it)) },
            valueRange = 1f..state.maxVolume.coerceAtLeast(2).toFloat(),
            steps = (state.maxVolume - 2).coerceAtLeast(0),
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )

        SettingsLabel("☀ LUMINOSITÉ ${(state.brightness * 100).toInt()}% (max ${(AppSettings.MAX_BRIGHTNESS * 100).toInt()}%)")
        Slider(
            value = state.brightness,
            onValueChange = actions::onBrightnessChange,
            valueRange = AppSettings.MIN_BRIGHTNESS..AppSettings.MAX_BRIGHTNESS,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )

        val delays = AppSettings.SCREEN_OFF_DELAYS
        val delayIndex = delays.indexOf(state.screenOffDelaySeconds).coerceAtLeast(0)
        SettingsLabel("🌙 ÉCRAN NOIR APRÈS ${formatDelay(delays[delayIndex])}")
        SettingsText("Sans toucher l'écran pendant ce temps, il devient noir (la lecture continue) ; un toucher le rallume.")
        Slider(
            value = delayIndex.toFloat(),
            onValueChange = { actions.onScreenOffDelayChange(delays[Math.round(it).coerceIn(0, delays.lastIndex)]) },
            valueRange = 0f..delays.lastIndex.toFloat(),
            steps = (delays.size - 2).coerceAtLeast(0),
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )

        SettingsLabel("📁 HISTOIRES")
        if (state.storiesRoots.isEmpty()) {
            SettingsText("Aucun dossier Histoires trouvé (carte SD ou stockage interne).")
        } else {
            state.storiesRoots.forEach { root ->
                SettingsText("${if (root.isRemovable) "Carte SD" else "Interne"} : ${root.dir.absolutePath}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton("RECHERCHER", CantoColors.Teal, actions::onRescan, Modifier.weight(1f))
            if (state.needsAllFilesAccess) {
                BrutalButton("AUTORISER L'ACCÈS", CantoColors.Amber, actions::onRequestAllFilesAccess, Modifier.weight(1f))
            }
        }

        SettingsLabel("📶 TRANSFERT WI-FI")
        BrutalButton(
            if (state.wifiUrl != null) "ARRÊTER LE TRANSFERT" else "DÉMARRER LE TRANSFERT",
            if (state.wifiUrl != null) CantoColors.Ember else CantoColors.Teal,
            actions::onToggleWifi,
            Modifier.fillMaxWidth()
        )
        if (state.wifiUrl != null) {
            SettingsText("Sur un ordinateur ou un téléphone du même Wi-Fi, ouvre :")
            Text(state.wifiUrl, color = CantoColors.Amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }

        BluetoothSection(state.bluetooth, actions)

        SettingsLabel("⬆ MISE À JOUR")
        val update = state.update
        SettingsText(
            when {
                update.message.isNotEmpty() -> update.message
                update.availableVersion != null -> "Nouvelle version ${update.availableVersion} disponible (installée : ${update.currentVersion})."
                else -> "Version ${update.currentVersion}."
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton("VÉRIFIER", CantoColors.Frame, { if (!update.isBusy) actions.onCheckUpdate() }, Modifier.weight(1f), CantoColors.Text)
            if (update.availableVersion != null) {
                BrutalButton(
                    if (update.isBusy) "TÉLÉCHARGEMENT…" else "INSTALLER ${update.availableVersion}",
                    CantoColors.Amber,
                    { if (!update.isBusy) actions.onInstallUpdate() },
                    Modifier.weight(1f)
                )
            }
        }

        if (state.info.isNotEmpty()) {
            Text(state.info, color = CantoColors.Ember, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton("CHANGER LE CODE", CantoColors.Frame, onChangePin, Modifier.weight(1f), CantoColors.Text)
            BrutalButton("⏻ ÉTEINDRE", CantoColors.Ember, actions::onPowerOff, Modifier.weight(1f))
            BrutalButton("QUITTER L'APP", CantoColors.Amber, actions::onExitApp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BluetoothSection(state: BluetoothUiState, actions: SettingsActions) {
    SettingsLabel("🔈 ENCEINTE BLUETOOTH")
    when {
        !state.isAvailable -> SettingsText("Bluetooth non disponible sur cet appareil.")
        !state.isEnabled -> BrutalButton("ACTIVER LE BLUETOOTH", CantoColors.Teal, actions::onBluetoothEnable, Modifier.fillMaxWidth())
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
                        BrutalButton("DÉCONNECTER", CantoColors.Frame, { actions.onBluetoothDisconnect(speaker.address) }, Modifier.width(170.dp), CantoColors.Text)
                    } else {
                        BrutalButton(
                            if (speaker.isBonded) "CONNECTER" else "APPAIRER",
                            CantoColors.Teal,
                            { actions.onBluetoothConnect(speaker.address) },
                            Modifier.width(170.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BrutalButton(
                    if (state.isScanning) "RECHERCHE…" else "RECHERCHER",
                    CantoColors.Teal,
                    actions::onBluetoothScan,
                    Modifier.weight(1f)
                )
                BrutalButton("RÉGLAGES ANDROID", CantoColors.Frame, actions::onOpenBluetoothSettings, Modifier.weight(1f), CantoColors.Text)
            }
        }
    }
}

private fun formatDelay(seconds: Int): String = if (seconds < 60) "$seconds S" else "${seconds / 60} MIN"

@Composable
private fun SettingsLabel(text: String) {
    Text(text, color = CantoColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
}

@Composable
private fun SettingsText(text: String) {
    Text(text, color = CantoColors.Text, style = MaterialTheme.typography.bodyLarge)
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
    textColor: Color = CantoColors.OnAccent
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
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = textColor
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
