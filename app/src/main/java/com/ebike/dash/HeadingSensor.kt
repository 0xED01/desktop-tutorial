package com.ebike.dash

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 手机朝向（指南针）。停车或慢速时 GPS 没有可靠的方向，
 * 用它让定位箭头始终指向手机前方。输出为真北方位角 0..360。
 */
class HeadingSensor(ctx: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rot = FloatArray(9)
    private val orient = FloatArray(3)
    private var sx = 0.0
    private var sy = 0.0
    private var started = false

    /** 磁偏角（度），由当前位置计算，把磁北换算为真北 */
    var declination = 0f

    var heading: Float? = null; private set

    fun start() {
        if (started || sensor == null) return
        started = true
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!started) return
        started = false
        sm.unregisterListener(this)
    }

    fun updateDeclination(lat: Double, lon: Double, alt: Double) {
        declination = GeomagneticField(lat.toFloat(), lon.toFloat(), alt.toFloat(), System.currentTimeMillis()).declination
    }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        val a = orient[0].toDouble() + Math.toRadians(declination.toDouble())
        // 角度低通滤波（用 sin/cos 平滑，避免 359°→0° 跳变）
        val k = if (heading == null) 1.0 else 0.15
        sx += (sin(a) - sx) * k
        sy += (cos(a) - sy) * k
        val h = ((Math.toDegrees(atan2(sx, sy)) + 360) % 360).toFloat()
        heading = h
        onHeading(h)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
