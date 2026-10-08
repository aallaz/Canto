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

    /** Style du mode clair (voir [Palettes.lightStyles]). */
    var styleName: String
        get() = prefs.getString(KEY_STYLE, Palettes.Couleurs.name) ?: Palettes.Couleurs.name
        set(value) {
            prefs.edit().putString(KEY_STYLE, value).apply()
        }

    /** Mode sombre (style bleu-vert foncé), basculé depuis la barre du haut. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
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
        private const val KEY_STYLE = "style"
        private const val KEY_DARK_MODE = "dark_mode"

        /** Choix proposés pour l'écran noir automatique, de 10 s à 10 min. */
        val SCREEN_OFF_DELAYS = listOf(10, 20, 30, 60, 120, 180, 300, 600)
        const val DEFAULT_SCREEN_OFF_DELAY = 120
    }
}
