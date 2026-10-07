package com.example.canto

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * Cherche le dossier "Histoires" en priorité sur la carte SD, puis sur le stockage interne.
 */
object StorageLocator {
    const val STORIES_DIR_NAME = "Histoires"

    data class StoriesRoot(val dir: File, val isRemovable: Boolean)

    /** Dossiers "Histoires" existants, carte SD d'abord. */
    fun existingRoots(context: Context): List<StoriesRoot> {
        val removable = removableVolumes(context).map { StoriesRoot(it, isRemovable = true) }
        val primary = primaryVolumes().map { StoriesRoot(it, isRemovable = false) }

        return (removable + primary)
            .mapNotNull { volume -> findStoriesDir(volume.dir)?.let { StoriesRoot(it, volume.isRemovable) } }
            .distinctBy { runCatching { it.dir.canonicalPath }.getOrDefault(it.dir.absolutePath) }
    }

    /** Dossier "Histoires" du stockage interne, créé si besoin (cible par défaut des transferts). */
    fun localRoot(): File {
        val volume = primaryVolumes().firstOrNull() ?: Environment.getExternalStorageDirectory()
        return findStoriesDir(volume) ?: File(volume, STORIES_DIR_NAME)
    }

    private fun findStoriesDir(volume: File): File? {
        val direct = File(volume, STORIES_DIR_NAME)
        if (direct.isDirectory) return direct
        return volume.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals(STORIES_DIR_NAME, ignoreCase = true) }
    }

    private fun primaryVolumes(): List<File> {
        return listOf(
            Environment.getExternalStorageDirectory(),
            File("/sdcard"),
            File("/storage/emulated/0")
        ).filter { it.isDirectory }
    }

    private fun removableVolumes(context: Context): List<File> {
        val volumes = mutableListOf<File>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes
                ?.filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                ?.mapNotNullTo(volumes) { it.directory }
        }

        // Les dossiers propres à l'app révèlent la racine de chaque volume monté.
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .filter { dir -> runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false) }
            .mapNotNullTo(volumes) { dir -> volumeRootOf(dir) }

        // Dernier recours : volumes listés sous /storage (ex. /storage/1234-ABCD).
        File("/storage").listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }
            ?.let(volumes::addAll)

        return volumes.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    private fun volumeRootOf(appDir: File): File? {
        val path = appDir.absolutePath
        val index = path.indexOf("/Android/")
        return if (index > 0) File(path.substring(0, index)) else null
    }
}
