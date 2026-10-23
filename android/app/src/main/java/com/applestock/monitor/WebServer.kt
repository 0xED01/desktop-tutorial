package com.applestock.monitor

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

class HttpResponse(val code: Int, val type: String, val body: ByteArray) {
    companion object {
        fun text(code: Int, s: String, type: String = "text/plain; charset=utf-8") =
            HttpResponse(code, type, s.toByteArray(Charsets.UTF_8))
        fun json(s: String, code: Int = 200) = text(code, s, "application/json; charset=utf-8")
    }
}

/** 极简 HTTP 服务器（只用 java.net，无第三方依赖），给同一 Wi-Fi 下的浏览器提供控制页面。 */
class WebServer(private val port: Int, private val handler: (HttpRequest) -> HttpResponse) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    val isRunning get() = server?.isClosed == false

    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(port))
        server = s
        thread(name = "web-accept", isDaemon = true) {
            while (!s.isClosed) {
                val c = try {
                    s.accept()
                } catch (_: Exception) {
                    break
                }
                pool.execute { serve(c) }
            }
        }
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    private fun serve(c: Socket) {
        c.use { sock ->
            try {
                sock.soTimeout = 10000
                val input = BufferedInputStream(sock.getInputStream())
                val req = parse(input) ?: return
                val res = try {
                    handler(req)
                } catch (e: Exception) {
                    HttpResponse.text(500, "服务器错误：${e.javaClass.simpleName}")
                }
                val head = "HTTP/1.1 ${res.code} ${reason(res.code)}\r\n" +
                    "Content-Type: ${res.type}\r\n" +
                    "Content-Length: ${res.body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n"
                val out = sock.getOutputStream()
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(res.body)
                out.flush()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val MAX_BODY = 64 * 1024

        fun parse(input: InputStream): HttpRequest? {
            val headBytes = ByteArrayOutputStream()
            var last4 = 0
            while (true) {
                val b = input.read()
                if (b < 0) return null
                headBytes.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) break
                if (headBytes.size() > 16 * 1024) return null
            }
            val lines = headBytes.toString(Charsets.UTF_8.name()).split("\r\n")
            val parts = lines.first().split(" ")
            if (parts.size < 2) return null
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY)
            val body = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(body, read, len - read)
                if (n < 0) break
                read += n
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = HashMap<String, String>()
            if ('?' in target) {
                for (kv in target.substringAfter('?').split('&')) {
                    if (kv.isEmpty()) continue
                    val k = URLDecoder.decode(kv.substringBefore('='), "UTF-8")
                    val v = URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
                    query[k] = v
                }
            }
            return HttpRequest(parts[0].uppercase(), path, query, headers, String(body, 0, read, Charsets.UTF_8))
        }

        private fun reason(code: Int) = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }

        /** 本机在局域网里的 IPv4 地址（如 192.168.1.23），优先 Wi-Fi。 */
        fun lanAddress(): String? {
            val candidates = ArrayList<Pair<String, String>>()
            try {
                for (nif in NetworkInterface.getNetworkInterfaces()) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (addr in nif.inetAddresses) {
                        val ip = addr.hostAddress ?: continue
                        if (addr.isSiteLocalAddress && ':' !in ip) candidates.add(nif.name to ip)
                    }
                }
            } catch (_: Exception) {
            }
            return (candidates.firstOrNull { it.first.startsWith("wlan") } ?: candidates.firstOrNull())?.second
        }
    }
}
