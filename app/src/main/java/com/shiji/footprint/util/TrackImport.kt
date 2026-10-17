package com.shiji.footprint.util

import com.shiji.footprint.data.TrackPoint
import java.io.BufferedInputStream
import java.io.InputStream

/** 根据文件内容自动判断 GPX 或 CSV 并解析。 */
object TrackImport {
    fun parse(input: InputStream): List<List<TrackPoint>> {
        val bis = BufferedInputStream(input)
        bis.mark(512)
        val head = ByteArray(512)
        val n = bis.read(head).coerceAtLeast(0)
        bis.reset()
        val text = String(head, 0, n, Charsets.UTF_8).removePrefix("﻿").trimStart()
        return if (text.startsWith("<")) Gpx.parse(bis) else Csv.parse(bis)
    }
}
