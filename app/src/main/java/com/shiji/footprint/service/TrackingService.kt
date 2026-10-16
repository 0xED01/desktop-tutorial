package com.shiji.footprint.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shiji.footprint.MainActivity
import com.shiji.footprint.R
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.TrackPoint
import com.shiji.footprint.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max

/** 前台定位服务：息屏后持续记录足迹。 */
class TrackingService : Service(), LocationListener {

    companion object {
        private const val CHANNEL = "tracking"
        private const val NOTIF_ID = 1001
        private const val ACTION_STOP = "com.shiji.footprint.action.STOP"
        private const val ACTION_RELOAD = "com.shiji.footprint.action.RELOAD"

        val running = MutableStateFlow(false)
        val startedAt = MutableStateFlow(0L)

        fun start(ctx: Context) {
            Prefs.setRecording(true)
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs.setRecording(false)
            ctx.stopService(Intent(ctx, TrackingService::class.java))
        }

        /** 记录模式变化后重新注册定位。 */
        fun reload(ctx: Context) {
            if (running.value) ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_RELOAD))
        }

        fun hasLocationPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private lateinit var lm: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastAccepted: Location? = null
    private var lastGpsFix = 0L
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "足迹记录", NotificationManager.IMPORTANCE_LOW).apply {
                description = "记录足迹时显示的常驻通知"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setRecording(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (running.value) {
                    lm.removeUpdates(this)
                    requestUpdates()
                }
                return START_STICKY
            }
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running.value) {
            if (!requestUpdates()) {
                stopSelf()
                return START_NOT_STICKY
            }
            Repository.newSegment()
            running.value = true
            startedAt.value = System.currentTimeMillis()
            acquireWakeLock()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(): Boolean {
        if (!hasLocationPermission(this)) return false
        val mode = Prefs.recordMode.value
        return try {
            var any = false
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, mode.intervalMs, 0f, this, Looper.getMainLooper())
                any = true
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, mode.intervalMs * 2, 0f, this, Looper.getMainLooper())
                any = true
            }
            any
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override fun onLocationChanged(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        if (loc.provider == LocationManager.GPS_PROVIDER) lastGpsFix = now
        else if (now - lastGpsFix < 30_000) return // 有 GPS 时忽略网络定位
        val mode = Prefs.recordMode.value
        if (loc.hasAccuracy() && loc.accuracy > mode.maxAccuracy) return
        val prev = lastAccepted
        val point = TrackPoint(
            t = System.currentTimeMillis(), lat = loc.latitude, lon = loc.longitude,
            acc = loc.accuracy, speed = loc.speed, alt = loc.altitude,
        )
        if (prev != null && loc.distanceTo(prev) < max(mode.minDistance, loc.accuracy * 0.5f)) {
            return
        }
        lastAccepted = loc
        Repository.addPoint(point)
        if (now - lastNotify > 20_000) {
            lastNotify = now
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())
        }
    }

    // 低版本系统上 LocationListener 的这些方法没有默认实现，必须覆写
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun buildNotification(): Notification {
        val s = Repository.stats.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("正在记录足迹")
            .setContentText("今日 ${Fmt.km(s.todayKm)} km · 已探索 ${Fmt.area(s.areaKm2)} km²")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF4F6BED.toInt())
            .setContentIntent(open)
            .addAction(0, "停止记录", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Footprint:tracking")
            .apply { setReferenceCounted(false); acquire() }
    }

    override fun onDestroy() {
        lm.removeUpdates(this)
        running.value = false
        startedAt.value = 0L
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
