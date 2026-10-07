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
    }
}
