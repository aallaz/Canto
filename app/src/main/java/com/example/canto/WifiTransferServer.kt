package com.example.canto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Dossier où écrire les histoires reçues, ou la raison pour laquelle aucun n'est accessible. */
data class TransferTarget(val dir: File?, val problem: String?)

/**
 * Mini serveur HTTP : bibliothèque des histoires et envoi depuis un navigateur sur le même Wi-Fi.
 *
 * Chaque fichier est envoyé brut (POST /upload?folder=...&name=...), ce qui évite
 * d'avoir à décoder du multipart. Pas de code : la page est ouverte à tout appareil du même Wi-Fi,
 * et le serveur ne tourne que pendant un transfert démarré depuis les réglages.
 */
class WifiTransferServer(
    private val uploadPage: String,
    private val library: (Category) -> List<StoryFolder>,
    private val transferTarget: (Category) -> TransferTarget,
    /** Autorisation de stockage manquante, avec la manière de l'accorder (null si tout est accordé). */
    private val storageAccessProblem: () -> String?,
    /** Dossier des vignettes de pochettes (cache de l'app). */
    private val cacheDir: File,
    private val onFilesChanged: () -> Unit
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    fun start(): Boolean {
        if (isRunning) return true
        val socket = runCatching { ServerSocket(PORT) }.getOrNull() ?: return false
        serverSocket = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({ handle(client) }, "canto-http-client").start()
            }
        }, "canto-http").start()
        return true
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** Adresse à taper dans le navigateur, ou null si pas de Wi-Fi. */
    fun url(): String? {
        val address = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { it.name.startsWith("wlan") }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull() ?: return null
        return "http://$address:$PORT"
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 30_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                val request = readRequest(input) ?: return
                route(request, input, output)
            }
        }
    }

    private fun route(request: Request, input: InputStream, output: OutputStream) {
        when {
            request.method == "GET" && request.path == "/" ->
                respond(output, 200, "text/html; charset=utf-8", uploadPage)

            request.method == "GET" && request.path == "/api/stories" -> {
                respond(output, 200, "application/json; charset=utf-8", storiesJson(category(request)))
            }

            request.method == "GET" && request.path == "/api/status" -> {
                val target = transferTarget(category(request))
                val json = JSONObject()
                    .put("target", target.dir?.absolutePath ?: JSONObject.NULL)
                    .put("problem", target.problem ?: JSONObject.NULL)
                    .put("access", storageAccessProblem() ?: JSONObject.NULL)
                respond(output, 200, "application/json; charset=utf-8", json.toString())
            }

            request.method == "DELETE" && request.path == "/api/story" -> {
                val story = findFolder(request)
                    ?: return respond(output, 404, TEXT, "Histoire introuvable")
                val dir = File(story.path)
                dir.deleteRecursively()
                onFilesChanged()
                if (dir.exists()) {
                    val cause = storageAccessProblem()
                        ?: "le dossier ${dir.parent} est en lecture seule (sur Android 10 et moins, la carte SD n'est pas modifiable par les applications)."
                    respond(output, 500, TEXT, "Suppression impossible : $cause")
                } else {
                    respond(output, 200, TEXT, "OK")
                }
            }

            request.method == "GET" && request.path == "/download" -> {
                val story = findFolder(request)
                    ?: return respond(output, 404, TEXT, "Histoire introuvable")
                respondZip(output, File(story.path))
            }

            request.method == "GET" && request.path == "/cover" -> {
                val cover = findFolder(request)?.coverPath?.let(::File)
                if (cover == null || !cover.isFile) return respond(output, 404, TEXT, "Pas d'image")
                // Vignette réduite : les pochettes d'origine (souvent plusieurs Mo) rendaient la page très lente.
                respondFile(output, thumbnail(cover) ?: cover)
            }

            request.method == "GET" && request.path == "/folders" -> {
                val folders = transferTarget(category(request)).dir?.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sortedBy { it.lowercase() }
                    .orEmpty()
                respond(output, 200, TEXT, folders.joinToString("\n"))
            }

            // Fichiers déjà présents (nom + taille) pour ne pas les renvoyer.
            request.method == "GET" && request.path == "/files" -> {
                val folder = sanitize(request.query["folder"])
                    ?: return respond(output, 400, TEXT, "Nom de dossier invalide")
                val files = transferTarget(category(request)).dir?.let { File(it, folder).listFiles() }
                    ?.filter { it.isFile && !it.name.endsWith(".part") }
                    .orEmpty()
                respond(output, 200, TEXT, files.joinToString("\n") { "${it.name}\t${it.length()}" })
            }

            request.method == "POST" && request.path == "/upload" -> {
                val folder = sanitize(request.query["folder"])
                val name = sanitize(request.query["name"])
                val length = request.headers["content-length"]?.toLongOrNull()
                    ?: return respond(output, 411, TEXT, "Taille manquante")
                val target = transferTarget(category(request))
                // Le corps est toujours lu en entier : sinon le navigateur ne voit qu'une connexion coupée.
                val error = when {
                    folder == null || name == null -> "Nom de dossier ou de fichier invalide".also { drain(input, length) }
                    name.substringAfterLast('.', "").lowercase() !in ALLOWED_EXTENSIONS ->
                        "Type de fichier refusé : $name".also { drain(input, length) }
                    target.dir == null -> (target.problem ?: "Aucun dossier accessible en écriture").also { drain(input, length) }
                    else -> receiveFile(File(target.dir, folder), name, length, input)
                }
                if (error == null) {
                    onFilesChanged()
                    respond(output, 200, TEXT, "OK")
                } else {
                    respond(output, 500, TEXT, error)
                }
            }

            // Pochette depuis un lien web : la boîte télécharge elle-même l'image (pas de blocage du navigateur).
            request.method == "POST" && request.path == "/api/cover-url" -> {
                val folder = sanitize(request.query["folder"])
                    ?: return respond(output, 400, TEXT, "Nom de dossier invalide")
                val link = request.query["url"].orEmpty()
                val dir = transferTarget(category(request)).dir?.let { File(it, folder) }
                    ?: return respond(output, 500, TEXT, "Aucun dossier accessible en écriture")
                val error = downloadCover(link, dir)
                if (error == null) {
                    onFilesChanged()
                    respond(output, 200, TEXT, "OK")
                } else {
                    respond(output, 500, TEXT, error)
                }
            }

            else -> respond(output, 404, TEXT, "Introuvable")
        }
    }

    /** Rubrique visée par la requête (?c=music pour la musique, histoires par défaut). */
    private fun category(request: Request): Category =
        if (request.query["c"] == "music") Category.Music else Category.Stories

    /** Histoire ou album désigné par son chemin (?id=...), dans l'une ou l'autre rubrique. */
    private fun findFolder(request: Request): StoryFolder? =
        Category.values().asSequence().flatMap { library(it).asSequence() }.firstOrNull { it.path == request.query["id"] }

    private fun storiesJson(category: Category): String {
        val stories = JSONArray()
        library(category).forEach { story ->
            val files = listAudioFiles(File(story.path))
            stories.put(
                JSONObject()
                    .put("id", story.path)
                    .put("folder", story.name)
                    .put("title", story.displayTitle())
                    .put("hasCover", story.coverPath != null)
                    .put("bytes", files.sumOf { it.length() })
                    .put("tracks", JSONArray(files.mapIndexed { index, file -> story.trackName(file, index) }))
            )
        }
        return stories.toString()
    }

    /** Écrit le fichier reçu ; retourne null si tout va bien, sinon le message d'erreur. */
    private fun receiveFile(dir: File, name: String, length: Long, input: InputStream): String? {
        val partial = File(dir, "$name.part")
        val target = File(dir, name)
        var problem: String? = null
        val out = runCatching {
            check(dir.isDirectory || dir.mkdirs()) { "impossible de créer le dossier" }
            partial.outputStream()
        }.getOrElse {
            problem = "Écriture impossible dans ${dir.absolutePath} : ${it.message}"
            null
        }

        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            // Page rechargée ou fermée pendant l'envoi : la lecture échoue (ou expire) au lieu de finir.
            val read = runCatching { input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()) }.getOrDefault(-1)
            if (read < 0) {
                problem = problem ?: "Connexion interrompue"
                break
            }
            if (out != null && problem == null) {
                runCatching { out.write(buffer, 0, read) }.onFailure {
                    problem = "Écriture impossible (${it.message}) : stockage plein ?"
                }
            }
            remaining -= read
        }
        runCatching { out?.close() }

        if (problem == null) {
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) problem = "Impossible de renommer ${partial.name}"
        }
        if (problem != null) partial.delete()
        return problem
    }

    private fun drain(input: InputStream, length: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) return
            remaining -= read
        }
    }

    /** Dossier d'histoire en .zip, envoyé au fil de l'eau (sans compression : l'audio ne se compresse pas). */
    private fun respondZip(output: OutputStream, dir: File) {
        val asciiName = dir.name.replace(Regex("[^A-Za-z0-9 ._-]"), "_")
        val encodedName = URLEncoder.encode(dir.name, "UTF-8").replace("+", "%20")
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/zip\r\n" +
            "Content-Disposition: attachment; filename=\"$asciiName.zip\"; filename*=UTF-8''$encodedName.zip\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry("${dir.name}/${file.name}").apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val byte = input.read()
            if (byte < 0 || head.size() > MAX_HEADER_SIZE) return null
            head.write(byte)
            matched = when {
                (matched == 0 || matched == 2) && byte == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && byte == '\n'.code -> matched + 1
                byte == '\r'.code -> 1
                else -> 0
            }
        }

        val lines = head.toString("UTF-8").split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val headers = lines.drop(1)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val target = parts[1]
        val query = target.substringAfter('?', "")
            .split('&')
            .filter { '=' in it }
            .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=')) }
        return Request(parts[0].uppercase(), target.substringBefore('?'), query, headers)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun sanitize(value: String?): String? {
        val cleaned = value
            ?.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
            ?.trim()
            ?.trimStart('.')
            ?.take(120)
        return cleaned?.takeIf { it.isNotEmpty() }
    }

    /** Vignette JPEG d'au plus [THUMBNAIL_SIZE] px, gardée en cache tant que la pochette ne change pas. */
    private fun thumbnail(cover: File): File? = runCatching {
        val dir = File(cacheDir, "vignettes").apply { mkdirs() }
        val key = "${cover.absolutePath}|${cover.lastModified()}|${cover.length()}".hashCode().toUInt().toString(16)
        val thumb = File(dir, "$key.jpg")
        if (thumb.isFile) return@runCatching thumb
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cover.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMBNAIL_SIZE) sample *= 2
        val decoded = BitmapFactory.decodeFile(cover.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val scale = THUMBNAIL_SIZE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        val partial = File(dir, "$key.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (!partial.renameTo(thumb)) partial.delete()
        thumb.takeIf { it.isFile }
    }.getOrNull()

    /** Télécharge une image (jpg, png, ou autre format converti en jpg) comme cover du dossier ; null si tout va bien. */
    private fun downloadCover(link: String, dir: File): String? {
        if (!link.startsWith("http://") && !link.startsWith("https://")) return "Lien invalide : il doit commencer par http:// ou https://"
        val bytes = runCatching {
            val connection = URL(link).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Canto")
            try {
                if (connection.responseCode !in 200..299) error("le site répond ${connection.responseCode}")
                val buffer = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        buffer.write(chunk, 0, read)
                        if (buffer.size() > MAX_COVER_BYTES) error("image trop lourde (plus de 15 Mo)")
                    }
                }
                buffer.toByteArray()
            } finally {
                connection.disconnect()
            }
        }.getOrElse { return "Téléchargement impossible (${it.message}). La boîte est-elle connectée à Internet ?" }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return "Ce lien ne mène pas à une image."
        val isPng = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        return runCatching {
            check(dir.isDirectory || dir.mkdirs()) { "impossible de créer le dossier" }
            // Une seule pochette : l'ancienne (autre format) ne doit pas rester prioritaire.
            dir.listFiles()?.filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }?.forEach { it.delete() }
            val target = File(dir, if (isPng) "cover.png" else "cover.jpg")
            if (isPng || isJpeg) {
                target.writeBytes(bytes)
            } else {
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
        }.exceptionOrNull()?.let { "Écriture impossible dans ${dir.absolutePath} : ${it.message}" }
    }

    private fun respondFile(output: OutputStream, file: File) {
        val type = when (file.extension.lowercase()) {
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${file.length()}\r\n" +
            "Cache-Control: private, max-age=600\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        file.inputStream().use { it.copyTo(output) }
        output.flush()
    }

    private fun respond(output: OutputStream, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray()
        val reason = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>
    )

    companion object {
        const val PORT = 8080
        private const val TEXT = "text/plain; charset=utf-8"
        private const val MAX_HEADER_SIZE = 16 * 1024
        private const val THUMBNAIL_SIZE = 320
        private const val MAX_COVER_BYTES = 15 * 1024 * 1024
        private val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "wav", "aac", "ogg", "flac", "jpg", "jpeg", "png", "nfo")
    }
}
