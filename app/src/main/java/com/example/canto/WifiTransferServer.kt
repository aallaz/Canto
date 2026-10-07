package com.example.canto

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Mini serveur HTTP pour envoyer des histoires depuis un navigateur sur le même Wi-Fi.
 *
 * Chaque fichier est envoyé brut (POST /upload?folder=...&name=...), ce qui évite
 * d'avoir à décoder du multipart. Le code parent est exigé dans l'en-tête X-Canto-Code.
 */
class WifiTransferServer(
    private val targetRoot: () -> File,
    private val checkCode: (String) -> Boolean,
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
                respond(output, 200, "text/html; charset=utf-8", UPLOAD_PAGE)

            request.method == "GET" && request.path == "/folders" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val folders = targetRoot().listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sortedBy { it.lowercase() }
                    .orEmpty()
                respond(output, 200, TEXT, folders.joinToString("\n"))
            }

            request.method == "POST" && request.path == "/upload" -> {
                if (!isAuthorized(request)) return respond(output, 401, TEXT, "Code incorrect")
                val folder = sanitize(request.query["folder"])
                val name = sanitize(request.query["name"])
                val length = request.headers["content-length"]?.toLongOrNull()
                when {
                    folder == null || name == null -> respond(output, 400, TEXT, "Nom de dossier ou de fichier invalide")
                    name.substringAfterLast('.', "").lowercase() !in ALLOWED_EXTENSIONS ->
                        respond(output, 415, TEXT, "Type de fichier refusé : $name")
                    length == null || length < 0 -> respond(output, 411, TEXT, "Taille manquante")
                    else -> {
                        val saved = saveFile(folder, name, length, input)
                        if (saved) {
                            onFilesChanged()
                            respond(output, 200, TEXT, "OK")
                        } else {
                            respond(output, 500, TEXT, "Écriture impossible dans ${targetRoot().absolutePath}")
                        }
                    }
                }
            }

            else -> respond(output, 404, TEXT, "Introuvable")
        }
    }

    private fun isAuthorized(request: Request): Boolean {
        return checkCode(request.headers["x-canto-code"].orEmpty())
    }

    private fun saveFile(folder: String, name: String, length: Long, input: InputStream): Boolean {
        val dir = File(targetRoot(), folder)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val partial = File(dir, "$name.part")
        val target = File(dir, name)
        return runCatching {
            partial.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) error("Connexion interrompue")
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }
            if (target.exists()) target.delete()
            partial.renameTo(target)
        }.getOrElse {
            partial.delete()
            false
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
        private val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "wav", "aac", "ogg", "jpg", "jpeg", "png", "nfo")

        private val UPLOAD_PAGE = """
            <!doctype html>
            <html lang="fr"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Canto – Envoi d'histoires</title>
            <style>
              body{font-family:sans-serif;background:#141318;color:#e8e2cf;max-width:640px;margin:0 auto;padding:16px}
              h1{color:#d4a23a} label{display:block;margin-top:16px;font-weight:bold}
              input,button{font-size:18px;padding:10px;width:100%;box-sizing:border-box;margin-top:6px}
              button{background:#d4a23a;border:3px solid #000;font-weight:bold;cursor:pointer}
              #log{white-space:pre-wrap;margin-top:16px;font-family:monospace}
            </style></head><body>
            <h1>Boîte à histoires</h1>
            <p>Choisis un dossier (une tuile) puis ajoute les fichiers audio et l'image <b>cover.jpg</b>.</p>
            <label>Code parent<input id="code" type="password" inputmode="numeric"></label>
            <label>Dossier<input id="folder" list="folders" placeholder="01_Boucle_d_or"></label>
            <datalist id="folders"></datalist>
            <label>Fichiers<input id="files" type="file" multiple accept="audio/*,image/*,.nfo"></label>
            <button id="send">Envoyer</button>
            <div id="log"></div>
            <script>
              const ${'$'} = id => document.getElementById(id);
              const log = t => ${'$'}('log').textContent += t + "\n";
              ${'$'}('code').addEventListener('change', async () => {
                const r = await fetch('/folders', {headers:{'X-Canto-Code':${'$'}('code').value}});
                if (!r.ok) { log('Code incorrect'); return; }
                ${'$'}('folders').innerHTML = '';
                (await r.text()).split('\n').filter(Boolean).forEach(f => {
                  const o = document.createElement('option'); o.value = f; ${'$'}('folders').appendChild(o);
                });
              });
              ${'$'}('send').addEventListener('click', async () => {
                const folder = ${'$'}('folder').value.trim();
                const files = ${'$'}('files').files;
                if (!folder || !files.length) { log('Indique un dossier et des fichiers.'); return; }
                for (const f of files) {
                  log('Envoi de ' + f.name + '…');
                  const url = '/upload?folder=' + encodeURIComponent(folder) + '&name=' + encodeURIComponent(f.name);
                  const r = await fetch(url, {method:'POST', body:f, headers:{'X-Canto-Code':${'$'}('code').value}});
                  log((r.ok ? '✔ ' : '✘ ') + f.name + (r.ok ? '' : ' : ' + await r.text()));
                }
                log('Terminé.');
              });
            </script></body></html>
        """.trimIndent()
    }
}
