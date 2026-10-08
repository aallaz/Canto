package com.example.canto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager

/**
 * Coupe le Wi-Fi et le Bluetooth pendant la veille profonde, puis les rétablit au réveil.
 *
 * Android ne laisse couper le Wi-Fi qu'au propriétaire de l'appareil (mode kiosque), et le Bluetooth
 * qu'avec l'autorisation « Appareils à proximité » sur Android 12+ : sinon la radio reste allumée.
 * L'état d'avant est mémorisé dans les préférences, pour être rétabli même si Canto a redémarré entre-temps.
 */
@SuppressLint("MissingPermission")
class DeepSleepRadios(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("canto_veille", Context.MODE_PRIVATE)
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetooth get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("DEPRECATION")
    fun switchOff() {
        val wifiWasOn = runCatching { wifi.isWifiEnabled }.getOrDefault(false)
        val bluetoothWasOn = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)
        // Mémorisé avant de couper : un redémarrage au milieu ne doit pas laisser les radios éteintes.
        prefs.edit()
            .putBoolean(KEY_WIFI, wifiWasOn || prefs.getBoolean(KEY_WIFI, false))
            .putBoolean(KEY_BLUETOOTH, bluetoothWasOn || prefs.getBoolean(KEY_BLUETOOTH, false))
            .commit()
        if (wifiWasOn) runCatching { wifi.setWifiEnabled(false) }
        if (bluetoothWasOn) runCatching { bluetooth?.disable() }
    }

    /** Rallume ce qui était allumé avant la veille profonde (sans effet si rien n'a été coupé). */
    @Suppress("DEPRECATION")
    fun restore() {
        if (prefs.getBoolean(KEY_WIFI, false)) runCatching { wifi.setWifiEnabled(true) }
        if (prefs.getBoolean(KEY_BLUETOOTH, false)) runCatching { bluetooth?.enable() }
        prefs.edit().remove(KEY_WIFI).remove(KEY_BLUETOOTH).apply()
    }

    private companion object {
        const val KEY_WIFI = "wifi_avant_veille"
        const val KEY_BLUETOOTH = "bluetooth_avant_veille"
    }
}
