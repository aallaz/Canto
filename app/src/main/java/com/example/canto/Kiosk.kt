package com.example.canto

import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** Récepteur d'administration : nécessaire pour que Canto puisse devenir propriétaire de l'appareil. */
class CantoAdminReceiver : DeviceAdminReceiver()

data class KioskUiState(
    /** Canto est propriétaire de l'appareil (activé une fois par ADB). */
    val isOwner: Boolean = false,
    /** L'écran de verrouillage d'Android est désactivé (impossible si un code Android est défini). */
    val keyguardDisabled: Boolean = false
)

/**
 * Mode kiosque. Si Canto est propriétaire de l'appareil (« device owner ») :
 * épinglage sans message, écran de verrouillage désactivé, Canto toujours écran d'accueil,
 * menu marche/arrêt conservé. Activation, une seule fois :
 *   adb shell dpm set-device-owner com.example.canto/.CantoAdminReceiver
 */
object Kiosk {
    const val ADB_ENABLE = "adb shell dpm set-device-owner com.example.canto/.CantoAdminReceiver"

    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(context: Context) = ComponentName(context, CantoAdminReceiver::class.java)

    fun isOwner(context: Context): Boolean = runCatching { dpm(context).isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    /** Applique les règles du mode propriétaire ; retourne l'état obtenu. */
    fun applyOwnerPolicies(context: Context): KioskUiState {
        if (!isOwner(context)) return KioskUiState()
        val dpm = dpm(context)
        val admin = admin(context)
        // Ne modifier la liste que si nécessaire : la changer pendant que l'app est verrouillée
        // peut amener Android à fermer la tâche verrouillée.
        val allowed = runCatching { dpm.getLockTaskPackages(admin).toList() }.getOrDefault(emptyList())
        if (context.packageName !in allowed && !isInLockTask(context)) {
            runCatching { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Le menu du bouton marche/arrêt (éteindre, redémarrer) reste disponible.
            runCatching { dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) }
        }
        val keyguardDisabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            runCatching { dpm.setKeyguardDisabled(admin, true) }.getOrDefault(false)
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching { dpm.addPersistentPreferredActivity(admin, home, ComponentName(context, MainActivity::class.java)) }
        return KioskUiState(isOwner = true, keyguardDisabled = keyguardDisabled)
    }

    /**
     * Éteint vraiment l'écran (comme le bouton marche/arrêt). Le verrouillage Android étant désactivé,
     * un appui sur le bouton ramène directement à Canto. Retourne false si Android le refuse
     * (pas propriétaire, ou droit « force-lock » pas encore pris en compte : redémarrer le téléphone).
     */
    fun turnScreenOff(context: Context): Boolean =
        isOwner(context) && runCatching { dpm(context).lockNow() }.isSuccess

    /** Retire le statut de propriétaire : Canto redevient une app ordinaire, désinstallable. */
    fun removeOwner(context: Context): Boolean {
        if (!isOwner(context)) return true
        val dpm = dpm(context)
        val admin = admin(context)
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) runCatching { dpm.setKeyguardDisabled(admin, false) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        @Suppress("DEPRECATION")
        runCatching { dpm.clearDeviceOwnerApp(context.packageName) }
        return !isOwner(context)
    }

    /** Épinglage simple (avec message), par opposition au vrai mode kiosque du propriétaire. */
    fun isPinnedOnly(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
    }

    /** Déjà épinglé : ne pas relancer l'épinglage (sinon Android redemande confirmation). */
    fun isInLockTask(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }
}
