package com.ebike.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    // 一条向北 1 公里、再向东 1 公里的 L 形路线
    private val corner = Geo.offset(39.9, 116.4, 0.0, 1000.0)
    private val end = Geo.offset(corner[0], corner[1], 90.0, 1000.0)
    private val route = Route(
        coords = listOf(doubleArrayOf(39.9, 116.4), corner, end),
        steps = listOf(
            RouteStep("depart", null, "", 0, 39.9, 116.4),
            RouteStep("turn", "right", "长安街", 0, corner[0], corner[1]),
            RouteStep("arrive", null, "", 0, end[0], end[1]),
        ),
        durationSec = 400.0,
    )

    @Test
    fun lengthAndStepPositions() {
        assertEquals(2000.0, route.length, 5.0)
        assertEquals(1000.0, route.stepAlong[1], 5.0)
        assertEquals(2000.0, route.stepAlong[2], 5.0)
    }

    @Test
    fun snapsOffsetPointAndFindsNextTurn() {
        val p = Geo.offset(39.9, 116.4, 0.0, 400.0)
        val q = Geo.offset(p[0], p[1], 90.0, 20.0) // 偏离路线 20 米
        val s = route.snap(q[0], q[1], 0)
        assertEquals(400.0, s.along, 3.0)
        assertEquals(20.0, s.dist, 1.0)
        assertEquals(1, route.nextStepIndex(s.along))
        val after = route.snap(end[0], end[1] - 0.003, 0)
        assertEquals(2, route.nextStepIndex(after.along))
    }

    @Test
    fun chineseInstructions() {
        assertEquals("右转，进入长安街", route.steps[1].instruction())
        assertEquals("到达目的地", route.steps[2].instruction())
        assertEquals("进入环岛，从第2出口驶出", RouteStep("roundabout", "right", "", 2, 0.0, 0.0).instruction())
    }

    @Test
    fun units() {
        val u = Units()
        assertEquals("350 米", u.navDist(352.0))
        assertEquals("1.2 公里", u.navDist(1240.0))
        assertTrue(Units(true).navDist(5000.0).endsWith("mi"))
        assertEquals("东北", Geo.compass(44f))
    }
}
