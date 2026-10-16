package com.shiji.footprint.geo

import java.util.concurrent.ConcurrentHashMap

/**
 * 已探索网格的多层级索引。最细层级为 [Geo.CELL_LEVEL]，向上逐级聚合，
 * 地图缩小时使用粗层级绘制，避免一次绘制过多格子。
 */
class CellIndex {
    private val levels = Array(Geo.CELL_LEVEL - Geo.MIN_LEVEL + 1) {
        ConcurrentHashMap.newKeySet<Long>()
    }

    @Volatile
    var areaKm2 = 0.0
        private set

    val size: Int get() = levels.last().size

    fun cells(level: Int): Set<Long> =
        levels[level.coerceIn(Geo.MIN_LEVEL, Geo.CELL_LEVEL) - Geo.MIN_LEVEL]

    /** 添加最细层级的格子，返回是否为新格子。 */
    fun add(key: Long): Boolean {
        if (!levels.last().add(key)) return false
        var x = Geo.keyX(key)
        var y = Geo.keyY(key)
        areaKm2 += Geo.cellAreaKm2(y)
        for (l in Geo.CELL_LEVEL - 1 downTo Geo.MIN_LEVEL) {
            x = x shr 1
            y = y shr 1
            levels[l - Geo.MIN_LEVEL].add(Geo.key(x, y))
        }
        return true
    }

    fun clear() {
        levels.forEach { it.clear() }
        areaKm2 = 0.0
    }
}
