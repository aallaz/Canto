package com.example.canto

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Xml
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import java.io.File
import org.xmlpull.v1.XmlPullParser

data class StoryFolder(
    val name: String,
    val path: String,
    val audioCount: Int,
    val coverPath: String?,
    val title: String?,
    val trackTitles: List<String>
)

class MainActivity : ComponentActivity() {
    private val storyFoldersState: MutableState<List<StoryFolder>> = mutableStateOf(emptyList())
    private val isScanningState: MutableState<Boolean> = mutableStateOf(true)
    private val messageState: MutableState<String> = mutableStateOf("Recherche des histoires…")
    private val selectedStoryState: MutableState<StoryFolder?> = mutableStateOf(null)
    private val audioFilesState: MutableState<List<File>> = mutableStateOf(emptyList())
    private val currentAudioNameState: MutableState<String> = mutableStateOf("")
    private val currentIndexState: MutableState<Int> = mutableStateOf(0)
    private val isPlayingState: MutableState<Boolean> = mutableStateOf(false)
    private val batteryLevelState: MutableState<Int> = mutableStateOf(-1)
    private val brightnessState: MutableState<Float> = mutableStateOf(0.8f)

    private var mediaPlayer: MediaPlayer? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanStoryFolders()
        } else {
            messageState.value = "L'accès au stockage est nécessaire pour lire les histoires."
            isScanningState.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(brightnessState.value)
        enterKioskMode()
        updateBatteryLevel()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(
                        storyFolders = storyFoldersState.value,
                        selectedStory = selectedStoryState.value,
                        isScanning = isScanningState.value,
                        message = messageState.value,
                        currentAudioName = currentAudioNameState.value,
                        currentIndex = currentIndexState.value,
                        audioCount = audioFilesState.value.size,
                        isPlaying = isPlayingState.value,
                        batteryLevel = batteryLevelState.value,
                        brightness = brightnessState.value,
                        onSelectStory = ::selectStory,
                        onBack = ::backToGallery,
                        onTogglePlayPause = ::togglePlayPause,
                        onPrevious = ::playPrevious,
                        onNext = ::playNext,
                        onBrightnessChange = ::setBrightness,
                        onExitApp = ::exitApp
                    )
                }
            }
        }

        if (hasStoragePermission()) {
            scanStoryFolders()
        } else {
            requestStoragePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        enterKioskMode()
        updateBatteryLevel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterKioskMode()
    }

    @Deprecated("Back is intentionally disabled for kiosk mode.")
    override fun onBackPressed() {
        // Locked on purpose. Use settings > exit with the code.
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    private fun scanStoryFolders() {
        isScanningState.value = true
        messageState.value = "Recherche des histoires…"

        val roots = listOf(
            File("/sdcard/Histoires"),
            File(Environment.getExternalStorageDirectory(), "Histoires"),
            File("/storage/emulated/0/Histoires")
        )

        val existingRoot = roots.firstOrNull { it.exists() && it.isDirectory }
        if (existingRoot == null) {
            messageState.value = "Aucun dossier Histoires trouvé. Vérifie l’emplacement : /sdcard/Histoires"
            isScanningState.value = false
            storyFoldersState.value = emptyList()
            return
        }

        val folders = existingRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { folder ->
                val metadata = readNfoMetadata(folder)
                val coverPath = findCoverPath(folder, metadata.posterPath)
                val audioFiles = folder.listFiles()
                    ?.filter { it.isFile && isAudioFile(it.name) }
                    .orEmpty()

                if (audioFiles.isEmpty() && coverPath == null) {
                    null
                } else {
                    StoryFolder(
                        name = folder.name,
                        path = folder.absolutePath,
                        audioCount = audioFiles.size,
                        coverPath = coverPath,
                        title = metadata.title,
                        trackTitles = metadata.trackTitles
                    )
                }
            }
            .orEmpty()

        storyFoldersState.value = folders
        messageState.value = if (folders.isEmpty()) {
            "Aucun dossier d’histoire n’a été trouvé dans ${existingRoot.absolutePath}."
        } else {
            ""
        }
        isScanningState.value = false
    }

    private fun selectStory(story: StoryFolder) {
        selectedStoryState.value = story
        val folder = File(story.path)
        val audioFiles = folder.listFiles()
            ?.filter { it.isFile && isAudioFile(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

        audioFilesState.value = audioFiles
        currentIndexState.value = 0
        if (audioFiles.isNotEmpty()) {
            currentAudioNameState.value = displayTrackName(story, audioFiles.first(), 0)
            playTrack(audioFiles.first())
        } else {
            currentAudioNameState.value = "Aucun fichier audio"
            isPlayingState.value = false
        }
    }

    private fun backToGallery() {
        selectedStoryState.value = null
        audioFilesState.value = emptyList()
        currentAudioNameState.value = ""
        currentIndexState.value = 0
        isPlayingState.value = false
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun togglePlayPause() {
        val files = audioFilesState.value
        if (files.isEmpty()) return
        if (mediaPlayer == null) {
            playTrack(files[currentIndexState.value])
            return
        }
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer?.pause()
            isPlayingState.value = false
        } else {
            mediaPlayer?.start()
            isPlayingState.value = true
        }
    }

    private fun playPrevious() {
        val files = audioFilesState.value
        if (files.isEmpty()) return
        val nextIndex = if (currentIndexState.value > 0) currentIndexState.value - 1 else files.lastIndex
        currentIndexState.value = nextIndex
        playTrack(files[nextIndex])
    }

    private fun playNext() {
        val files = audioFilesState.value
        if (files.isEmpty()) return
        if (currentIndexState.value >= files.lastIndex) {
            currentIndexState.value = files.lastIndex
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
            isPlayingState.value = false
            selectedStoryState.value?.let { story ->
                currentAudioNameState.value = displayTrackName(story, files.last(), files.lastIndex)
            }
            return
        }
        val nextIndex = currentIndexState.value + 1
        currentIndexState.value = nextIndex
        playTrack(files[nextIndex])
    }

    private fun setBrightness(value: Float) {
        val boundedValue = value.coerceIn(0.05f, 1f)
        brightnessState.value = boundedValue
        applyBrightness(boundedValue)
    }

    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(0.05f, 1f)
        }
    }

    private fun updateBatteryLevel() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        batteryLevelState.value = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    private fun enterKioskMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        runCatching { startLockTask() }
    }

    private fun exitApp() {
        runCatching { stopLockTask() }
        mediaPlayer?.release()
        mediaPlayer = null
        finishAndRemoveTask()
    }

    private fun playTrack(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                playNext()
            }
            prepare()
            start()
        }
        selectedStoryState.value?.let { story ->
            currentAudioNameState.value = displayTrackName(story, file, currentIndexState.value)
        } ?: run {
            currentAudioNameState.value = cleanDisplayName(file.nameWithoutExtension)
        }
        isPlayingState.value = true
    }

    private fun isAudioFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".ogg")
    }

    private fun findCoverPath(folder: File, posterPath: String?): String? {
        val localCover = folder.listFiles()
            ?.firstOrNull { file ->
                file.isFile &&
                    file.extension.lowercase() in COVER_EXTENSIONS &&
                    file.nameWithoutExtension.lowercase() in COVER_NAMES
            }
            ?.absolutePath

        if (localCover != null) return localCover

        return posterPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }
            ?.absolutePath
    }

    private fun readNfoMetadata(folder: File): NfoMetadata {
        val nfoFile = folder.listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("nfo", ignoreCase = true) }
            ?: return NfoMetadata()

        return runCatching {
            val parser = Xml.newPullParser()
            nfoFile.inputStream().use { input ->
                parser.setInput(input, null)
                parseNfo(parser)
            }
        }.getOrDefault(NfoMetadata())
    }

    private fun parseNfo(parser: XmlPullParser): NfoMetadata {
        var albumTitle: String? = null
        var posterPath: String? = null
        val trackTitles = mutableListOf<String>()
        var inTrack = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "track" -> inTrack = true
                        "title" -> {
                            val value = parser.nextText().trim()
                            if (value.isNotEmpty()) {
                                if (inTrack) {
                                    trackTitles += cleanDisplayName(value)
                                } else if (albumTitle == null) {
                                    albumTitle = cleanDisplayName(value)
                                }
                            }
                        }
                        "poster" -> {
                            val value = parser.nextText().trim()
                            if (value.isNotEmpty()) posterPath = value
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("track", ignoreCase = true)) inTrack = false
                }
            }
        }

        return NfoMetadata(albumTitle, posterPath, trackTitles)
    }

    private fun displayTrackName(story: StoryFolder, file: File, index: Int): String {
        return story.trackTitles.getOrNull(index)
            ?: cleanDisplayName(file.nameWithoutExtension)
    }
}

