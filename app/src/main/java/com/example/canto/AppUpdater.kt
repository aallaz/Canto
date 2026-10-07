package com.example.canto

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Mise à jour depuis la release GitHub "apk-latest" publiée par .github/workflows/build-apk.yml.
 * Les appels réseau sont bloquants : à faire hors du thread UI.
 */
class AppUpdater(private val context: Context) {
    data class Release(val versionCode: Long, val versionName: String, val commit: String)

    val currentVersionCode: Long
        get() = packageInfo()?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L

    val currentVersionName: String
        get() = packageInfo()?.versionName ?: "?"

    private val apkFile: File
        get() = File(context.cacheDir, "updates/canto.apk")

    /** Dernière version publiée, ou null si le réseau ou GitHub ne répond pas. */
    fun fetchLatest(): Release? = runCatching {
        val json = JSONObject(fetch("$BASE_URL/version.json") { it.inputStream.bufferedReader().readText() })
        Release(
            versionCode = json.getLong("versionCode"),
            versionName = json.optString("versionName", json.getLong("versionCode").toString()),
            commit = json.optString("commit")
        )
    }.getOrNull()

    /** Télécharge l'APK. Retourne le fichier, ou null en cas d'échec. */
    fun download(onProgress: (Int) -> Unit): File? = runCatching {
        val target = apkFile
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        fetch("$BASE_URL/canto.apk") { connection ->
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        target.delete()
        check(partial.renameTo(target))
        target
    }.getOrNull()

    /** Android 8+ : Canto doit être autorisé à installer des applications. */
    fun canInstall(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    /** Lance l'installation ; le résultat arrive dans [InstallResultReceiver]. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Silencieux si Canto s'est déjà installé lui-même une fois (Android 12+).
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("canto.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                flags
            )
            session.commit(callback.intentSender)
        }
    }

    private fun <T> fetch(url: String, block: (HttpURLConnection) -> T): T {
        val connection = open(url)
        try {
            return block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        // Toujours la version publiée à l'instant, jamais une réponse en cache.
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Canto")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            error("HTTP ${connection.responseCode}")
        }
        return connection
    }

    private fun packageInfo() = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    companion object {
        const val BASE_URL = "https://github.com/aallaz/Canto/releases/download/apk-latest"

        /** Reçoit les événements d'installation (MainActivity s'y abonne quand elle est ouverte). */
        @Volatile
        var listener: ((InstallEvent) -> Unit)? = null
    }
}

sealed class InstallEvent {
    /** Android demande une confirmation : écran système à afficher. */
    class NeedsConfirmation(val intent: Intent) : InstallEvent()
    class Failed(val message: String) : InstallEvent()
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val event = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                InstallEvent.NeedsConfirmation(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> return
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallEvent.Failed(
                    "Signature différente de la version installée : désinstalle Canto une fois, " +
                        "puis installe la nouvelle version (voir README)."
                )
            else -> InstallEvent.Failed(
                "Installation échouée : " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status")
            )
        }

        val listener = AppUpdater.listener
        if (listener != null) {
            listener(event)
        } else if (event is InstallEvent.NeedsConfirmation) {
            context.startActivity(event.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
