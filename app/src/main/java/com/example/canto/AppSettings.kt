package com.example.canto

import android.content.Context
import java.security.MessageDigest

/**
 * Réglages persistants de l'application (code parent, luminosité).
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("canto", Context.MODE_PRIVATE)

    val hasPin: Boolean
        get() = prefs.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        set(value) {
            prefs.edit().putFloat(KEY_BRIGHTNESS, value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)).apply()
        }

    /** Volume maximal autorisé (0 = pas encore choisi). */
    var volumeLimitValue: Int
        get() = prefs.getInt(KEY_VOLUME_LIMIT, 0)
        set(value) {
            prefs.edit().putInt(KEY_VOLUME_LIMIT, value).apply()
        }

    /** Limite effective : celle choisie, sinon 70 % du volume maximal du téléphone. */
    fun volumeLimit(maxVolume: Int): Int {
        val chosen = volumeLimitValue
        return if (chosen in 1..maxVolume) chosen else (maxVolume * 0.7f).toInt().coerceAtLeast(1)
    }

    /** Délai sans toucher avant l'écran noir, en secondes. */
    var screenOffDelaySeconds: Int
        get() = prefs.getInt(KEY_SCREEN_OFF_DELAY, DEFAULT_SCREEN_OFF_DELAY).let { if (it in SCREEN_OFF_DELAYS) it else DEFAULT_SCREEN_OFF_DELAY }
        set(value) {
            prefs.edit().putInt(KEY_SCREEN_OFF_DELAY, value).apply()
        }

    /** L'autorisation d'installer des applis a déjà été proposée au premier lancement. */
    var installPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_INSTALL_PERMISSION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INSTALL_PERMISSION_ASKED, value).apply()
        }

    /** Code parent demandé à l'ouverture des réglages (désactivable dans les réglages). */
    var pinEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PIN_ENABLED, value).apply()
        }

    /** Normalisation du volume pendant la lecture (réglages → son et écran). */
    var normalizeVolume: Boolean
        get() = prefs.getBoolean(KEY_NORMALIZE_VOLUME, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, value).apply()
        }

    /** Mode sombre (style bleu-vert foncé), basculé depuis la barre du haut. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    /** Affichage choisi pour le compteur du lecteur (piste, temps de la piste, temps total). */
    var playerInfo: PlayerInfo
        get() = PlayerInfo.values().getOrNull(prefs.getInt(KEY_PLAYER_INFO, 0)) ?: PlayerInfo.Track
        set(value) {
            prefs.edit().putInt(KEY_PLAYER_INFO, value.ordinal).apply()
        }

    /** Minuterie de veille : durée proposée (minutes). */
    var sleepTimerMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_MINUTES, DEFAULT_SLEEP_TIMER_MINUTES).let { if (it in SLEEP_TIMER_CHOICES) it else DEFAULT_SLEEP_TIMER_MINUTES }
        set(value) {
            prefs.edit().putInt(KEY_SLEEP_MINUTES, value).apply()
        }

    /** Minuterie écoulée : laisser finir la piste en cours avant d'arrêter. */
    var sleepWaitTrackEnd: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_WAIT_TRACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SLEEP_WAIT_TRACK, value).apply()
        }

    /** Minuterie écoulée : éteindre le téléphone (sinon simple écran noir). */
    var sleepPowerOff: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_POWER_OFF, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SLEEP_POWER_OFF, value).apply()
        }

    /** Musique : pistes de l'album dans un ordre mélangé. */
    var musicShuffle: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_SHUFFLE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC_SHUFFLE, value).apply()
        }

    /** Musique : l'album recommence au début une fois terminé. */
    var musicRepeat: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_REPEAT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC_REPEAT, value).apply()
        }

    /** Histoire en cours au moment de la dernière sauvegarde, pour reprendre après un redémarrage. */
    data class SavedPlayback(val path: String, val order: List<Int>, val orderPosition: Int, val positionMs: Long)

    fun savePlayback(saved: SavedPlayback) {
        prefs.edit()
            .putString(KEY_RESUME_PATH, saved.path)
            .putString(KEY_RESUME_ORDER, saved.order.joinToString(","))
            .putInt(KEY_RESUME_ORDER_POSITION, saved.orderPosition)
            .putLong(KEY_RESUME_POSITION_MS, saved.positionMs)
            .apply()
    }

    fun savedPlayback(): SavedPlayback? {
        val path = prefs.getString(KEY_RESUME_PATH, null) ?: return null
        val order = prefs.getString(KEY_RESUME_ORDER, "").orEmpty()
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        return SavedPlayback(
            path,
            order,
            prefs.getInt(KEY_RESUME_ORDER_POSITION, 0),
            prefs.getLong(KEY_RESUME_POSITION_MS, 0L)
        )
    }

    fun clearSavedPlayback() {
        prefs.edit()
            .remove(KEY_RESUME_PATH)
            .remove(KEY_RESUME_ORDER)
            .remove(KEY_RESUME_ORDER_POSITION)
            .remove(KEY_RESUME_POSITION_MS)
            .apply()
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("canto:$pin".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PIN_LENGTH = 4

        /** Luminosité limitée pour économiser la batterie et ménager les yeux. */
        const val MIN_BRIGHTNESS = 0.02f
        const val MAX_BRIGHTNESS = 0.6f
        const val DEFAULT_BRIGHTNESS = 0.3f

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_VOLUME_LIMIT = "volume_limit"
        private const val KEY_SCREEN_OFF_DELAY = "screen_off_delay"
        private const val KEY_INSTALL_PERMISSION_ASKED = "install_permission_asked"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PLAYER_INFO = "player_info"
        private const val KEY_SLEEP_MINUTES = "sleep_minutes"
        private const val KEY_SLEEP_WAIT_TRACK = "sleep_wait_track"
        private const val KEY_SLEEP_POWER_OFF = "sleep_power_off"
        private const val KEY_MUSIC_SHUFFLE = "music_shuffle"
        private const val KEY_MUSIC_REPEAT = "music_repeat"
        private const val KEY_RESUME_PATH = "resume_path"
        private const val KEY_RESUME_ORDER = "resume_order"
        private const val KEY_RESUME_ORDER_POSITION = "resume_order_position"
        private const val KEY_RESUME_POSITION_MS = "resume_position_ms"

        /** Durées proposées pour la minuterie de veille, en minutes. */
        val SLEEP_TIMER_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60, 90)
        const val DEFAULT_SLEEP_TIMER_MINUTES = 20

        /** Choix proposés pour l'écran noir automatique, de 10 s à 10 min. */
        val SCREEN_OFF_DELAYS = listOf(10, 20, 30, 60, 120, 180, 300, 600)
        const val DEFAULT_SCREEN_OFF_DELAY = 120
    }
}
