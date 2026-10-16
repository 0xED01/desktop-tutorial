package com.shiji.footprint.util

import android.util.Xml
import com.shiji.footprint.data.TrackPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 解析 GPX 文件（trk / rte / wpt），每段返回一个点列表。 */
object Gpx {
    fun parse(input: InputStream): List<List<TrackPoint>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val segments = ArrayList<List<TrackPoint>>()
        var cur = ArrayList<TrackPoint>()
        var inPt = false
        var lat = 0.0
        var lon = 0.0
        var time = 0L
        var ele = 0.0
        var lastTime = 0L

        fun flush() {
            if (cur.isNotEmpty()) segments.add(cur)
            cur = ArrayList()
        }

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')
            when (ev) {
                XmlPullParser.START_TAG -> when (name) {
                    "trkseg", "rte" -> flush()
                    "trkpt", "rtept", "wpt" -> {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        time = 0L; ele = 0.0; inPt = true
                    }
                    "time" -> if (inPt) time = parseTime(parser.nextText().trim())
                    "ele" -> if (inPt) ele = parser.nextText().trim().toDoubleOrNull() ?: 0.0
                }
                XmlPullParser.END_TAG -> when (name) {
                    "trkpt", "rtept", "wpt" -> {
                        inPt = false
                        if (!lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            val t = if (time > 0) time else if (lastTime > 0) lastTime + 1000 else System.currentTimeMillis()
                            lastTime = t
                            cur.add(TrackPoint(t = t, lat = lat, lon = lon, alt = ele))
                        }
                    }
                    "trkseg", "rte" -> flush()
                }
            }
            ev = parser.next()
        }
        flush()
        return segments
    }

    private fun parseTime(s: String): Long = try {
        Instant.parse(s).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
