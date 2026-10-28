package com.example.canto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SCREEN_FADE_OUT_MS = 1500
const val SCREEN_FADE_IN_MS = 500

data class PlayerUiState(
    val story: StoryFolder,
    val currentAudioName: String,
    val currentIndex: Int,
    val audioCount: Int,
    val isPlaying: Boolean,
    val info: PlayerInfo = PlayerInfo.Track,
    /** Position dans la piste en cours, en millisecondes (relue par le compteur quand il affiche un temps). */
    val position: () -> Long = { 0L },
    /** Durée de chaque piste en millisecondes (vide tant qu'elles ne sont pas lues, -1 si inconnue). */
    val trackDurationsMs: List<Long> = emptyList()
)

/** Ce qu'affiche le compteur du lecteur ; un appui passe au suivant. */
enum class PlayerInfo { Track, TrackTime, AlbumTime }

data class UpdateUiState(
    val currentVersion: String = "",
    val availableVersion: String? = null,
    val isBusy: Boolean = false,
    val message: String = ""
)

data class SettingsUiState(
    val batteryLevel: Int,
    /** Place occupée sur le stockage des histoires, en %, ou -1 si inconnue. */
    val storageUsedPercent: Int,
    val brightness: Float,
    val volume: Int,
    val volumeLimit: Int,
    val screenOffDelaySeconds: Int,
    val normalizeVolume: Boolean,
    val maxVolume: Int,
    val storiesRoots: List<StorageLocator.StoriesRoot>,
    val musicRoots: List<StorageLocator.StoriesRoot>,
    val kiosk: KioskUiState,
    val pinEnabled: Boolean,
    val needsAllFilesAccess: Boolean,
    val wifiUrl: String?,
    val bluetooth: BluetoothUiState,
    val wifi: WifiUiState,
    val update: UpdateUiState,
    val info: String
)

interface SettingsActions {
    fun hasPin(): Boolean
    fun isPinEnabled(): Boolean
    fun onTogglePin()
    fun onRemoveDeviceOwner()
    fun checkPin(pin: String): Boolean
    fun savePin(pin: String)
    fun onBrightnessChange(value: Float)
    fun onVolumeChange(value: Int)
    fun onVolumeLimitChange(value: Int)
    fun onScreenOffDelayChange(seconds: Int)
    fun onToggleNormalize()
    fun onRescan()
    fun onRequestAllFilesAccess()
    fun onToggleWifi()
    /** Allume ou coupe le Bluetooth. */
    fun onBluetoothToggle()
    fun onBluetoothScan()
    fun onBluetoothConnect(address: String)
    fun onBluetoothDisconnect(address: String)
    fun onOpenBluetoothSettings()
    fun onWifiToggle()
    fun onWifiScan()
    /** [password] null : réseau ouvert ou déjà enregistré. */
    fun onWifiConnect(ssid: String, password: String?)
    fun onCheckUpdate()
    fun onInstallUpdate()
    fun onPowerOff()
    fun onExitApp()
    fun onClose()
}

/** Niveaux de navigation, de gauche à droite. */
enum class NavLevel { Main, Category }

data class NavigationUiState(
    val level: NavLevel,
    val category: Category,
    val stories: List<StoryFolder>,
    val music: List<StoryFolder>,
    val isScanning: Boolean,
    val storiesMessage: String,
    val musicMessage: String,
    /** Histoire ou album chargé dans le lecteur : sa tuile est encadrée. */
    val currentStoryPath: String? = null
)

class NavigationActions(
    val onOpenCategory: (Category) -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectStory: (StoryFolder) -> Unit,
    /** Glissement vers la gauche : menu principal > rubrique > lecteur (histoire en cours). */
    val onSwipeForward: () -> Unit,
    /** Glissement vers la droite : retour au niveau précédent. */
    val onSwipeBack: () -> Unit
)

