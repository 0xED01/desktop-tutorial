package com.gomoku.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.io.BufferedWriter
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/*
 * 协议：TCP 文本行，字段以 '|' 分隔。
 *   C→H  HELLO|GMK1|昵称
 *   H→C  WELCOME|昵称
 *   H→C  NEW|1/0          新局，1 表示房主执黑
 *   MOVE|行|列
 *   UNDO? / UNDO_Y / UNDO_N
 *   AGAIN? / AGAIN_Y / AGAIN_N
 *   RESIGN, PING, BYE
 * 房间发现：房主每秒向 UDP 37521 广播  GMK1|昵称|TCP端口
 */
const val PROTO = "GMK1"
const val TCP_PORT = 37520
const val UDP_PORT = 37521

private val main = Handler(Looper.getMainLooper())

fun cleanName(s: String) = s.replace('|', ' ').replace('\n', ' ').trim().take(20).ifEmpty { "玩家" }

/** 本机 IPv4 地址（排除回环），Wi-Fi / 热点网卡优先。 */
fun localIpv4(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("ap") || n.name.startsWith("swlan")) 0 else 1 }
        .flatMap { n -> n.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" } }
        .filter { it.isNotEmpty() && !it.startsWith("127.") }
        .distinct()
}.getOrDefault(emptyList())

private fun broadcastTargets(): List<InetAddress> {
    val list = ArrayList<InetAddress>()
    runCatching {
        for (n in NetworkInterface.getNetworkInterfaces()) {
            if (!n.isUp || n.isLoopback) continue
            for (a in n.interfaceAddresses) a.broadcast?.let { list.add(it) }
        }
    }
    runCatching { list.add(InetAddress.getByName("255.255.255.255")) }
    return list.distinct()
}

/** 一条已建立的 TCP 连接。回调均在主线程执行。 */
class Connection(private val socket: Socket) {
    interface Listener {
        fun onMessage(parts: List<String>)
        fun onDisconnected()
    }

    var listener: Listener? = null
    @Volatile private var closed = false
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val sender: ExecutorService = Executors.newSingleThreadExecutor()
    private val pinger: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val peerAddress: String get() = socket.inetAddress?.hostAddress ?: ""

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = 15000
    }

    fun start() {
        thread(name = "gmk-read", isDaemon = true) {
            try {
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                while (!closed) {
                    val line = reader.readLine() ?: break
                    if (line == "PING") continue
                    val parts = line.split('|')
                    main.post { if (!closed) listener?.onMessage(parts) }
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: IOException) {
            }
            main.post {
                if (!closed) {
                    close(sendBye = false)
                    listener?.onDisconnected()
                }
            }
        }
        pinger.scheduleWithFixedDelay({ write("PING") }, 4, 4, TimeUnit.SECONDS)
    }

    fun send(vararg parts: Any) {
        if (closed) return
        val line = parts.joinToString("|")
        sender.execute { write(line) }
    }

    private fun write(line: String) {
        try {
            synchronized(writer) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        } catch (_: IOException) {
        }
    }

    fun close(sendBye: Boolean = true) {
        if (closed) return
        closed = true
        pinger.shutdownNow()
        sender.execute {
            if (sendBye) write("BYE")
            runCatching { socket.close() }
        }
        sender.shutdown()
    }
}

/** 房主：监听 TCP 连接并持续广播房间信息。 */
class Host(private val name: String, private val onClient: (Socket) -> Unit, private val onError: (String) -> Unit) {
    @Volatile private var stopped = false
    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    var port = TCP_PORT
        private set

    fun start() {
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(TCP_PORT)) }
        } catch (_: IOException) {
            try {
                ServerSocket(0)
            } catch (e: IOException) {
                onError("无法创建房间：${e.message}"); return
            }
        }
        server = ss
        port = ss.localPort
        thread(name = "gmk-accept", isDaemon = true) {
            try {
                val s = ss.accept()
                main.post { if (stopped) runCatching { s.close() } else onClient(s) }
            } catch (_: IOException) {
            }
        }
        thread(name = "gmk-beacon", isDaemon = true) {
            try {
                val sock = DatagramSocket().apply { broadcast = true }
                udp = sock
                val data = "$PROTO|${cleanName(name)}|$port".toByteArray(Charsets.UTF_8)
                while (!stopped) {
                    for (addr in broadcastTargets()) {
                        runCatching { sock.send(DatagramPacket(data, data.size, addr, UDP_PORT)) }
                    }
                    Thread.sleep(1000)
                }
            } catch (_: Exception) {
            } finally {
                udp?.close()
            }
        }
    }

    /** 停止广播与监听（已接受的连接不受影响）。 */
    fun stop() {
        stopped = true
        runCatching { server?.close() }
        runCatching { udp?.close() }
    }
}

data class Room(val name: String, val ip: String, val port: Int, var seen: Long)

/** 监听局域网内的房间广播。 */
class Scanner(ctx: Context, private val onRooms: (List<Room>) -> Unit) {
    @Volatile private var stopped = false
    private var socket: DatagramSocket? = null
    private val rooms = LinkedHashMap<String, Room>()
    private val lock: WifiManager.MulticastLock? = runCatching {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("gomoku").apply { setReferenceCounted(false) }
    }.getOrNull()
    private val ownIps = localIpv4().toSet()

    private val prune = object : Runnable {
        override fun run() {
            if (stopped) return
            val now = System.currentTimeMillis()
            if (rooms.values.removeAll { now - it.seen > 4000 }) onRooms(rooms.values.toList())
            main.postDelayed(this, 1000)
        }
    }

    fun start() {
        runCatching { lock?.acquire() }
        thread(name = "gmk-scan", isDaemon = true) {
            try {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_PORT))
                }
                socket = sock
                val buf = ByteArray(512)
                while (!stopped) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val parts = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim().split('|')
                    if (parts.size < 3 || parts[0] != PROTO) continue
                    val ip = pkt.address?.hostAddress ?: continue
                    val port = parts[2].toIntOrNull() ?: continue
                    if (ip in ownIps) continue
                    val name = parts[1]
                    main.post {
                        if (stopped) return@post
                        val key = "$ip:$port"
                        val existing = rooms[key]
                        if (existing == null || existing.name != name) {
                            rooms[key] = Room(name, ip, port, System.currentTimeMillis())
                            onRooms(rooms.values.toList())
                        } else {
                            existing.seen = System.currentTimeMillis()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        main.postDelayed(prune, 1000)
    }

    fun stop() {
        stopped = true
        main.removeCallbacks(prune)
        runCatching { socket?.close() }
        runCatching { lock?.release() }
    }
}

/** 连接到房主。回调在主线程执行。 */
fun connectTo(ip: String, port: Int, onResult: (Socket?, String?) -> Unit) {
    thread(name = "gmk-connect", isDaemon = true) {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(ip, port), 5000)
            main.post { onResult(s, null) }
        } catch (e: Exception) {
            runCatching { s.close() }
            main.post { onResult(null, "无法连接到 $ip") }
        }
    }
}
