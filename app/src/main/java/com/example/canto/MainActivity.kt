package com.example.canto

import android.Manifest
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Xml
import android.view.MotionEvent
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
    private val isChargingState: MutableState<Boolean> = mutableStateOf(false)
    private val wifiConnectedState: MutableState<Boolean> = mutableStateOf(false)
    private val bluetoothState: MutableState<BluetoothUiState> = mutableStateOf(BluetoothUiState())
    private val updateState: MutableState<UpdateUiState> = mutableStateOf(UpdateUiState())
    private val brightnessState: MutableState<Float> = mutableStateOf(AppSettings.DEFAULT_BRIGHTNESS)
    private val volumeState: MutableState<Int> = mutableStateOf(0)
    private val volumeLimitState: MutableState<Int> = mutableStateOf(0)
    private val screenDarkState: MutableState<Boolean> = mutableStateOf(false)
    private val screenOffDelayState: MutableState<Int> = mutableStateOf(AppSettings.DEFAULT_SCREEN_OFF_DELAY)
    private val showSettingsState: MutableState<Boolean> = mutableStateOf(false)
    private val needsAllFilesAccessState: MutableState<Boolean> = mutableStateOf(false)
    private val wifiUrlState: MutableState<String?> = mutableStateOf(null)
    private val settingsInfoState: MutableState<String> = mutableStateOf("")

    private lateinit var settings: AppSettings
    private lateinit var audioManager: AudioManager
    private lateinit var transferServer: WifiTransferServer
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var speakers: BluetoothSpeakers
    private lateinit var updater: AppUpdater
    private var pendingBluetoothScan = false
    private var brightnessAnimator: ValueAnimator? = null
    private var installAfterPermission = false
    private var mediaPlayer: MediaPlayer? = null
    private var awaitingExternalSettings = false

    /** Dossiers Histoires trouvés au dernier scan (lus depuis les threads du serveur). */
    @Volatile
    private var scannedRoots: List<File> = emptyList()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rescanRunnable = Runnable { scanStoryFolders() }
    private val idleRunnable = Runnable { setScreenDark(true) }
    private val updateCheckRunnable = object : Runnable {
        override fun run() {
            checkForUpdate(silent = true)
            mainHandler.postDelayed(this, UPDATE_CHECK_INTERVAL_MS)
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPermissions(bluetoothConnectPermissions())) speakers.start()
        if (pendingBluetoothScan && hasPermissions(bluetoothScanPermissions())) {
            speakers.scan()
        } else if (pendingBluetoothScan) {
            settingsInfoState.value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                "Sans l'autorisation « Position », Android ne permet pas de rechercher de nouvelles enceintes. " +
                    "Les enceintes déjà appairées restent utilisables."
            } else {
                "Autorise Canto à utiliser le Bluetooth pour rechercher une enceinte."
            }
        }
        pendingBluetoothScan = false
    }

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
        askInstallPermissionOnFirstLaunch()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevelState.value = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isChargingState.value = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
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

    /** Suit le volume, y compris quand il est changé par les boutons physiques. */
    private val volumeObserver by lazy {
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = refreshVolume()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            mainHandler.post { wifiConnectedState.value = wifi }
        }

        override fun onLost(network: Network) {
            mainHandler.post { wifiConnectedState.value = false }
        }
    }

    private val settingsActions = object : SettingsActions {
        override fun hasPin(): Boolean = settings.hasPin
        override fun checkPin(pin: String): Boolean = settings.checkPin(pin)
        override fun savePin(pin: String) = settings.setPin(pin)
        override fun onBrightnessChange(value: Float) = setBrightness(value)
        override fun onVolumeChange(value: Int) = setVolume(value)
        override fun onVolumeLimitChange(value: Int) = setVolumeLimit(value)
        override fun onScreenOffDelayChange(seconds: Int) {
            settings.screenOffDelaySeconds = seconds
            screenOffDelayState.value = seconds
            restartIdleTimer()
        }
        override fun onRescan() = scanStoryFolders()
        override fun onRequestAllFilesAccess() = requestAllFilesAccess()
        override fun onToggleWifi() = toggleWifiTransfer()
        override fun onBluetoothEnable() {
            if (!speakers.enable()) openExternalSettings(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        override fun onBluetoothScan() {
            // La permission n'est demandée qu'ici, au moment où le parent lance une recherche.
            val needed = bluetoothConnectPermissions() + bluetoothScanPermissions()
            if (hasPermissions(needed)) {
                speakers.scan()
            } else {
                pendingBluetoothScan = true
                bluetoothPermissionLauncher.launch(needed)
            }
        }
        override fun onBluetoothConnect(address: String) {
            if (!hasPermissions(bluetoothConnectPermissions())) {
                bluetoothPermissionLauncher.launch(bluetoothConnectPermissions())
                return
            }
            if (!speakers.connect(address)) {
                settingsInfoState.value = "Connexion impossible depuis Canto : utilise « Réglages Android »."
            }
        }
        override fun onBluetoothDisconnect(address: String) {
            if (!speakers.disconnect(address)) {
                settingsInfoState.value = "Déconnexion impossible depuis Canto : utilise « Réglages Android »."
            }
        }
        override fun onOpenBluetoothSettings() {
            openExternalSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        override fun onCheckUpdate() = checkForUpdate(silent = false)
        override fun onInstallUpdate() = installUpdate()
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
        volumeLimitState.value = settings.volumeLimit(maxVolume())
        screenOffDelayState.value = settings.screenOffDelaySeconds
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        speakers = BluetoothSpeakers(this) { state -> runOnUiThread { bluetoothState.value = state } }
        updater = AppUpdater(this)
        updateState.value = UpdateUiState(currentVersion = updater.currentVersionName)
        AppUpdater.listener = { event -> runOnUiThread { onInstallEvent(event) } }
        transferServer = WifiTransferServer(
            uploadPage = assets.open("upload.html").bufferedReader().use { it.readText() },
            library = { storyFoldersState.value },
            transferTarget = ::findTransferTarget,
            storageAccessProblem = ::storageAccessProblem,
            checkCode = { code -> settings.checkPin(code) },
            onFilesChanged = { runOnUiThread { scheduleRescan() } }
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        brightnessState.value = settings.brightness
        applyBrightness(brightnessState.value)
        enterKioskMode()

        // Diffusions du système : RECEIVER_EXPORTED obligatoire, car avec NOT_EXPORTED,
        // ContextCompat les bloque sur Android < 13 (la batterie restait alors à « ? »).
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )?.let { sticky -> batteryReceiver.onReceive(this, sticky) }
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(this, mediaReceiver, mediaFilter, ContextCompat.RECEIVER_EXPORTED)
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        refreshVolume()
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        if (hasPermissions(bluetoothConnectPermissions())) speakers.start()
        mainHandler.postDelayed(updateCheckRunnable, FIRST_UPDATE_CHECK_DELAY_MS)

        setContent {
            CantoTheme {
                val story = selectedStoryState.value
                AppScreen(
                    storyFolders = storyFoldersState.value,
                    isScanning = isScanningState.value,
                    message = messageState.value,
                    status = StatusBarState(
                        batteryLevel = batteryLevelState.value,
                        isCharging = isChargingState.value,
                        volume = volumeState.value,
                        volumeLimit = volumeLimitState.value,
                        wifiConnected = wifiConnectedState.value,
                        transferActive = wifiUrlState.value != null,
                        bluetoothConnected = bluetoothState.value.speakers.any { it.isConnected },
                        updateAvailable = updateState.value.availableVersion != null
                    ),
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
                            volumeLimit = volumeLimitState.value,
                            screenOffDelaySeconds = screenOffDelayState.value,
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            storiesRoots = storiesRootsState.value,
                            needsAllFilesAccess = needsAllFilesAccessState.value,
                            wifiUrl = wifiUrlState.value,
                            bluetooth = bluetoothState.value,
                            update = updateState.value,
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
                    onOpenSettings = ::openSettings,
                    onVolumeChange = ::setVolume,
                    isScreenDark = screenDarkState.value,
                    onScreenOff = { setScreenDark(true) },
                    onScreenWake = { setScreenDark(false) }
                )
            }
        }

        refreshAllFilesAccess()
        if (hasStorageAccess()) {
            scanStoryFolders()
            askInstallPermissionOnFirstLaunch()
        } else {
            // La demande d'autorisation d'installation suit, une fois celle-ci traitée.
            permissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        enterKioskMode()
        restartIdleTimer()
        if (awaitingExternalSettings) {
            awaitingExternalSettings = false
            refreshAllFilesAccess()
            scanStoryFolders()
            resumeInstallAfterPermission()
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(idleRunnable)
        super.onPause()
    }

    /** Tout toucher relance le compte à rebours de l'écran noir automatique. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        restartIdleTimer()
        return super.dispatchTouchEvent(event)
    }

    private fun restartIdleTimer() {
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.postDelayed(idleRunnable, screenOffDelayState.value * 1000L)
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
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.removeCallbacks(updateCheckRunnable)
        AppUpdater.listener = null
        contentResolver.unregisterContentObserver(volumeObserver)
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        speakers.stop()
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
        scannedRoots = roots.map { it.dir }
        // Histoire supprimée (interface web, carte retirée) pendant qu'elle est ouverte.
        selectedStoryState.value?.let { story -> if (!File(story.path).isDirectory) backToGallery() }
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

    /**
     * Premier dossier Histoires réellement accessible en écriture (test d'écriture, car
     * File.canWrite() se trompe souvent sur carte SD), sinon celui du stockage interne.
     */
    private fun findTransferTarget(): TransferTarget {
        val candidates = (scannedRoots + StorageLocator.localRoot()).distinctBy { it.absolutePath }
        candidates.firstOrNull(::canWriteTo)?.let { return TransferTarget(it, null) }

        val hint = storageAccessProblem()
            ?: "Aucun dossier accessible en écriture (${candidates.joinToString { it.absolutePath }})."
        return TransferTarget(null, hint)
    }

    private fun storageAccessProblem(): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess() ->
            "Canto n'a pas l'accès aux fichiers. Sur la boîte : réglages → Autoriser l'accès " +
                "(ou adb shell appops set --uid com.example.canto MANAGE_EXTERNAL_STORAGE allow)."
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ->
            "Canto n'a pas le droit d'écrire : adb shell pm grant com.example.canto android.permission.WRITE_EXTERNAL_STORAGE"
        else -> null
    }

    private fun canWriteTo(dir: File): Boolean = runCatching {
        if (!dir.isDirectory) dir.mkdirs()
        val probe = File(dir, ".canto-test")
        probe.writeText("ok")
        probe.delete()
    }.getOrDefault(false)

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
                currentAudioNameState.value = story.trackName(files.last(), files.lastIndex)
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
            ?.let { story -> story.trackName(file, currentIndexState.value) }
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
        refreshVolume()
        if (hasPermissions(bluetoothConnectPermissions())) speakers.start()
        refreshAllFilesAccess()
        wifiUrlState.value = if (transferServer.isRunning) transferServer.url() ?: "Pas de Wi-Fi" else null
        settingsInfoState.value = ""
        showSettingsState.value = true
        if (!updateState.value.isBusy) checkForUpdate(silent = true)
    }

    private fun maxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private fun refreshVolume() {
        volumeState.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        enforceVolumeLimit()
    }

    /** Le volume ne dépasse jamais la limite fixée dans les réglages, même avec les boutons physiques. */
    private fun enforceVolumeLimit() {
        if (volumeState.value > volumeLimitState.value) setVolume(volumeLimitState.value)
    }

    private fun setVolume(value: Int) {
        val bounded = value.coerceIn(0, volumeLimitState.value)
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != bounded) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, bounded, 0)
        }
        volumeState.value = bounded
    }

    private fun setVolumeLimit(value: Int) {
        val bounded = value.coerceIn(1, maxVolume())
        settings.volumeLimitValue = bounded
        volumeLimitState.value = bounded
        enforceVolumeLimit()
    }

    private fun setBrightness(value: Float) {
        val boundedValue = value.coerceIn(AppSettings.MIN_BRIGHTNESS, AppSettings.MAX_BRIGHTNESS)
        brightnessState.value = boundedValue
        settings.brightness = boundedValue
        applyBrightness(boundedValue)
    }

    /**
     * Écran noir : rétroéclairage au minimum sous un voile noir. L'écran n'est pas réellement
     * éteint, car il faudrait le bouton power (caché dans la boîte) pour le rallumer.
     */
    private fun setScreenDark(dark: Boolean) {
        if (screenDarkState.value == dark) return
        screenDarkState.value = dark
        // Le rétroéclairage suit le voile noir en fondu (même durée que dans CantoUi).
        val current = window.attributes.screenBrightness.takeIf { it >= 0f } ?: brightnessState.value
        val target = if (dark) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF else brightnessState.value
        brightnessAnimator?.cancel()
        brightnessAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = (if (dark) SCREEN_FADE_OUT_MS else SCREEN_FADE_IN_MS).toLong()
            addUpdateListener { animator ->
                window.attributes = window.attributes.apply { screenBrightness = animator.animatedValue as Float }
            }
            start()
        }
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

    // --- Bluetooth ---

    /** Lister et connecter les enceintes appairées (aucune demande avant Android 12). */
    private fun bluetoothConnectPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

    /** Rechercher de nouvelles enceintes : avant Android 12, Android exige la permission de position. */
    private fun bluetoothScanPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasPermissions(permissions: Array<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // --- Mise à jour ---

    private fun checkForUpdate(silent: Boolean) {
        if (!silent) updateState.value = updateState.value.copy(message = "Recherche d'une mise à jour…")
        Thread {
            val latest = updater.fetchLatest()
            runOnUiThread {
                val current = updateState.value
                updateState.value = when {
                    latest == null -> current.copy(message = if (silent) current.message else "Impossible de joindre GitHub (Wi-Fi ?).")
                    latest.versionCode > updater.currentVersionCode -> current.copy(availableVersion = latest.versionName, message = "")
                    else -> current.copy(
                        availableVersion = null,
                        message = if (silent) "" else
                            "Canto est à jour (installée : ${updater.currentVersionName}, publiée sur GitHub : ${latest.versionName})."
                    )
                }
            }
        }.start()
    }

    /**
     * Au tout premier lancement (parent présent), propose d'autoriser Canto à installer ses mises à jour,
     * pour ne pas avoir à le faire plus tard depuis les réglages.
     */
    private fun askInstallPermissionOnFirstLaunch() {
        if (settings.installPermissionAsked || updater.canInstall()) return
        settings.installPermissionAsked = true
        openInstallPermissionSettings()
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            openExternalSettings(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }
    }

    /** Au retour de l'écran d'autorisation, l'installation demandée reprend toute seule. */
    private fun resumeInstallAfterPermission() {
        if (!installAfterPermission) return
        installAfterPermission = false
        if (updater.canInstall()) {
            installUpdate()
        } else {
            updateState.value = updateState.value.copy(message = "Installation annulée : Canto n'a pas été autorisé à installer des applications.")
        }
    }

    private fun installUpdate() {
        if (!updater.canInstall()) {
            installAfterPermission = true
            updateState.value = updateState.value.copy(message = "Autorise Canto à installer des applications : l'installation reprendra ensuite.")
            openInstallPermissionSettings()
            return
        }
        updateState.value = updateState.value.copy(isBusy = true, message = "Téléchargement…")
        Thread {
            val apk = updater.download { percent ->
                runOnUiThread { updateState.value = updateState.value.copy(message = "Téléchargement… $percent%") }
            }
            runOnUiThread {
                if (apk == null) {
                    updateState.value = updateState.value.copy(isBusy = false, message = "Téléchargement impossible (Wi-Fi ?).")
                    return@runOnUiThread
                }
                pausePlayback()
                updateState.value = updateState.value.copy(message = "Installation…")
                runCatching { updater.install(apk) }.onFailure {
                    updateState.value = updateState.value.copy(isBusy = false, message = "Installation impossible : ${it.message}")
                }
            }
        }.start()
    }

    private fun onInstallEvent(event: InstallEvent) {
        when (event) {
            is InstallEvent.NeedsConfirmation -> openExternalSettings(event.intent)
            is InstallEvent.Failed -> updateState.value = updateState.value.copy(isBusy = false, message = event.message)
        }
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


    private companion object {
        const val RESCAN_DELAY_MS = 1500L
        const val FIRST_UPDATE_CHECK_DELAY_MS = 20_000L
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
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

/** Fichiers audio lisibles d'un dossier, dans l'ordre de lecture. */
fun listAudioFiles(folder: File): List<File> {
    return folder.listFiles()
        ?.filter { it.isFile && it.length() > 0 && it.canRead() && isAudioFile(it.name) }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
}

private fun isAudioFile(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
}

/** Titre de la piste : celui du .nfo s'il existe, sinon le nom du fichier. */
fun StoryFolder.trackName(file: File, index: Int): String {
    return trackTitles.getOrNull(index) ?: cleanDisplayName(file.nameWithoutExtension)
}

fun StoryFolder.displayTitle(): String {
    return title?.takeIf { it.isNotBlank() } ?: cleanDisplayName(name)
}

private fun cleanDisplayName(value: String): String {
    return value
        .replace('_', ' ')
        .trim()
}