/** Actions de la barre du haut. */
class StatusBarActions(
    val onVolumeChange: (Int) -> Unit,
    /** Bouton « lecture en cours » : revenir au lecteur. */
    val onOpenPlayer: () -> Unit,
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
    nav: NavigationUiState,
    navActions: NavigationActions,
    status: StatusBarState,
    statusActions: StatusBarActions,
    player: PlayerUiState?,
    settings: SettingsUiState?,
    settingsActions: SettingsActions,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    /** Avance (ms positif) ou recul dans la piste, par appui long sur suivant / précédent. */
    onSeek: (Int) -> Unit,
    /** Appui sur le compteur du lecteur : affichage suivant. */
    onCycleInfo: () -> Unit,
    isScreenDark: Boolean,
    onScreenWake: () -> Unit
) {
    // 0 = menu principal, 1 = rubrique, 2 = lecteur : sert à choisir le sens de l'animation.
    val depth = when {
        player != null -> 2
        nav.level == NavLevel.Category -> 1
        else -> 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CantoColors.Background)
            .horizontalSwipe(onSwipeLeft = navActions.onSwipeForward, onSwipeRight = navActions.onSwipeBack)
    ) {
        AnimatedContent(
            targetState = depth,
            transitionSpec = {
                // On avance vers la droite du parcours : le nouvel écran arrive par la droite.
                val forward = targetState > initialState
                (slideInHorizontally(tween(NAV_ANIMATION_MS)) { width -> if (forward) width else -width } togetherWith
                    slideOutHorizontally(tween(NAV_ANIMATION_MS)) { width -> if (forward) -width else width })
            },
            label = "navigation"
        ) { level ->
            when (level) {
                2 -> if (player != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        StatusBar(status, statusActions, Modifier.padding(start = 18.dp, end = 16.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            PlayerScreen(
                                state = player,
                                onBack = onBack,
                                onTogglePlayPause = onTogglePlayPause,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onSeek = onSeek,
                                onCycleInfo = onCycleInfo
                            )
                        }
                    }
                }
                1 -> CategoryScreen(nav, navActions)
                else -> MainMenu(status, statusActions, navActions)
            }
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

private const val NAV_ANIMATION_MS = 350

/** Glissement horizontal franc (au moins 80 dp) ; les curseurs gardent leurs propres gestes. */
private fun Modifier.horizontalSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier =
    pointerInput(Unit) {
        val threshold = 80.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -threshold -> onSwipeLeft()
                    total > threshold -> onSwipeRight()
                }
            },
            onHorizontalDrag = { change, amount ->
                total += amount
                change.consume()
            }
        )
    }

/** Menu principal : barre du haut et une tuile par rubrique. */
@Composable
private fun MainMenu(status: StatusBarState, statusActions: StatusBarActions, actions: NavigationActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        StatusBar(status, statusActions, Modifier.padding(start = 18.dp, end = 16.dp))
        // Deux tuiles centrées, de la taille des tuiles des rubriques (3 par ligne).
        // Les réglages s'ouvrent par un glissement de gauche à droite.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 8.dp, end = 25.dp, bottom = 32.dp)
        ) {
            val tileWidth = (maxWidth - 44.dp) / 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)
            ) {
                MenuTile("HISTOIRES", MenuGlyph.Stories, CantoColors.Amber, { actions.onOpenCategory(Category.Stories) }, Modifier.width(tileWidth))
                MenuTile("MUSIQUE", MenuGlyph.Music, CantoColors.Teal, { actions.onOpenCategory(Category.Music) }, Modifier.width(tileWidth))
            }
        }
    }
}

private enum class MenuGlyph { Stories, Music }

/** Proportions communes à toutes les tuiles (menu, histoires, albums). */
private const val TILE_ASPECT_RATIO = 0.85f

/** Titre de tuile : toujours la place pour 2 lignes, coupé au-delà. */
@Composable
private fun TileTitle(text: String) {
    val style = MaterialTheme.typography.titleSmall
    val twoLines = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }
    Text(
        text = text,
        color = CantoColors.Text,
        style = style,
        fontWeight = FontWeight.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .height(twoLines + 4.dp)
    )
}

@Composable
private fun MenuTile(label: String, glyph: MenuGlyph, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(TILE_ASPECT_RATIO)) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                MenuIcon(glyph, Modifier.fillMaxSize(0.5f))
            }
            TileTitle(label)
        }
    }
}

