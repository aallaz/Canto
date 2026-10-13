package com.example.canto

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

data class WifiNetworkUi(
    val ssid: String,
    /** Force du signal, de 0 à 4. */
    val level: Int,
    val secured: Boolean,
    /** Réseau déjà enregistré sur le téléphone : connexion sans mot de passe. */
    val saved: Boolean,
    val connected: Boolean
)

data class WifiUiState(
    val enabled: Boolean = false,
    val scanning: Boolean = false,
    val connectedSsid: String? = null,
    val networks: List<WifiNetworkUi> = emptyList()
)

/**
 * Wi-Fi depuis Canto : activer / désactiver, rechercher les réseaux et s'y connecter.
 *
 * Depuis Android 10, seules les apps propriétaires de l'appareil (mode kiosque) peuvent allumer le Wi-Fi
 * et ajouter un réseau ; la recherche demande l'autorisation « Position » et la localisation activée.
 */
@SuppressLint("MissingPermission")
class WifiNetworks(context: Context, private val onChange: (WifiUiState) -> Unit) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var scanning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) scanning = false
            publish()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        publish()
    }

    fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    /** Retourne false si Android refuse (Canto n'est pas propriétaire de l'appareil). */
    @Suppress("DEPRECATION")
    fun setEnabled(enabled: Boolean): Boolean {
        val done = runCatching { wifi.setWifiEnabled(enabled) }.getOrDefault(false)
        publish()
        return done
    }

    /** Lance une recherche ; la liste arrive quelques secondes plus tard. */
    @Suppress("DEPRECATION")
    fun scan() {
        scanning = runCatching { wifi.startScan() }.getOrDefault(false)
        publish()
    }

    /**
     * Se connecte à [ssid] : réseau enregistré, ou nouveau réseau avec [password] (null si ouvert).
     * Retourne un message d'erreur, ou null si la connexion est lancée.
     */
    @Suppress("DEPRECATION")
    fun connect(ssid: String, password: String?): String? {
        val quoted = "\"$ssid\""
        val saved = savedConfigurations().firstOrNull { it.SSID == quoted }
        val networkId = if (saved != null && password == null) {
            saved.networkId
        } else {
            val result = scanResults().firstOrNull { it.SSID == ssid }
            val capabilities = result?.capabilities.orEmpty()
            if ("EAP" in capabilities) return "Réseau d'entreprise (EAP) non pris en charge."
            if ("WEP" in capabilities) return "Réseau WEP non pris en charge."
            val config = WifiConfiguration().apply {
                SSID = quoted
                when {
                    password.isNullOrEmpty() -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    "PSK" !in capabilities && "SAE" in capabilities && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                        preSharedKey = "\"$password\""
                    }
                    else -> preSharedKey = "\"$password\""
                }
            }
            saved?.let { runCatching { wifi.removeNetwork(it.networkId) } }
            runCatching { wifi.addNetwork(config) }.getOrDefault(-1)
        }
        if (networkId < 0) {
            return "Android refuse d'ajouter le réseau : Canto doit être propriétaire de l'appareil (mode kiosque)."
        }
        runCatching {
            wifi.disconnect()
            wifi.enableNetwork(networkId, true)
            wifi.reconnect()
        }
        publish()
        return null
    }

    fun state(): WifiUiState {
        val enabled = runCatching { wifi.isWifiEnabled }.getOrDefault(false)
        if (!enabled) return WifiUiState(enabled = false)
        @Suppress("DEPRECATION")
        val connected = runCatching { wifi.connectionInfo?.ssid }.getOrNull()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val savedSsids = savedConfigurations().mapNotNull { it.SSID?.removeSurrounding("\"") }.toSet()
        val networks = scanResults()
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, results) ->
                val best = results.maxByOrNull { it.level }!!
                WifiNetworkUi(
                    ssid = ssid,
                    level = WifiManager.calculateSignalLevel(best.level, 5).coerceIn(0, 4),
                    secured = listOf("WEP", "PSK", "SAE", "EAP").any { it in best.capabilities },
                    saved = ssid in savedSsids,
                    connected = ssid == connected
                )
            }
            .sortedWith(compareByDescending<WifiNetworkUi> { it.connected }.thenByDescending { it.level })
        return WifiUiState(enabled = true, scanning = scanning, connectedSsid = connected, networks = networks)
    }

    private fun scanResults(): List<ScanResult> = runCatching { wifi.scanResults }.getOrNull().orEmpty()

    @Suppress("DEPRECATION")
    private fun savedConfigurations(): List<WifiConfiguration> = runCatching { wifi.configuredNetworks }.getOrNull().orEmpty()

    private fun publish() = onChange(state())
}