private data class NfoMetadata(
    val title: String? = null,
    val posterPath: String? = null,
    val trackTitles: List<String> = emptyList()
)

private val COVER_NAMES = setOf("cover", "folder")
private val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png")
private const val EXIT_CODE = "1234"

private fun StoryFolder.displayTitle(): String {
    return title?.takeIf { it.isNotBlank() } ?: cleanDisplayName(name)
}

private fun cleanDisplayName(value: String): String {
    return value
        .replace('_', ' ')
        .trim()
}

@Composable
fun AppScreen(
    storyFolders: List<StoryFolder>,
    selectedStory: StoryFolder?,
    isScanning: Boolean,
    message: String,
    currentAudioName: String,
    currentIndex: Int,
    audioCount: Int,
    isPlaying: Boolean,
    batteryLevel: Int,
    brightness: Float,
    onSelectStory: (StoryFolder) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onExitApp: () -> Unit
) {
    if (selectedStory != null) {
        PlayerScreen(
            story = selectedStory,
            currentAudioName = currentAudioName,
            currentIndex = currentIndex,
            audioCount = audioCount,
            isPlaying = isPlaying,
            batteryLevel = batteryLevel,
            brightness = brightness,
            onBack = onBack,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onBrightnessChange = onBrightnessChange,
            onExitApp = onExitApp
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalColors.Yellow)
            .padding(0.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        if (isScanning) {
            BrutalFrame(
                color = BrutalColors.Cream,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    color = BrutalColors.Orange,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp)
                )
            }
        } else if (storyFolders.isEmpty()) {
            BrutalFrame(
                color = BrutalColors.Lime,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message.ifBlank {
                        "Ajoute des dossiers dans Histoires avec un fichier audio et une image cover.jpg, jpeg, png."
                    },
                    color = BrutalColors.Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(storyFolders) { story ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onSelectStory(story) }
                    ) {
                        StoryCover(path = story.coverPath, modifier = Modifier.fillMaxSize())
                        Text(
                            text = story.displayTitle().uppercase(),
                            color = BrutalColors.Ink,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(BrutalColors.Lime)
                                .border(3.dp, BrutalColors.Ink)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    story: StoryFolder,
    currentAudioName: String,
    currentIndex: Int,
    audioCount: Int,
    isPlaying: Boolean,
    batteryLevel: Int,
    brightness: Float,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onExitApp: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalColors.Cyan)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalFrame(
                color = BrutalColors.Cream,
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
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(end = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StoryCover(
                            path = story.coverPath,
                            modifier = Modifier
                                .border(3.dp, BrutalColors.Ink)
                                .size(210.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                story.displayTitle().uppercase(),
                                color = BrutalColors.Ink,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                            )
                            Text(
                                currentAudioName,
                                color = BrutalColors.Ink,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(98.dp)
                            )
                        }
                    }
                    Text(
                        "${currentIndex + 1} / $audioCount",
                        color = BrutalColors.Ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            BrutalFrame(
                color = BrutalColors.Yellow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrutalButton("◀", BrutalColors.Lime, onPrevious, Modifier.weight(1f))
                    BrutalButton(if (isPlaying) "▮▮" else "▶", BrutalColors.Orange, onTogglePlayPause, Modifier.weight(1f))
                    BrutalButton("▶", BrutalColors.Lime, onNext, Modifier.weight(1f))
                    BrutalButton("⌂", BrutalColors.Cream, onBack, Modifier.weight(1f))
                    BrutalButton("⚙", BrutalColors.Cyan, { showSettings = true }, Modifier.weight(1f))
                }
            }
        }

        if (showSettings) {
            SettingsPanel(
                batteryLevel = batteryLevel,
                brightness = brightness,
                onBrightnessChange = onBrightnessChange,
                onExitApp = onExitApp,
                onClose = { showSettings = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .widthIn(min = 300.dp, max = 340.dp)
            )
        }
    }
}

@Composable
fun StoryCover(path: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        path?.let { BitmapFactory.decodeFile(it) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image de l’histoire",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier,
            color = BrutalColors.Orange
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, BrutalColors.Ink)
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    batteryLevel: Int,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onExitApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var exitCode by remember { mutableStateOf("") }
    val batteryText = if (batteryLevel >= 0) "$batteryLevel%" else "?"

    BrutalFrame(
        color = BrutalColors.Cream,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚙",
                    color = BrutalColors.Ink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "×",
                    color = BrutalColors.Ink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "☀ ${(brightness * 100).toInt()}%",
                    color = BrutalColors.Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "BAT $batteryText",
                    color = BrutalColors.Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.05f..1f,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = exitCode,
                onValueChange = { exitCode = it.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                placeholder = { Text("CODE") },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(3.dp, BrutalColors.Ink)
            )
            BrutalButton(
                text = "SORTIE",
                color = if (exitCode == EXIT_CODE) BrutalColors.Orange else BrutalColors.Lime,
                onClick = {
                    if (exitCode == EXIT_CODE) onExitApp()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private object BrutalColors {
    val Ink = Color(0xFF101010)
    val Cream = Color(0xFFFFF7D6)
    val Yellow = Color(0xFFFFDE38)
    val Cyan = Color(0xFF18D6D1)
    val Lime = Color(0xFFB7F34A)
    val Orange = Color(0xFFFF7A1A)
}

@Composable
private fun BrutalFrame(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(7.dp, 7.dp)
                .background(BrutalColors.Ink)
        )
        Column(
            modifier = Modifier
                .background(color)
                .border(4.dp, BrutalColors.Ink),
            content = content
        )
    }
}

@Composable
private fun BrutalButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(5.dp, 5.dp)
                .background(BrutalColors.Ink)
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(3.dp, BrutalColors.Ink),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = BrutalColors.Ink
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}