@Composable
private fun MenuIcon(glyph: MenuGlyph, modifier: Modifier = Modifier) {
    val color = CantoColors.OnAccent
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.07f
        when (glyph) {
            MenuGlyph.Stories -> {
                // Livre ouvert.
                drawPath(Path().apply {
                    moveTo(w * 0.5f, h * 0.28f); lineTo(w * 0.08f, h * 0.2f); lineTo(w * 0.08f, h * 0.78f)
                    lineTo(w * 0.5f, h * 0.86f); lineTo(w * 0.92f, h * 0.78f); lineTo(w * 0.92f, h * 0.2f); close()
                }, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(color, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.86f), stroke)
            }
            MenuGlyph.Music -> {
                // Double croche.
                drawCircle(color, w * 0.13f, Offset(w * 0.27f, h * 0.76f))
                drawCircle(color, w * 0.13f, Offset(w * 0.73f, h * 0.66f))
                drawLine(color, Offset(w * 0.37f, h * 0.76f), Offset(w * 0.37f, h * 0.22f), stroke)
                drawLine(color, Offset(w * 0.83f, h * 0.66f), Offset(w * 0.83f, h * 0.12f), stroke)
                drawPath(Path().apply {
                    moveTo(w * 0.37f - stroke / 2, h * 0.22f); lineTo(w * 0.83f + stroke / 2, h * 0.12f)
                    lineTo(w * 0.83f + stroke / 2, h * 0.24f); lineTo(w * 0.37f - stroke / 2, h * 0.34f); close()
                }, color)
            }
        }
    }
}

/** Rubrique : une tuile par histoire ou album, sans barre du haut ; retour au menu par glissement. */
@Composable
private fun CategoryScreen(nav: NavigationUiState, actions: NavigationActions) {
    val items = if (nav.category == Category.Music) nav.music else nav.stories
    val message = if (nav.category == Category.Music) nav.musicMessage else nav.storiesMessage

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        // Marge en bas et à droite pour que l'ombre des tuiles ne touche pas le bord.
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 25.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (nav.isScanning) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.7f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CantoColors.Amber)
                    }
                } else {
                    Text(
                        message.ifBlank { "Rien ici pour l'instant." },
                        color = CantoColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        items(items, key = { it.path }) { story ->
            StoryTile(story = story, isCurrent = story.path == nav.currentStoryPath, onClick = { actions.onSelectStory(story) })
        }
    }
}

@Composable
private fun StoryTile(story: StoryFolder, isCurrent: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TILE_ASPECT_RATIO)
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
                .border(4.dp, if (isCurrent) CantoColors.Amber else CantoColors.Frame)
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
            TileTitle(story.displayTitle().uppercase())
        }
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onCycleInfo: () -> Unit
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
                    modifier = Modifier.fillMaxSize(),
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
                    // La bande du bas reste libre pour le compteur.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
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
                PlayerCounter(
                    state = state,
                    onClick = onCycleInfo,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }

        // Grandes icônes dessinées, lisibles par un enfant.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalHoldButton(CantoColors.Teal, onPrevious, { onSeek(-SEEK_STEP_MS) }, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Previous) }
            BrutalIconButton(CantoColors.Ember, onTogglePlayPause, Modifier.weight(1f)) {
                PlayerIcon(if (state.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play)
            }
            BrutalHoldButton(CantoColors.Teal, onNext, { onSeek(SEEK_STEP_MS) }, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Next) }
            BrutalIconButton(CantoColors.Amber, onBack, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Home) }
        }
    }
}

/**
 * Compteur en bas à droite du lecteur. Un appui fait défiler : numéro de piste,
 * temps de la piste, temps de tout l'album.
 */
@Composable
private fun PlayerCounter(state: PlayerUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val durations = state.trackDurationsMs
    // Position relue deux fois par seconde, seulement quand un temps est affiché.
    val readPosition by rememberUpdatedState(state.position)
    var positionMs by remember { mutableStateOf(state.position()) }
    if (state.info != PlayerInfo.Track) {
        LaunchedEffect(state.info, state.currentIndex) {
            while (true) {
                positionMs = readPosition()
                delay(POSITION_REFRESH_MS)
            }
        }
    }
    val (caption, value) = when (state.info) {
        PlayerInfo.Track -> "PISTE" to "${state.currentIndex + 1} / ${state.audioCount}"
        PlayerInfo.TrackTime -> {
            val total = durations.getOrNull(state.currentIndex) ?: -1L
            "CETTE PISTE" to "${formatDuration(positionMs)} / ${formatDuration(total)}"
        }
        PlayerInfo.AlbumTime -> {
            val known = durations.size == state.audioCount && durations.all { it >= 0 }
            val elapsed = if (known) durations.take(state.currentIndex).sum() + positionMs else -1L
            "EN TOUT" to "${formatDuration(elapsed)} / ${formatDuration(if (known) durations.sum() else -1L)}"
        }
    }
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            // Zone de toucher plus grande que le texte.
            .padding(start = 16.dp, top = 12.dp)
    ) {
        Text(
            caption,
            color = CantoColors.Text.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            color = CantoColors.Text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
    }
}

