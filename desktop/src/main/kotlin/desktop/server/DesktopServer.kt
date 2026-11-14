package desktop.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Server HTTP lokal untuk UI web. Pengganti LocalHttpServer (ServerSocket mentah di Android) memakai
 * com.sun.net.httpserver yang ada di JDK, jadi tidak menambah dependensi.
 *
 * Keamanan, berbeda dari versi HP:
 *  - hanya mendengarkan 127.0.0.1, tidak terlihat dari jaringan Wi-Fi/LAN;
 *  - tanpa header CORS "*", jadi situs lain di browser tidak bisa membaca data lewat localhost;
 *  - Host dan Origin diperiksa untuk menahan DNS-rebinding dan permintaan lintas-situs.
 */
class DesktopServer(
    private val api: Api,
    private val pageHtml: () -> String,
    private val assets: AssetSource,
    preferredPort: Int,
) {
    private val http: HttpServer
    val port: Int

    init {
        val loopback = InetAddress.getLoopbackAddress()
        var bound: HttpServer? = null
        // Port dipertahankan antar peluncuran: localStorage browser terikat ke asal (host:port).
        for (candidate in preferredPort..preferredPort + 20) {
            try {
                bound = HttpServer.create(InetSocketAddress(loopback, candidate), 0)
                break
            } catch (_: BindException) {
                // port dipakai program lain, coba berikutnya
            }
        }
        http = bound ?: HttpServer.create(InetSocketAddress(loopback, 0), 0)
        port = http.address.port
        http.executor = Executors.newCachedThreadPool { r -> Thread(r, "singghara-http").apply { isDaemon = true } }
        http.createContext("/") { ex -> handle(ex) }
    }

    val url: String get() = "http://127.0.0.1:$port"

    fun start() = http.start()
    fun stop() = http.stop(0)

    private fun handle(ex: HttpExchange) {
        try {
            if (!isLocalRequest(ex)) {
                send(ex, 403, "text/plain; charset=utf-8", "Forbidden".toByteArray())
                return
            }
            val path = ex.requestURI.rawPath ?: "/"
            val method = ex.requestMethod.uppercase()

            when {
                method == "OPTIONS" -> send(ex, 204, "text/plain", ByteArray(0))
                path in PAGE_PATHS -> send(ex, 200, "text/html; charset=utf-8", pageHtml().toByteArray(Charsets.UTF_8), cache = false)
                path == "/api/clock-font" -> serveClockFont(ex)
                path.startsWith("/api/") -> serveApi(ex, path, method)
                else -> serveAsset(ex, path)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            runCatching { send(ex, 500, "text/plain; charset=utf-8", "Kesalahan server".toByteArray()) }
        } finally {
            ex.close()
        }
    }

    private fun serveApi(ex: HttpExchange, path: String, method: String) {
        val body = ex.requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        if (body.size > MAX_BODY_BYTES) {
            send(ex, 413, "text/plain; charset=utf-8", "Berkas terlalu besar".toByteArray())
            return
        }
        val reply = try {
            runBlocking(Dispatchers.IO) {
                api.handle(path, method, parseQuery(ex.requestURI.rawQuery), body.toString(Charsets.UTF_8))
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Reply(500, failure("Kesalahan server: ${t.message}"))
        }
        send(ex, reply.status, "application/json; charset=utf-8", reply.body.toString().toByteArray(Charsets.UTF_8), cache = false)
    }

    private fun serveClockFont(ex: HttpExchange) {
        val bytes = assets.read("fonts/Technology-Bold.ttf") ?: assets.read("fonts/Technology.ttf")
        if (bytes == null) send(ex, 404, "text/plain; charset=utf-8", "Font tidak ditemukan".toByteArray())
        else send(ex, 200, "font/ttf", bytes, cache = true)
    }

    private fun serveAsset(ex: HttpExchange, rawPath: String) {
        val clean = URLDecoder.decode(rawPath, Charsets.UTF_8).removePrefix("/").removePrefix("assets/")
        if (clean.contains("..") || clean.contains('\\')) {
            send(ex, 400, "text/plain; charset=utf-8", "Bad Request".toByteArray())
            return
        }
        val name = clean.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        // Urutan pencarian sama dengan LocalHttpServer di Android, termasuk aturan favicon/logo.
        val candidates = buildList {
            add("web/$clean")
            when (ext) {
                "css" -> add("web/css/$name")
                "js" -> add("web/js/$name")
                "woff", "woff2", "ttf" -> add("web/css/fonts/$name")
            }
            add("web/$name")
            if (name.startsWith("favicon") || name.startsWith("app-logo") || name.startsWith("apple-touch-icon") || name == "logo.png") {
                add(if (name == "logo.png") "web/app-logo.png" else "web/favicon.ico")
            }
        }
        val hit = candidates.firstNotNullOfOrNull { c -> assets.read(c)?.let { c to it } }
        if (hit == null) {
            send(ex, 404, "text/plain; charset=utf-8", "Asset Not Found: $rawPath".toByteArray())
        } else {
            send(ex, 200, mimeOf(hit.first), hit.second, cache = rawPath != "/sw.js")
        }
    }

    private fun send(ex: HttpExchange, status: Int, contentType: String, body: ByteArray, cache: Boolean = false) {
        ex.responseHeaders.set("Content-Type", contentType)
        ex.responseHeaders.set(
            "Cache-Control",
            if (cache) "public, max-age=86400" else "no-store, must-revalidate"
        )
        ex.responseHeaders.set("X-Content-Type-Options", "nosniff")
        if (status == 204 || body.isEmpty()) {
            ex.sendResponseHeaders(status, -1)
        } else {
            ex.sendResponseHeaders(status, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
    }

    private fun isLocalRequest(ex: HttpExchange): Boolean {
        val allowedHosts = setOf("127.0.0.1:$port", "localhost:$port", "[::1]:$port")
        val host = ex.requestHeaders.getFirst("Host")?.lowercase() ?: return false
        if (host !in allowedHosts) return false
        val origin = ex.requestHeaders.getFirst("Origin")?.lowercase() ?: return true
        return origin.removePrefix("http://") in allowedHosts
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split("&").filter { it.isNotEmpty() }.associate {
            val key = URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8)
            val value = URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
            key to value
        }
    }

    private fun mimeOf(resource: String): String = when (resource.substringAfterLast('.').lowercase()) {
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "html" -> "text/html; charset=utf-8"
        "json", "webmanifest" -> "application/manifest+json; charset=utf-8"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_BODY_BYTES = 128 * 1024 * 1024 - 1
        val PAGE_PATHS = setOf(
            "/", "/index.html", "/ht", "/handy_talky", "/walkie_talkie", "/telegram_ai", "/telegram", "/tg"
        )
    }
}

/** Sumber aset statis. Produksi: classpath (folder assets Android ikut dikemas). Uji: folder biasa. */
fun interface AssetSource {
    fun read(path: String): ByteArray?
}

object ClasspathAssets : AssetSource {
    override fun read(path: String): ByteArray? =
        ClasspathAssets::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
}
