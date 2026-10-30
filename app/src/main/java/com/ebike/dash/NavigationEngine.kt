package com.ebike.dash

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NavProgress(
    val step: RouteStep,
    val distToStep: Double,
    val remaining: Double,
    val snap: Route.Snap,
)

interface NavListener {
    fun onRouteReady(route: Route, reroute: Boolean)
    fun onRouteError(msg: String)
    fun onProgress(p: NavProgress)
    fun onSpeak(text: String)
    fun onArrived()
}

/** 导航引擎：路线吸附、转向提示、语音节点、偏航重算 */
class NavigationEngine(private val scope: CoroutineScope, private val l: NavListener, private val units: Units) {
    var route: Route? = null; private set
    var destName: String = ""; private set
    private var dest: DoubleArray? = null
    private var lastSeg = 0
    private var offCount = 0
    private var lastReroute = 0L
    private var routing = false
    private val announced = HashMap<Int, Int>()

    val active get() = dest != null

    fun start(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, name: String) {
        dest = doubleArrayOf(toLat, toLon)
        destName = name
        route = null
        request(fromLat, fromLon, false)
    }

    fun stop() {
        dest = null
        route = null
    }

    private fun request(lat: Double, lon: Double, reroute: Boolean) {
        val d = dest ?: return
        if (routing) return
        routing = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Net.route(lat, lon, d[0], d[1]) }
                if (dest === d) {
                    route = r
                    lastSeg = 0
                    offCount = 0
                    announced.clear()
                    l.onRouteReady(r, reroute)
                }
            } catch (e: Exception) {
                if (!reroute) dest = null
                l.onRouteError(e.message ?: "网络错误")
            } finally {
                routing = false
            }
        }
    }

    fun onLocation(loc: Location) {
        val r = route ?: return
        val snap = r.snap(loc.latitude, loc.longitude, lastSeg)
        lastSeg = snap.seg

        // 偏航检测：连续 3 次偏离路线 45 米以上则重新规划
        offCount = if (snap.dist > 45 && loc.accuracy < 40) offCount + 1 else 0
        val now = SystemClock.elapsedRealtime()
        if (offCount >= 3 && now - lastReroute > 15_000) {
            lastReroute = now
            offCount = 0
            l.onSpeak("您已偏离路线，正在重新规划")
            request(loc.latitude, loc.longitude, true)
            return
        }

        val remaining = r.length - snap.along
        if (remaining < 25) {
            l.onSpeak("已到达目的地附近，本次导航结束")
            stop()
            l.onArrived()
            return
        }

        val k = r.nextStepIndex(snap.along)
        val step = r.steps[k]
        val toStep = r.stepAlong[k] - snap.along
        announce(k, step, toStep)
        l.onProgress(NavProgress(step, toStep, remaining, snap))
    }

    /** 分三级播报：400 米预告、120 米提醒、25 米即刻 */
    private fun announce(k: Int, step: RouteStep, d: Double) {
        val done = announced[k] ?: 0
        val level = when {
            d <= 25 -> 3
            d <= 120 -> 2
            d <= 400 -> 1
            else -> 0
        }
        if (level == 0 || level <= done) return
        announced[k] = level
        val text = step.instruction()
        when (level) {
            1 -> if (d > 200) l.onSpeak("前方${units.spokenDist(d)}，$text")
            2 -> l.onSpeak("${units.spokenDist(d)}后，$text")
            3 -> if (step.type != "arrive") l.onSpeak("现在$text")
        }
    }
}