private const val POSITION_REFRESH_MS = 500L

/** 75 000 ms -> "1:15", 3 723 000 ms -> "1:02:03" ; durée inconnue -> "--:--". */
private fun formatDuration(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
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
    // Décodée hors du fil d'affichage, réduite et gardée en mémoire : le défilement ne saccade plus.
    val bitmap by produceState(initialValue = path?.let(CoverCache::get), path) {
        if (value == null && path != null) value = withContext(Dispatchers.IO) { CoverCache.load(path) }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Image de l’histoire",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            colorFilter = CantoColors.CoverDuotone?.let { (dark, light) -> duotone(dark, light) }
        )
    } else {
        Surface(modifier = modifier, color = CantoColors.Secondary) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("♪", color = CantoColors.Text, style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

/** Pochettes réduites (au plus [MAX_SIZE] px) et gardées en mémoire pour un défilement fluide. */
private object CoverCache {
    private const val MAX_SIZE = 480
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
    }

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun load(path: String): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()?.also { cache.put(path, it) }
    }.getOrNull()
}

/**
 * Bichromie : la luminosité de chaque pixel est projetée entre deux couleurs
 * (ombres = [dark], lumières = [light]). Aucun fichier à préparer : l'effet est calculé à l'affichage.
 */
private fun duotone(dark: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float): FloatArray {
        val k = l - d
        return floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, d * 255f)
    }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f)
        )
    )
}

private enum class PinStage { Create, Confirm, Enter, Unlocked }

