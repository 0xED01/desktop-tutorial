package com.shiji.footprint.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.shiji.footprint.service.TrackingService

/** 未在记录时获取一次当前位置（用于"定位到我"）。 */
object LocationHelper {
    @SuppressLint("MissingPermission")
    fun oneShot(ctx: Context, cb: (Location) -> Unit): Boolean {
        if (!TrackingService.hasLocationPermission(ctx)) return false
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { lm.allProviders.contains(it) && lm.isProviderEnabled(it) }
        if (providers.isEmpty()) return false
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { cb(it) }
        val handler = Handler(Looper.getMainLooper())
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                cb(location)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            providers.forEach { lm.requestLocationUpdates(it, 1000L, 0f, listener, Looper.getMainLooper()) }
        } catch (_: SecurityException) {
            return false
        }
        handler.postDelayed({ lm.removeUpdates(listener) }, 30_000)
        return true
    }
}
