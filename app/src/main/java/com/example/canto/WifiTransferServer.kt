package com.example.canto

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
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
 * d'avoir à décoder du multipart. POST /login échange le code parent contre un jeton de session,
 * transmis ensuite dans l'en-tête X-Canto-Token (ou ?t= pour les images).
 */
class WifiTransferServer(
    private val uploadPage: String,
    private val library: (Category) -> List<StoryFolder>,
    private val transferTarget: (Category) -> TransferTarget,
    /** Autorisation de stockage manquante, avec la manière de l'accorder (null si tout est accordé). */
    private val storageAccessProblem: () -> String?,
    private val checkCode: (String) -> Boolean,
    private val onFilesChanged: () -> Unit
) {
    @Volatile
    private var serverSocket: ServerSocket? = null
    private val tokens = ConcurrentHashMap.newKeySet<String>()

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
        tokens.clear()
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

            request.method == "POST" && request.path == "/login" -> {
                if (!checkCode(request.headers["x-canto-code"].orEmpty())) return respond(output, 401, TEXT, "Code incorrect")
                val token = UUID.randomUUID().toString()
                tokens += token
                respond(output, 200, TEXT, token)
            }

            request.method == "GET" && request.path == "/api/stories" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                respond(output, 200, "application/json; charset=utf-8", storiesJson(category(request)))
            }

            request.method == "GET" && request.path == "/api/status" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val target = transferTarget(category(request))
                val json = JSONObject()
                    .put("target", target.dir?.absolutePath ?: JSONObject.NULL)
                    .put("problem", target.problem ?: JSONObject.NULL)
                    .put("access", storageAccessProblem() ?: JSONObject.NULL)
                respond(output, 200, "application/json; charset=utf-8", json.toString())
            }

            request.method == "DELETE" && request.path == "/api/story" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
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
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val story = findFolder(request)
                    ?: return respond(output, 404, TEXT, "Histoire introuvable")
                respondZip(output, File(story.path))
            }

            request.method == "GET" && request.path == "/cover" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val cover = findFolder(request)?.coverPath?.let(::File)
                if (cover == null || !cover.isFile) return respond(output, 404, TEXT, "Pas d'image")
                respondFile(output, cover)
            }

            request.method == "GET" && request.path == "/folders" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val folders = transferTarget(category(request)).dir?.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sortedBy { it.lowercase() }
                    .orEmpty()
                respond(output, 200, TEXT, folders.joinToString("\n"))
            }

            // Fichiers déjà présents (nom + taille) pour ne pas les renvoyer.
            request.method == "GET" && request.path == "/files" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val folder = sanitize(request.query["folder"])
                    ?: return respond(output, 400, TEXT, "Nom de dossier invalide")
                val files = transferTarget(category(request)).dir?.let { File(it, folder).listFiles() }
                    ?.filter { it.isFile && !it.name.endsWith(".part") }
                    .orEmpty()
                respond(output, 200, TEXT, files.joinToString("\n") { "${it.name}\t${it.length()}" })
            }

            request.method == "POST" && request.path == "/upload" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
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

            else -> respond(output, 404, TEXT, "Introuvable")
        }
    }

    private fun isAuthorized(request: Request): Boolean {
        val token = request.headers["x-canto-token"] ?: request.query["t"]
        return (token != null && token in tokens) || checkCode(request.headers["x-canto-code"].orEmpty())
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
        private val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "wav", "aac", "ogg", "flac", "jpg", "jpeg", "png", "nfo")
    }
}
