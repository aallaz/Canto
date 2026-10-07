package com.example.canto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

data class BluetoothSpeaker(
    val address: String,
    val name: String,
    val isBonded: Boolean,
    val isConnected: Boolean
)

data class BluetoothUiState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val speakers: List<BluetoothSpeaker> = emptyList()
)

/**
 * Enceintes Bluetooth (profil A2DP) : appairage, connexion, déconnexion.
 *
 * Android n'offre pas d'API publique pour connecter un appareil déjà appairé : on passe par
 * BluetoothA2dp.connect() en réflexion, avec repli sur les réglages Bluetooth du système.
 * Toutes les opérations supposent les permissions Bluetooth accordées (SecurityException sinon).
 */
@SuppressLint("MissingPermission")
class BluetoothSpeakers(
    private val context: Context,
    private val onChange: (BluetoothUiState) -> Unit
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var a2dp: BluetoothA2dp? = null
    private val discovered = LinkedHashMap<String, BluetoothDevice>()
    private var started = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            a2dp = proxy as? BluetoothA2dp
            publish()
        }

        override fun onServiceDisconnected(profile: Int) {
            a2dp = null
            publish()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && isAudioDevice(device)) discovered[device.address] = device
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    // La plupart des enceintes se connectent seules après l'appairage ; sinon on insiste.
                    if (device != null && state == BluetoothDevice.BOND_BONDED) connectA2dp(device)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (adapter?.isEnabled == true && a2dp == null) openProfileProxy()
                }
            }
            publish()
        }
    }

    val isAvailable: Boolean
        get() = adapter != null

    fun start() {
        if (started || adapter == null) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        openProfileProxy()
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { adapter?.cancelDiscovery() }
        runCatching { context.unregisterReceiver(receiver) }
        a2dp?.let { proxy -> runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy) } }
        a2dp = null
    }

    /** Active le Bluetooth. Retourne false si Android exige l'écran système (Android 13+). */
    fun enable(): Boolean {
        @Suppress("DEPRECATION")
        return runCatching { adapter?.enable() == true }.getOrDefault(false)
    }

    fun scan() {
        runCatching {
            adapter?.cancelDiscovery()
            adapter?.startDiscovery()
        }
        publish()
    }

    /** Appaire (nouvelle enceinte) ou connecte (enceinte déjà appairée). */
    fun connect(address: String): Boolean {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        runCatching { adapter?.cancelDiscovery() }
        val ok = if (device.bondState == BluetoothDevice.BOND_BONDED) connectA2dp(device) else runCatching { device.createBond() }.getOrDefault(false)
        publish()
        return ok
    }

    fun disconnect(address: String): Boolean {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        val ok = invokeA2dp("disconnect", device)
        publish()
        return ok
    }

    private fun connectA2dp(device: BluetoothDevice): Boolean = invokeA2dp("connect", device)

    private fun invokeA2dp(method: String, device: BluetoothDevice): Boolean {
        val proxy = a2dp ?: return false
        return runCatching {
            BluetoothA2dp::class.java.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as Boolean
        }.getOrDefault(false)
    }

    private fun openProfileProxy() {
        runCatching { adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP) }
    }

    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        val bluetoothClass = runCatching { device.bluetoothClass }.getOrNull() ?: return true
        return bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            bluetoothClass.hasService(BluetoothClass.Service.AUDIO) ||
            bluetoothClass.hasService(BluetoothClass.Service.RENDER)
    }

    fun state(): BluetoothUiState {
        val adapter = adapter ?: return BluetoothUiState()
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) return BluetoothUiState(isAvailable = true)

        val connected = runCatching { a2dp?.connectedDevices.orEmpty() }.getOrDefault(emptyList()).map { it.address }.toSet()
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet()).filter(::isAudioDevice)
        val devices = (bonded + discovered.values).distinctBy { it.address }
        val speakers = devices.map { device ->
            BluetoothSpeaker(
                address = device.address,
                name = runCatching { device.name }.getOrNull() ?: device.address,
                isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
                isConnected = device.address in connected
            )
        }.sortedWith(compareByDescending<BluetoothSpeaker> { it.isConnected }.thenByDescending { it.isBonded }.thenBy { it.name })

        return BluetoothUiState(
            isAvailable = true,
            isEnabled = true,
            isScanning = runCatching { adapter.isDiscovering }.getOrDefault(false),
            speakers = speakers
        )
    }

    private fun publish() = onChange(state())
}
