package com.example.canto

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité utilisé uniquement pour ouvrir le menu d'extinction du système
 * quand l'appareil n'est pas rooté (le bouton power peut être caché dans la boîte).
 */
class PowerService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: PowerService? = null

        /** Ouvre le menu "Éteindre / Redémarrer" du système. Retourne false si le service est inactif. */
        fun showPowerDialog(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
        }
    }
}

object PowerController {
    /** Tente une extinction via root (LineageOS + su). Bloquant : à appeler hors du thread UI. */
    fun shutdownWithRoot(): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