@Composable
private fun SettingsOverlay(state: SettingsUiState, actions: SettingsActions) {
    var stage by remember {
        mutableStateOf(
            when {
                !actions.isPinEnabled() -> PinStage.Unlocked
                actions.hasPin() -> PinStage.Enter
                else -> PinStage.Create
            }
        )
    }
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

/** Sous-menus des réglages ; null = page d'accueil (boutons). */
private enum class SettingsPage(val title: String) {
    SoundScreen("SON ET ÉCRAN"),
    Wifi("WI-FI ET TRANSFERT"),
    Speaker("ENCEINTE"),
    Update("MISE À JOUR"),
    Folders("DOSSIERS"),
    CodeKiosk("CODE ET KIOSQUE")
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onChangePin: () -> Unit
) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val current = page
            if (current == null) {
                SettingsLabel("RÉGLAGES")
                Box(modifier = Modifier.weight(1f)) {
                    SettingsText(
                        "Batterie ${if (state.batteryLevel >= 0) "${state.batteryLevel}%" else "?"} · " +
                            "Stockage ${if (state.storageUsedPercent >= 0) "${state.storageUsedPercent}%" else "?"} · " +
                            "version ${state.update.currentVersion}"
                    )
                }
            } else {
                BrutalButton("‹ RETOUR", CantoColors.Secondary, { page = null }, Modifier.width(130.dp), CantoColors.Text, compact = true)
                Box(modifier = Modifier.weight(1f)) { SettingsLabel(current.title) }
            }
            CloseButton(actions::onClose)
        }

        when (page) {
            null -> SettingsHome(state, onOpen = { page = it }, actions = actions)
            SettingsPage.SoundScreen -> SoundScreenSettings(state, actions)
            SettingsPage.Wifi -> WifiSettings(state, actions)
            SettingsPage.Speaker -> BluetoothSection(state.bluetooth, actions)
            SettingsPage.Update -> UpdateSettings(state.update, actions)
            SettingsPage.Folders -> FolderSettings(state, actions)
            SettingsPage.CodeKiosk -> CodeKioskSettings(state, actions, onChangePin)
        }

        if (state.info.isNotEmpty()) {
            Text(state.info, color = CantoColors.Warning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Page d'accueil : un bouton par sous-menu, puis éteindre et quitter. */
@Composable
private fun SettingsHome(state: SettingsUiState, onOpen: (SettingsPage) -> Unit, actions: SettingsActions) {
    val update = state.update
    val pages = listOf(
        SettingsPage.SoundScreen to CantoColors.Teal,
        SettingsPage.Wifi to when {
            state.wifiUrl != null -> CantoColors.Amber
            state.wifi.enabled -> CantoColors.Teal
            else -> CantoColors.Secondary
        },
        SettingsPage.Speaker to if (state.bluetooth.isEnabled) CantoColors.Teal else CantoColors.Secondary,
        SettingsPage.Update to if (update.availableVersion != null) CantoColors.Amber else CantoColors.Teal,
        SettingsPage.Folders to if (state.needsAllFilesAccess) CantoColors.Amber else CantoColors.Teal,
        SettingsPage.CodeKiosk to CantoColors.Teal
    )
    pages.chunked(3).forEach { line ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            line.forEach { (target, color) ->
                val label = when {
                    target == SettingsPage.Wifi && state.wifiUrl != null -> "TRANSFERT : ACTIF"
                    target == SettingsPage.Wifi && !state.wifi.enabled -> "WI-FI : DÉSACTIVÉ"
                    target == SettingsPage.Speaker && state.bluetooth.isAvailable && !state.bluetooth.isEnabled -> "BLUETOOTH : DÉSACTIVÉ"
                    target == SettingsPage.Update && update.availableVersion != null -> "MISE À JOUR : ${update.availableVersion}"
                    else -> target.title
                }
                BrutalButton(label, color, { onOpen(target) }, Modifier.weight(1f))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BrutalButton("ÉTEINDRE", CantoColors.Ember, actions::onPowerOff, Modifier.weight(1f), compact = true)
        BrutalButton("QUITTER VERS ANDROID", CantoColors.Secondary, actions::onExitApp, Modifier.weight(1f), CantoColors.Text, compact = true)
    }
}

@Composable
private fun SoundScreenSettings(state: SettingsUiState, actions: SettingsActions) {
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
        label = "ÉCRAN NOIR APRÈS ${formatDelay(delays[delayIndex])}",
        value = delayIndex.toFloat(),
        onValueChange = { actions.onScreenOffDelayChange(delays[Math.round(it).coerceIn(0, delays.lastIndex)]) },
        range = 0f..delays.lastIndex.toFloat(),
        steps = (delays.size - 2).coerceAtLeast(0)
    )
    if (VolumeNormalizer.isAvailable) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SettingsText("Normaliser : les histoires et chansons enregistrées trop fort ou trop doucement sont ramenées au même niveau.")
            }
            BrutalButton(
                if (state.normalizeVolume) "NORMALISER : OUI" else "NORMALISER : NON",
                if (state.normalizeVolume) CantoColors.Teal else CantoColors.Secondary,
                actions::onToggleNormalize,
                Modifier.width(230.dp),
                if (state.normalizeVolume) CantoColors.OnAccent else CantoColors.Text,
                compact = true
            )
        }
    }
}

