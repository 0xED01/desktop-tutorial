package com.shiji.footprint.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.TrackPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** 封装 osmdroid MapView 及其图层，供 Compose 的 AndroidView 使用。 */
@SuppressLint("ClickableViewAccessibility")
class FootprintMap(ctx: Context, private val withFog: Boolean) {
    val view = MapView(ctx)
    private val density = ctx.resources.displayMetrics.density
    private val fog = FogOverlay(Repository.cells)
    private val track = TrackOverlay(density)
    private val dot = LocationDotOverlay(density)
    private var style: MapStyle? = null
    var onUserGesture: (() -> Unit)? = null

    init {
        view.setMultiTouchControls(true)
        view.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        view.setTilesScaledToDpi(true)
        view.minZoomLevel = 3.0
        view.isVerticalMapRepetitionEnabled = false
        view.setScrollableAreaLimitLatitude(85.0, -85.0, 0)
        if (withFog) view.overlays.add(fog)
        view.overlays.add(track)
        view.overlays.add(dot)
        view.setOnTouchListener { v, e ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (e.actionMasked == MotionEvent.ACTION_MOVE) onUserGesture?.invoke()
            false
        }
        view.controller.setZoom(4.0)
        view.controller.setCenter(GeoPoint(35.8, 104.1))
    }

    val gcj: Boolean get() = style?.gcj ?: false

    fun update(
        style: MapStyle,
        fogEnabled: Boolean,
        fogAlpha: Float,
        trackPoints: List<TrackPoint>,
        location: TrackPoint?,
        recording: Boolean,
    ) {
        if (this.style?.id != style.id) {
            this.style = style
            view.setTileSource(style.source)
            view.maxZoomLevel = style.maxZoom.toDouble()
            fog.gcj = style.gcj
            track.gcj = style.gcj
            dot.gcj = style.gcj
        }
        fog.isEnabled = withFog && fogEnabled
        fog.setStyle(fogAlpha, style.dark)
        track.points = trackPoints
        dot.point = location
        dot.active = recording
        view.invalidate()
    }

    fun display(p: TrackPoint): GeoPoint = GeoPoint(0.0, 0.0).setDisplay(p.lat, p.lon, gcj)

    fun animateTo(p: TrackPoint, minZoom: Double = 16.0) {
        val zoom = maxOf(view.zoomLevelDouble, minZoom).coerceAtMost(view.maxZoomLevel)
        view.controller.animateTo(display(p), zoom, 600L)
    }

    fun restoreCamera() {
        val cam = Prefs.camera()
        if (cam != null) {
            view.controller.setZoom(cam[2])
            view.controller.setCenter(GeoPoint(cam[0], cam[1]))
        } else {
            Repository.lastLocation.value?.let {
                view.controller.setZoom(15.0)
                view.controller.setCenter(display(it))
            }
        }
    }

    fun saveCamera() {
        val c = view.mapCenter
        Prefs.saveCamera(c.latitude, c.longitude, view.zoomLevelDouble)
    }

    /** 缩放到能完整显示轨迹。 */
    fun fit(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val gps = points.map { display(it) }
        val run = Runnable {
            val bb = BoundingBox.fromGeoPoints(gps)
            if (bb.latitudeSpan < 0.002 && bb.longitudeSpanWithDateLine < 0.002) {
                view.controller.setZoom(16.5)
                view.controller.setCenter(bb.centerWithDateLine)
            } else {
                view.zoomToBoundingBox(bb.increaseByScale(1.35f), false)
            }
        }
        if (view.width > 0 && view.height > 0) run.run()
        else view.addOnFirstLayoutListener { _, _, _, _, _ -> run.run() }
    }
}
