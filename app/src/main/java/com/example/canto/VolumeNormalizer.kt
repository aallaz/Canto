package com.example.canto

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Normalisation du volume : un compresseur rapproche les passages (et les pistes) faibles et forts,
 * puis un limiteur évite toute saturation. Appliqué en direct pendant la lecture, sans modifier les fichiers.
 * Disponible à partir d'Android 9.
 */
class VolumeNormalizer {
    private var effect: DynamicsProcessing? = null

    /** Branche la normalisation sur la piste en cours (ou la retire si [enabled] est faux). */
    fun attach(audioSessionId: Int, enabled: Boolean) {
        release()
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) effect = runCatching { create(audioSessionId) }.getOrNull()
    }

    fun release() {
        effect?.let { runCatching { it.release() } }
        effect = null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun create(audioSessionId: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            false, 0, // pas d'égaliseur avant
            true, 1, // compresseur sur une seule bande (toutes les fréquences)
            false, 0, // pas d'égaliseur après
            true // limiteur
        ).build()
        return DynamicsProcessing(0, audioSessionId, config).apply {
            setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(
                    true,
                    20_000f, // toute la bande audio
                    20f, // attaque (ms)
                    300f, // relâchement (ms)
                    6f, // taux de compression
                    -40f, // seuil (dB)
                    10f, // genou (dB)
                    -90f, // pas de porte de bruit
                    1f,
                    0f,
                    14f // gain après compression : les passages faibles remontent
                )
            )
            setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            enabled = true
        }
    }

    companion object {
        private const val CHANNELS = 2
        val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
