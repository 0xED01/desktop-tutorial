package com.ebike.dash

class RouteStep(
    val type: String,
    val modifier: String?,
    val name: String,
    val exit: Int,
    val lat: Double,
    val lon: Double,
) {
    /** 中文转向提示 */
    fun instruction(): String {
        val dir = when (modifier) {
            "left" -> "左转"
            "right" -> "右转"
            "slight left" -> "向左前方行驶"
            "slight right" -> "向右前方行驶"
            "sharp left" -> "向左后方急转"
            "sharp right" -> "向右后方急转"
            "uturn" -> "掉头"
            else -> "直行"
        }
        val action = when (type) {
            "arrive" -> return "到达目的地"
            "depart" -> "出发"
            "roundabout", "rotary", "exit roundabout", "exit rotary" ->
                if (exit > 0) "进入环岛，从第${exit}出口驶出" else "进入环岛"
            "fork" -> if (modifier?.contains("left") == true) "靠左行驶" else "靠右行驶"
            "merge" -> "汇入主路"
            "on ramp" -> "驶入匝道"
            "off ramp" -> "驶出匝道"
            "continue", "new name" -> if (modifier == null || modifier == "straight") "继续直行" else dir
            else -> dir
        }
        return if (name.isNotBlank()) "$action，进入$name" else action
    }
}

/**
 * 一条规划好的路线。coords 为 [lat, lon] 列表，cum 为沿线累计距离（米），
 * stepAlong 为每个转向点在路线上的累计距离。
 */
class Route(
    val coords: List<DoubleArray>,
    val steps: List<RouteStep>,
    val durationSec: Double,
) {
    val cum = DoubleArray(coords.size)
    val stepAlong = DoubleArray(steps.size)
    val length: Double

    init {
        for (i in 1 until coords.size) {
            cum[i] = cum[i - 1] + Geo.distance(coords[i - 1][0], coords[i - 1][1], coords[i][0], coords[i][1])
        }
        length = if (coords.isEmpty()) 0.0 else cum.last()
        // 按顺序把每个转向点投影到路线上
        var from = 0
        for ((k, s) in steps.withIndex()) {
            val snap = snapRange(s.lat, s.lon, from, coords.size - 2)
            stepAlong[k] = snap.along
            from = snap.seg
        }
    }

    class Snap(val seg: Int, val along: Double, val dist: Double, val lat: Double, val lon: Double)

    fun snap(lat: Double, lon: Double, hint: Int): Snap {
        val local = snapRange(lat, lon, hint - 3, hint + 80)
        return if (local.dist < 50) local else snapRange(lat, lon, 0, coords.size - 2)
    }

    private fun snapRange(lat: Double, lon: Double, from: Int, to: Int): Snap {
        val a0 = from.coerceAtLeast(0)
        val a1 = to.coerceAtMost(coords.size - 2)
        var best = Snap(0, 0.0, Double.MAX_VALUE, lat, lon)
        for (i in a0..a1) {
            val a = coords[i]
            val b = coords[i + 1]
            val r = Geo.projectToSegment(lat, lon, a[0], a[1], b[0], b[1])
            if (r[0] < best.dist) {
                val t = r[1]
                best = Snap(
                    i, cum[i] + t * (cum[i + 1] - cum[i]), r[0],
                    a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t,
                )
            }
        }
        return best
    }

    /** 下一个（尚未经过的）转向点索引 */
    fun nextStepIndex(along: Double): Int {
        for (k in 1 until steps.size) if (stepAlong[k] > along + 3) return k
        return steps.size - 1
    }

    /** 从吸附点开始的剩余路线，用于“走过的路线消失”的效果 */
    fun remainingCoords(snap: Snap): List<DoubleArray> {
        val out = ArrayList<DoubleArray>(coords.size - snap.seg)
        out.add(doubleArrayOf(snap.lat, snap.lon))
        for (i in snap.seg + 1 until coords.size) out.add(coords[i])
        return out
    }
}
