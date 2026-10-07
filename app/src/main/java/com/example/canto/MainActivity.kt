package com.example.canto

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Xml
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
    private val storiesRootsState: MutableState<List<StorageLocator.StoriesRoot>> = mutableStateOf(emptyList())
    private val isScanningState: MutableState<Boolean> = mutableStateOf(true)
    private val messageState: MutableState<String> = mutableStateOf("Recherche des histoires…")
    private val selectedStoryState: MutableState<StoryFolder?> = mutableStateOf(null)
    private val audioFilesState: MutableState<List<File>> = mutableStateOf(emptyList())
    private val currentAudioNameState: MutableState<String> = mutableStateOf("")
    private val currentIndexState: MutableState<Int> = mutableStateOf(0)
    private val isPlayingState: MutableState<Boolean> = mutableStateOf(false)
    private val batteryLevelState: MutableState<Int> = mutableStateOf(-1)
    private val brightnessState: MutableState<Float> = mutableStateOf(AppSettings.DEFAULT_BRIGHTNESS)
    private val volumeState: MutableState<Int> = mutableStateOf(0)
    private val showSettingsState: MutableState<Boolean> = mutableStateOf(false)
    private val needsAllFilesAccessState: MutableState<Boolean> = mutableStateOf(false)
    private val wifiUrlState: MutableState<String?> = mutableStateOf(null)
    private val settingsInfoState: MutableState<String> = mutableStateOf("")

    private lateinit var settings: AppSettings
    private lateinit var audioManager: AudioManager
    private lateinit var transferServer: WifiTransferServer
    private var mediaPlayer: MediaPlayer? = null
    private var awaitingExternalSettings = false

    /** Dossier où écrire les transferts Wi-Fi (lu depuis les threads du serveur). */
    @Volatile
    private var transferRoot: File? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rescanRunnable = Runnable { scanStoryFolders() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshAllFilesAccess()
        if (results.values.any { it } || hasStorageAccess()) {
            scanStoryFolders()
        } else {
            messageState.value = "L'accès au stockage est nécessaire pour lire les histoires. Autorise-le dans les réglages ⚙."
            isScanningState.value = false
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevelState.value = if (level >= 0 && scale > 0) level * 100 / scale else -1
        }
    }

    /** Relance la recherche quand une carte SD est insérée ou retirée. */
    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val story = selectedStoryState.value
            if (story != null && !File(story.path).isDirectory) backToGallery()
            scheduleRescan()
        }
    }

    private val settingsActions = object : SettingsActions {
        override fun hasPin(): Boolean = settings.hasPin
        override fun checkPin(pin: String): Boolean = settings.checkPin(pin)
        override fun savePin(pin: String) = settings.setPin(pin)
        override fun onBrightnessChange(value: Float) = setBrightness(value)
        override fun onVolumeChange(value: Int) = setVolume(value)
        override fun onRescan() = scanStoryFolders()
        override fun onRequestAllFilesAccess() = requestAllFilesAccess()
        override fun onToggleWifi() = toggleWifiTransfer()
        override fun onPowerOff() = powerOff()
        override fun onExitApp() = exitApp()
        override fun onClose() {
            showSettingsState.value = false
            settingsInfoState.value = ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        transferServer = WifiTransferServer(
            uploadPage = assets.open("upload.html").bufferedReader().use { it.readText() },
            targetRoot = ::currentTransferRoot,
            checkCode = { code -> settings.checkPin(code) },
            onFilesChanged = { runOnUiThread { scheduleRescan() } }
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        brightnessState.value = settings.brightness
        applyBrightness(brightnessState.value)
        enterKioskMode()

        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(this, mediaReceiver, mediaFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            CantoTheme {
                val story = selectedStoryState.value
                AppScreen(
                    storyFolders = storyFoldersState.value,
                    isScanning = isScanningState.value,
                    message = messageState.value,
                    batteryLevel = batteryLevelState.value,
                    player = story?.let {
                        PlayerUiState(
                            story = it,
                            currentAudioName = currentAudioNameState.value,
                            currentIndex = currentIndexState.value,
                            audioCount = audioFilesState.value.size,
                            isPlaying = isPlayingState.value
                        )
                    },
                    settings = if (showSettingsState.value) {
                        SettingsUiState(
                            batteryLevel = batteryLevelState.value,
                            brightness = brightnessState.value,
                            volume = volumeState.value,
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            storiesRoots = storiesRootsState.value,
                            needsAllFilesAccess = needsAllFilesAccessState.value,
                            wifiUrl = wifiUrlState.value,
                            info = settingsInfoState.value
                        )
                    } else {
                        null
                    },
                    settingsActions = settingsActions,
                    onSelectStory = ::selectStory,
                    onBack = ::backToGallery,
                    onTogglePlayPause = ::togglePlayPause,
                    onPrevious = ::playPrevious,
                    onNext = ::playNext,
                    onOpenSettings = ::openSettings
                )
            }
        }

        refreshAllFilesAccess()
        if (hasStorageAccess()) {
            scanStoryFolders()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        enterKioskMode()
        if (awaitingExternalSettings) {
            awaitingExternalSettings = false
            refreshAllFilesAccess()
            scanStoryFolders()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterKioskMode()
    }

    @Deprecated("Back is intentionally disabled for kiosk mode.")
    override fun onBackPressed() {
        // Verrouillé volontairement. Sortie possible via ⚙ > code parent > Quitter.
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(rescanRunnable)
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(mediaReceiver) }
        transferServer.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    // --- Stockage ---

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    private fun hasStorageAccess(): Boolean {
        return hasAllFilesAccess() || requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun refreshAllFilesAccess() {
        needsAllFilesAccessState.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess()
    }

    /** Android 11+ : l'accès complet est nécessaire pour la carte SD, les .nfo et l'écriture des transferts. */
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        if (!openExternalSettings(appIntent)) {
            openExternalSettings(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun scheduleRescan() {
        mainHandler.removeCallbacks(rescanRunnable)
        mainHandler.postDelayed(rescanRunnable, RESCAN_DELAY_MS)
    }

    private fun scanStoryFolders() {
        isScanningState.value = true
        if (storyFoldersState.value.isEmpty()) messageState.value = "Recherche des histoires…"

        Thread {
            val roots = StorageLocator.existingRoots(this)
            val folders = roots.flatMap { scanRoot(it.dir) }
            runOnUiThread { applyScanResult(roots, folders) }
        }.start()
    }

    private fun applyScanResult(roots: List<StorageLocator.StoriesRoot>, folders: List<StoryFolder>) {
        storiesRootsState.value = roots
        storyFoldersState.value = folders
        transferRoot = roots.firstOrNull { it.dir.canWrite() }?.dir
        messageState.value = when {
            roots.isEmpty() ->
                "Aucun dossier Histoires trouvé sur la carte SD ni dans le stockage interne. " +
                    "Crée un dossier Histoires ou envoie des histoires par Wi-Fi depuis les réglages ⚙."
            folders.isEmpty() ->
                "Aucune histoire trouvée dans ${roots.joinToString { it.dir.absolutePath }}."
            else -> ""
        }
        isScanningState.value = false
    }

    private fun scanRoot(root: File): List<StoryFolder> {
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.mapNotNull { folder ->
                val metadata = readNfoMetadata(folder)
                val coverPath = findCoverPath(folder, metadata.posterPath)
                val audioFiles = listAudioFiles(folder)

                if (audioFiles.isEmpty()) {
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
    }

    private fun currentTransferRoot(): File {
        val root = transferRoot ?: StorageLocator.localRoot()
        if (!root.isDirectory) root.mkdirs()
        return root
    }

    // --- Lecture ---

    private fun selectStory(story: StoryFolder) {
        selectedStoryState.value = story
        val audioFiles = listAudioFiles(File(story.path))

        audioFilesState.value = audioFiles
        currentIndexState.value = 0
        if (audioFiles.isNotEmpty()) {
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
        val player = mediaPlayer
        if (player == null) {
            playTrack(files[currentIndexState.value])
            return
        }
        if (player.isPlaying) {
            player.pause()
            isPlayingState.value = false
        } else {
            player.start()
            isPlayingState.value = true
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        isPlayingState.value = false
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

    private fun playTrack(file: File) {
        mediaPlayer?.release()
        mediaPlayer = null
        val trackName = selectedStoryState.value
            ?.let { story -> displayTrackName(story, file, currentIndexState.value) }
            ?: cleanDisplayName(file.nameWithoutExtension)

        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { playNext() }
                prepare()
                start()
            }
        }.getOrNull()

        mediaPlayer = player
        currentAudioNameState.value = if (player != null) trackName else "Lecture impossible : $trackName"
        isPlayingState.value = player != null
    }

    // --- Réglages ---

    private fun openSettings() {
        volumeState.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        refreshAllFilesAccess()
        wifiUrlState.value = if (transferServer.isRunning) transferServer.url() ?: "Pas de Wi-Fi" else null
        settingsInfoState.value = ""
        showSettingsState.value = true
    }

    private fun setVolume(value: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val bounded = value.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, bounded, 0)
        volumeState.value = bounded
    }

    private fun setBrightness(value: Float) {
        val boundedValue = value.coerceIn(AppSettings.MIN_BRIGHTNESS, AppSettings.MAX_BRIGHTNESS)
        brightnessState.value = boundedValue
        settings.brightness = boundedValue
        applyBrightness(boundedValue)
    }

    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(AppSettings.MIN_BRIGHTNESS, AppSettings.MAX_BRIGHTNESS)
        }
    }

    private fun toggleWifiTransfer() {
        if (transferServer.isRunning) {
            transferServer.stop()
            wifiUrlState.value = null
            return
        }
        if (!transferServer.start()) {
            settingsInfoState.value = "Impossible de démarrer le transfert (port ${WifiTransferServer.PORT} occupé ?)"
            return
        }
        val url = transferServer.url()
        wifiUrlState.value = url ?: "Pas de Wi-Fi"
        settingsInfoState.value = if (url == null) "Connecte d'abord le téléphone à un réseau Wi-Fi." else ""
    }

    /**
     * Extinction : root si disponible (LineageOS + su), sinon menu d'extinction du système
     * via le service d'accessibilité, sinon ouverture des réglages pour activer ce service.
     */
    private fun powerOff() {
        pausePlayback()
        settingsInfoState.value = "Extinction…"
        Thread {
            if (PowerController.shutdownWithRoot()) return@Thread
            runOnUiThread {
                if (PowerService.showPowerDialog()) {
                    settingsInfoState.value = ""
                } else {
                    settingsInfoState.value =
                        "Active « Canto » dans Réglages > Accessibilité, puis appuie à nouveau sur Éteindre."
                    openExternalSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }.start()
    }

    /** Ouvre un écran système en sortant temporairement du mode kiosque (réactivé au retour). */
    private fun openExternalSettings(intent: Intent): Boolean {
        runCatching { stopLockTask() }
        return runCatching {
            startActivity(intent)
            awaitingExternalSettings = true
        }.isSuccess
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
        transferServer.stop()
        runCatching { stopLockTask() }
        mediaPlayer?.release()
        mediaPlayer = null
        finishAndRemoveTask()
    }

    // --- Fichiers et métadonnées ---

    private fun listAudioFiles(folder: File): List<File> {
        return folder.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.canRead() && isAudioFile(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun isAudioFile(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
    }

    /** cover/folder.jpg, sinon l'affiche du .nfo, sinon n'importe quelle image du dossier. */
    private fun findCoverPath(folder: File, posterPath: String?): String? {
        val images = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in COVER_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

        images.firstOrNull { it.nameWithoutExtension.lowercase() in COVER_NAMES }
            ?.let { return it.absolutePath }

        posterPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }
            ?.let { return it.absolutePath }

        return (images.firstOrNull { image -> COVER_HINTS.any { it in image.name.lowercase() } } ?: images.firstOrNull())
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

    private companion object {
        const val RESCAN_DELAY_MS = 1500L
    }
}

private data class NfoMetadata(
    val title: String? = null,
    val posterPath: String? = null,
    val trackTitles: List<String> = emptyList()
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "aac", "ogg")
private val COVER_NAMES = setOf("cover", "folder")
private val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png")
private val COVER_HINTS = listOf("cover", "folder", "front", "album")

fun StoryFolder.displayTitle(): String {
    return title?.takeIf { it.isNotBlank() } ?: cleanDisplayName(name)
}

private fun cleanDisplayName(value: String): String {
    return value
        .replace('_', ' ')
        .trim()
}
