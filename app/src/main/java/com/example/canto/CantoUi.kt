package com.example.canto

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
    val storiesRoots: List<StorageLocator.StoriesRoot>,
    val musicRoots: List<StorageLocator.StoriesRoot>,
    val kiosk: KioskUiState,
    val pinEnabled: Boolean,
    val needsAllFilesAccess: Boolean,
    val wifiUrl: String?,
    val bluetooth: BluetoothUiState,
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

/** Niveaux de navigation, de gauche à droite. */
enum class NavLevel { Main, Category }

data class NavigationUiState(
    val level: NavLevel,
    val category: Category,
    val stories: List<StoryFolder>,
    val music: List<StoryFolder>,
    val isScanning: Boolean,
    val storiesMessage: String,
    val musicMessage: String
)

class NavigationActions(
    val onOpenCategory: (Category) -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectStory: (StoryFolder) -> Unit,
    /** Glissement vers la gauche : menu principal > rubrique > écran noir. */
    val onSwipeForward: () -> Unit,
    /** Glissement vers la droite : retour au niveau précédent. */
    val onSwipeBack: () -> Unit
)

/** Actions de la barre du haut. */
class StatusBarActions(
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
                                onNext = onNext
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
        // Mêmes proportions que les tuiles des histoires et des albums.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 8.dp, end = 25.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            MenuTile("HISTOIRES", MenuGlyph.Stories, CantoColors.Amber, { actions.onOpenCategory(Category.Stories) }, Modifier.weight(1f))
            MenuTile("MUSIQUE", MenuGlyph.Music, CantoColors.Teal, { actions.onOpenCategory(Category.Music) }, Modifier.weight(1f))
            MenuTile("RÉGLAGES", MenuGlyph.Settings, CantoColors.Secondary, actions.onOpenSettings, Modifier.weight(1f))
        }
    }
}

private enum class MenuGlyph { Stories, Music, Settings }

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
    val color = if (glyph == MenuGlyph.Settings) CantoColors.Text else CantoColors.OnAccent
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
            MenuGlyph.Settings -> {
                val center = Offset(w / 2, h / 2)
                val radius = w * 0.26f
                repeat(8) { index ->
                    rotate(index * 45f, center) {
                        drawRect(color, Offset(center.x - w * 0.07f, center.y - radius - w * 0.14f), Size(w * 0.14f, w * 0.16f))
                    }
                }
                drawCircle(color, radius, center, style = Stroke(w * 0.13f))
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
        items(items) { story ->
            StoryTile(story = story, onClick = { actions.onSelectStory(story) })
        }
    }
}

@Composable
private fun StoryTile(story: StoryFolder, onClick: () -> Unit) {
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
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalIconButton(CantoColors.Teal, onPrevious, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Previous) }
            BrutalIconButton(CantoColors.Ember, onTogglePlayPause, Modifier.weight(1f)) {
                PlayerIcon(if (state.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play)
            }
            BrutalIconButton(CantoColors.Teal, onNext, Modifier.weight(1f)) { PlayerIcon(PlayerGlyph.Next) }
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
    Transfer("TRANSFERT WI-FI"),
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
                    SettingsText("Batterie ${if (state.batteryLevel >= 0) "${state.batteryLevel}%" else "?"} · version ${state.update.currentVersion}")
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
            SettingsPage.Transfer -> TransferSettings(state, actions)
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
        SettingsPage.Transfer to if (state.wifiUrl != null) CantoColors.Amber else CantoColors.Teal,
        SettingsPage.Speaker to CantoColors.Teal,
        SettingsPage.Update to if (update.availableVersion != null) CantoColors.Amber else CantoColors.Teal,
        SettingsPage.Folders to if (state.needsAllFilesAccess) CantoColors.Amber else CantoColors.Teal,
        SettingsPage.CodeKiosk to CantoColors.Teal
    )
    pages.chunked(3).forEach { line ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            line.forEach { (target, color) ->
                val label = when {
                    target == SettingsPage.Transfer && state.wifiUrl != null -> "TRANSFERT : ACTIF"
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
}

@Composable
private fun TransferSettings(state: SettingsUiState, actions: SettingsActions) {
    if (state.wifiUrl != null) {
        SettingsText("Sur un ordinateur ou un téléphone du même Wi-Fi, ouvre :")
        Text(state.wifiUrl, color = CantoColors.Amber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        SettingsText("Le code parent est demandé sur la page.")
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
