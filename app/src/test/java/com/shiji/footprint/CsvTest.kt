package com.shiji.footprint

import com.shiji.footprint.util.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun parse(s: String) = Csv.parse(s.byteInputStream())

    @Test
    fun yishengZujiFormat() {
        val segs = parse(
            """
            dataTime,locType,longitude,latitude,heading,accuracy,speed,distance,isBackForeground,stepType,altitude
            1700000000,1,120.1500,30.2500,0,10,1.2,0,0,0,12.5
            1700000010,1,120.1502,30.2501,0,8,1.3,0,0,0,12.0
            1700009000,1,120.2000,30.3000,0,8,1.3,0,0,0,12.0
            """.trimIndent()
        )
        assertEquals(2, segs.size) // 间隔 > 30 分钟拆段
        val p = segs[0][0]
        assertEquals(30.25, p.lat, 1e-9)
        assertEquals(120.15, p.lon, 1e-9)
        assertEquals(1_700_000_000_000L, p.t)
        assertEquals(12.5, p.alt, 1e-9)
        assertEquals(10f, p.acc)
    }

    @Test
    fun chineseHeaderDateStringsAndQuotes() {
        val segs = parse(
            "﻿时间,纬度,经度,备注\n" +
                "2026-10-01 08:00:00,31.2304,121.4737,\"上海, 外滩\"\n" +
                "2026/10/01 08:00:10,31.2305,121.4738,x\n"
        )
        assertEquals(1, segs.size)
        assertEquals(2, segs[0].size)
        assertEquals(10_000L, segs[0][1].t - segs[0][0].t)
        assertEquals(121.4737, segs[0][0].lon, 1e-9)
    }

    @Test
    fun headerlessAndMillisAndSemicolon() {
        val segs = parse("1700000000000;116.4074;39.9042\n1700000005000;116.4075;39.9043\n")
        assertEquals(2, segs[0].size)
        assertEquals(39.9042, segs[0][0].lat, 1e-9)
        assertEquals(116.4074, segs[0][0].lon, 1e-9)
        assertEquals(1_700_000_000_000L, segs[0][0].t)
    }

    @Test
    fun skipsInvalidRows() {
        val segs = parse("lat,lng,timestamp\nabc,1,2\n0,0,1700000000\n91,10,1700000000\n30.1,120.1,1700000000\n")
        assertEquals(1, segs.sumOf { it.size })
        assertTrue(Csv.parseTime("2026-10-01T08:00:00Z") > 0)
    }
}