/** Wi-Fi : activer, choisir un réseau (mot de passe si besoin), puis transfert depuis un navigateur. */
@Composable
private fun WifiSettings(state: SettingsUiState, actions: SettingsActions) {
    val wifi = state.wifi
    var passwordFor by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Saisie du mot de passe seule en haut de la page : le clavier cache le bas de l'écran.
    val target = passwordFor
    if (target != null) {
        SettingsText("Mot de passe du réseau « $target » :")
        BasicTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = CantoColors.Text),
            cursorBrush = SolidColor(CantoColors.Amber),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .background(CantoColors.Background)
                .border(3.dp, CantoColors.Amber)
                .padding(12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BrutalButton("ANNULER", CantoColors.Secondary, { passwordFor = null }, Modifier.weight(1f), CantoColors.Text, compact = true)
            BrutalButton(
                if (showPassword) "MASQUER" else "AFFICHER",
                CantoColors.Secondary,
                { showPassword = !showPassword },
                Modifier.weight(1f),
                CantoColors.Text,
                compact = true
            )
            BrutalButton(
                "SE CONNECTER",
                if (password.length >= 8) CantoColors.Teal else CantoColors.Secondary,
                {
                    if (password.length >= 8) {
                        actions.onWifiConnect(target, password)
                        passwordFor = null
                    }
                },
                Modifier.weight(1f),
                compact = true
            )
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BrutalButton(
            if (wifi.enabled) "WI-FI : ACTIVÉ" else "WI-FI : DÉSACTIVÉ",
            if (wifi.enabled) CantoColors.Teal else CantoColors.Secondary,
            actions::onWifiToggle,
            Modifier.weight(1f),
            if (wifi.enabled) CantoColors.OnAccent else CantoColors.Text,
            compact = true
        )
        if (wifi.enabled) {
            BrutalButton(
                if (wifi.scanning) "RECHERCHE…" else "RECHERCHER",
                CantoColors.Teal,
                actions::onWifiScan,
                Modifier.weight(1f),
                compact = true
            )
        }
    }

    if (!wifi.enabled) {
        SettingsText("Wi-Fi coupé : la boîte fonctionne sans, il ne sert qu'aux transferts et aux mises à jour.")
        return
    }

    SettingsText(wifi.connectedSsid?.let { "Connecté à « $it »." } ?: "Non connecté : choisis un réseau.")
    if (wifi.networks.isEmpty()) {
        SettingsText(if (wifi.scanning) "Recherche des réseaux…" else "Aucun réseau dans la liste : appuie sur Rechercher.")
    }
    wifi.networks.take(MAX_WIFI_NETWORKS).forEach { network ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(network.ssid, color = CantoColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Signal ${"▮".repeat(network.level.coerceAtLeast(1))}${"▯".repeat(4 - network.level.coerceAtLeast(1))} · " +
                        when {
                            network.connected -> "connecté"
                            network.saved -> "enregistré"
                            network.secured -> "protégé"
                            else -> "ouvert"
                        },
                    color = if (network.connected) CantoColors.Moss else CantoColors.Text.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (network.connected) {
                BrutalButton("CONNECTÉ", CantoColors.Secondary, {}, Modifier.width(170.dp), CantoColors.Text, compact = true)
            } else {
                BrutalButton(
                    "CONNECTER",
                    CantoColors.Teal,
                    {
                        if (network.secured && !network.saved) {
                            password = ""
                            showPassword = false
                            passwordFor = network.ssid
                        } else {
                            actions.onWifiConnect(network.ssid, null)
                        }
                    },
                    Modifier.width(170.dp),
                    compact = true
                )
            }
        }
    }

    SettingsLabel("TRANSFERT")
    if (state.wifiUrl != null) {
        SettingsText("Sur un ordinateur ou un téléphone du même Wi-Fi, ouvre :")
        Text(state.wifiUrl, color = CantoColors.Amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    } else {
        SettingsText("Ajoute, télécharge ou supprime des histoires et des albums depuis un navigateur sur le même Wi-Fi.")
    }
    BrutalButton(
        if (state.wifiUrl != null) "ARRÊTER LE TRANSFERT" else "DÉMARRER LE TRANSFERT",
        if (state.wifiUrl != null) CantoColors.Ember else CantoColors.Teal,
        actions::onToggleWifi,
        Modifier.fillMaxWidth(),
        compact = true
    )
}

private const val MAX_WIFI_NETWORKS = 12

@Composable
private fun UpdateSettings(update: UpdateUiState, actions: SettingsActions) {
    SettingsText(
        if (update.availableVersion != null) "Version ${update.currentVersion} installée, ${update.availableVersion} disponible."
        else "Version ${update.currentVersion} installée."
    )
    if (update.message.isNotEmpty()) SettingsText(update.message)
    BrutalButton(
        when {
            update.isBusy -> "TÉLÉCHARGEMENT…"
            update.availableVersion != null -> "INSTALLER ${update.availableVersion}"
            else -> "RECHERCHER UNE MISE À JOUR"
        },
        if (update.availableVersion != null) CantoColors.Amber else CantoColors.Teal,
        {
            when {
                update.isBusy -> Unit
                update.availableVersion != null -> actions.onInstallUpdate()
                else -> actions.onCheckUpdate()
            }
        },
        Modifier.fillMaxWidth(),
        compact = true
    )
}

@Composable
private fun FolderSettings(state: SettingsUiState, actions: SettingsActions) {
    if (state.storiesRoots.isEmpty() && state.musicRoots.isEmpty()) {
        SettingsText("Aucun dossier Histoires ni Musique trouvé.")
    }
    (state.storiesRoots + state.musicRoots).forEach { root ->
        SettingsText("${if (root.isRemovable) "Carte SD" else "Interne"} : ${root.dir.absolutePath}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.needsAllFilesAccess) {
            BrutalButton("AUTORISER L'ACCÈS", CantoColors.Amber, actions::onRequestAllFilesAccess, Modifier.weight(1f), compact = true)
        }
        BrutalButton("RECHERCHER", CantoColors.Teal, actions::onRescan, Modifier.weight(1f), compact = true)
    }
}

@Composable
private fun CodeKioskSettings(state: SettingsUiState, actions: SettingsActions, onChangePin: () -> Unit) {
    var confirmRemoveOwner by remember { mutableStateOf(false) }
    SettingsLabel("CODE PARENT")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BrutalButton(
            if (state.pinEnabled) "CODE : ACTIVÉ" else "CODE : DÉSACTIVÉ",
            if (state.pinEnabled) CantoColors.Secondary else CantoColors.Ember,
            {
                actions.onTogglePin()
                if (!state.pinEnabled && !actions.hasPin()) onChangePin()
            },
            Modifier.weight(1f),
            if (state.pinEnabled) CantoColors.Text else CantoColors.OnAccent,
            compact = true
        )
        BrutalButton("CHANGER LE CODE", CantoColors.Secondary, onChangePin, Modifier.weight(1f), CantoColors.Text, compact = true)
    }
    SettingsLabel("KIOSQUE")
    SettingsText(
        when {
            !state.kiosk.isOwner -> "Simple. Propriétaire : ${Kiosk.ADB_ENABLE}"
            state.kiosk.keyguardDisabled -> "Propriétaire, sans verrouillage Android."
            else -> "Propriétaire (un code Android empêche de retirer le verrouillage)."
        }
    )
    if (state.kiosk.isOwner) {
        BrutalButton(
            if (confirmRemoveOwner) "CONFIRMER : RETIRER LE KIOSQUE ?" else "RETIRER KIOSQUE",
            CantoColors.Ember,
            {
                if (confirmRemoveOwner) actions.onRemoveDeviceOwner()
                confirmRemoveOwner = !confirmRemoveOwner
            },
            Modifier.fillMaxWidth(),
            compact = true
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        !state.isEnabled -> {
            BrutalButton("BLUETOOTH : DÉSACTIVÉ", CantoColors.Secondary, actions::onBluetoothToggle, Modifier.fillMaxWidth(), CantoColors.Text, compact = true)
            SettingsText("Bluetooth coupé : il ne sert qu'aux enceintes. Le couper économise la batterie.")
        }
        else -> {
            BrutalButton("BLUETOOTH : ACTIVÉ", CantoColors.Teal, actions::onBluetoothToggle, Modifier.fillMaxWidth(), compact = true)
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
                BrutalButton("ASSOCIER (ANDROID)", CantoColors.Secondary, actions::onOpenBluetoothSettings, Modifier.weight(1f), CantoColors.Text, compact = true)
            }
            // En mode kiosque, Android ne peut pas afficher la fenêtre du code d'association.
            SettingsText(
                "Voiture, téléphone ou appareil à code : « Associer (Android) », rester sur l'écran Bluetooth, " +
                    "lancer l'association depuis l'autre appareil, puis valider le code. Bouton Accueil pour revenir."
            )
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

/**
 * Bouton du lecteur : toucher = action ([onClick]), appui long = [onHold] répété tant que le doigt
 * reste posé (avance ou recul rapide dans la piste).
 */
@Composable
private fun BrutalHoldButton(
    color: Color,
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    // Actions à jour sans relancer la détection du geste (un appui long en cours serait interrompu).
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold by rememberUpdatedState(onHold)
    Box(modifier = modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(5.dp, 5.dp)
                .background(CantoColors.Shadow)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) lerp(color, CantoColors.Shadow, 0.25f) else color)
                .border(3.dp, CantoColors.Shadow)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true
                        var held = false
                        val repeat = scope.launch {
                            delay(HOLD_DELAY_MS)
                            held = true
                            while (true) {
                                currentOnHold()
                                delay(HOLD_REPEAT_MS)
                            }
                        }
                        val released = tryAwaitRelease()
                        repeat.cancel()
                        pressed = false
                        if (released && !held) currentOnClick()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

/** Appui long sur précédent / suivant : 5 s de piste toutes les 200 ms. */
private const val SEEK_STEP_MS = 5000
private const val HOLD_DELAY_MS = 450L
private const val HOLD_REPEAT_MS = 200L

@Composable
private fun BrutalIconButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    BrutalButtonFrame(color, onClick, modifier.fillMaxHeight(), 4.dp, fillHeight = true) { icon() }
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
