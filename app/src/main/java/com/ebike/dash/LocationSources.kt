package com.ebike.dash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.sin

fun interface LocationSink {
    fun onLocation(loc: Location)
}

/**
 * 直接使用系统 GPS（不依赖谷歌服务，国内手机可用）。
 * 以 GPS 为主，网络定位只用于冷启动时快速给出大致位置。
 */
class GpsSource(private val ctx: Context, private val sink: LocationSink) : LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastGpsAt = 0L
    var running = false; private set

    fun hasPermission() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        running = true
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
            }
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            last?.let { sink.onLocation(it) }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!running) return
        running = false
        lm.removeUpdates(this)
    }

    fun gpsEnabled() = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastGpsAt = now
        } else if (now - lastGpsAt < 10_000) {
            return // 有 GPS 信号时忽略网络定位
        }
        sink.onLocation(location)
    }

    // 旧版本 Android 上这些不是默认方法，必须实现
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

/**
 * 演示模式：模拟一段真实的骑行（加速、巡航、红灯停车），
 * 绕起点附近一圈。用于展示和室内调试。
 */
class DemoSource(
    startLat: Double,
    startLon: Double,
    private val sink: LocationSink,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val path: List<DoubleArray>
    private var idx = 0
    private var off = 0.0
    private var speed = 0.0 // m/s
    private var t = 0.0
    private val dt = 0.5

    init {
        // 起点附近一个约 350 米半径的环线
        val center = Geo.offset(startLat, startLon, 0.0, 350.0)
        path = (0..48).map { i -> Geo.offset(center[0], center[1], 180.0 + i * 7.5, 350.0) }
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, (dt * 1000).toLong())
        }
    }

    fun start() = handler.post(tick)
    fun stop() = handler.removeCallbacks(tick)

    private fun step() {
        t += dt
        // 目标速度：20~32 km/h 波动，每 75 秒模拟一次红灯停车 8 秒
        val cycle = t % 75.0
        val target = if (cycle > 67) 0.0 else (26 + 6 * sin(t * 2 * PI / 40)) / 3.6
        val acc = if (target > speed) 1.4 else 2.5
        speed += (target - speed).coerceIn(-acc * dt, acc * dt)

        var d = speed * dt
        while (d > 0 && idx < path.size - 1) {
            val a = path[idx]
            val b = path[idx + 1]
            val seg = Geo.distance(a[0], a[1], b[0], b[1])
            if (off + d < seg) {
                off += d
                d = 0.0
            } else {
                d -= seg - off
                idx++
                off = 0.0
                if (idx >= path.size - 1) idx = 0
            }
        }
        val a = path[idx]
        val b = path[idx + 1]
        val seg = Geo.distance(a[0], a[1], b[0], b[1]).coerceAtLeast(0.01)
        val f = (off / seg).coerceIn(0.0, 1.0)
        val loc = Location("demo").apply {
            latitude = a[0] + (b[0] - a[0]) * f
            longitude = a[1] + (b[1] - a[1]) * f
            this.speed = this@DemoSource.speed.toFloat()
            bearing = Geo.bearing(a[0], a[1], b[0], b[1])
            accuracy = 4f
            altitude = 48.0 + 6 * sin(t / 30)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        sink.onLocation(loc)
    }
}
