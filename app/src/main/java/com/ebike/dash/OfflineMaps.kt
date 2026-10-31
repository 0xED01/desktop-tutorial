package com.ebike.dash

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * 离线地图：把某个范围的地图提前下载到手机里，没有网络也能显示（GPS 本身不需要网络）。
 * 浅色、深色两套样式各下载一份；两者共用同一套道路数据，第二份只多一点点。
 * 地图数据最细到第 14 级，更近的缩放由手机放大显示，所以只需下载到 14 级。
 */
class OfflineMaps(ctx: Context) {
    private val mgr = OfflineManager.getInstance(ctx)
    private val pixelRatio = ctx.resources.displayMetrics.density

    /** 正在下载 */
    var downloading = false; private set
    /** 0..1 */
    var progress = 0f; private set
    var bytes = 0L; private set
    /** 网络中断等可自动恢复的问题，给用户看的提示 */
    var notice: String? = null; private set

    var onUpdate: (() -> Unit)? = null
    var onFinished: ((ok: Boolean, message: String?) -> Unit)? = null

    private var active: OfflineRegion? = null
    private var created = mutableListOf<OfflineRegion>()

    init {
        // 浏览过的地方也自动缓存，最多 200 MB
        mgr.setMaximumAmbientCacheSize(200L * 1024 * 1024, object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {}
            override fun onError(message: String) {}
        })
    }

    /** 已下载的区域数（浅色 + 深色算一个区域）和大约占用空间 */
    fun summary(cb: (areas: Int, bytes: Long) -> Unit) {
        mgr.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions?.toList().orEmpty()
                if (regions.isEmpty()) { cb(0, 0); return }
                val groups = regions.map { groupOf(it) }.toSet()
                var pending = regions.size
                val sizeByGroup = HashMap<Long, Long>()
                for (r in regions) {
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            val g = groupOf(r)
                            // 同一区域两套样式的瓦片是共用的，取较大的那个作为占用估算
                            sizeByGroup[g] = maxOf(sizeByGroup[g] ?: 0L, status?.completedResourceSize ?: 0L)
                            if (--pending == 0) cb(groups.size, sizeByGroup.values.sum())
                        }
                        override fun onError(error: String?) {
                            if (--pending == 0) cb(groups.size, sizeByGroup.values.sum())
                        }
                    })
                }
            }
            override fun onError(error: String) = cb(0, 0)
        })
    }

    /** 下载以 (lat, lon) 为中心、半径 radiusKm 的地图 */
    fun download(lat: Double, lon: Double, radiusKm: Double = RADIUS_KM) {
        if (downloading) return
        downloading = true
        progress = 0f
        bytes = 0L
        notice = null
        created.clear()
        val n = Geo.offset(lat, lon, 0.0, radiusKm * 1000)
        val s = Geo.offset(lat, lon, 180.0, radiusKm * 1000)
        val e = Geo.offset(lat, lon, 90.0, radiusKm * 1000)
        val w = Geo.offset(lat, lon, 270.0, radiusKm * 1000)
        val bounds = LatLngBounds.Builder().include(LatLng(n[0], n[1])).include(LatLng(s[0], s[1]))
            .include(LatLng(e[0], e[1])).include(LatLng(w[0], w[1])).build()
        val group = System.currentTimeMillis()
        downloadStyle(listOf(Palette.LIGHT.mapStyle, Palette.DARK.mapStyle), 0, bounds, group)
        onUpdate?.invoke()
    }

    private fun downloadStyle(styles: List<String>, index: Int, bounds: LatLngBounds, group: Long) {
        if (!downloading) return
        if (index >= styles.size) {
            finish(true, null)
            return
        }
        val def = OfflineTilePyramidRegionDefinition(styles[index], bounds, 0.0, 14.0, pixelRatio)
        val meta = JSONObject().put("group", group).put("style", styles[index]).toString().toByteArray()
        mgr.createOfflineRegion(def, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                if (!downloading) { offlineRegion.delete(noopDelete); return }
                created.add(offlineRegion)
                active = offlineRegion
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (!downloading) return
                        val frac = if (status.requiredResourceCount > 0)
                            status.completedResourceCount.toFloat() / status.requiredResourceCount else 0f
                        progress = ((index + frac) / styles.size).coerceIn(0f, 1f)
                        if (index == 0) bytes = status.completedResourceSize
                        notice = null
                        onUpdate?.invoke()
                        if (status.isComplete) {
                            offlineRegion.setObserver(null)
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            downloadStyle(styles, index + 1, bounds, group)
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        // 网络错误时引擎会自动重试，这里只提示
                        notice = if (error.reason == OfflineRegionError.REASON_CONNECTION)
                            "网络中断，恢复后会自动继续" else "部分地图暂时下载失败，正在重试"
                        onUpdate?.invoke()
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        finish(false, "范围太大，超过了单次可下载的上限")
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) = finish(false, "无法开始下载：$error")
        })
    }

    /** 取消：停止下载并删除这次未完成的数据 */
    fun cancel() {
        if (!downloading) return
        downloading = false
        active?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        created.forEach { it.delete(noopDelete) }
        created.clear()
        active = null
        onUpdate?.invoke()
    }

    private fun finish(ok: Boolean, message: String?) {
        if (!downloading) return
        downloading = false
        active = null
        if (!ok) created.forEach { it.delete(noopDelete) }
        created.clear()
        progress = if (ok) 1f else progress
        onUpdate?.invoke()
        onFinished?.invoke(ok, message)
    }

    fun deleteAll(done: () -> Unit) {
        mgr.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions?.toList().orEmpty()
                if (regions.isEmpty()) { done(); return }
                var pending = regions.size
                for (r in regions) r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { if (--pending == 0) done() }
                    override fun onError(error: String) { if (--pending == 0) done() }
                })
            }
            override fun onError(error: String) = done()
        })
    }

    private fun groupOf(r: OfflineRegion): Long = try {
        JSONObject(String(r.metadata)).optLong("group", r.id)
    } catch (_: Exception) {
        r.id
    }

    private val noopDelete = object : OfflineRegion.OfflineRegionDeleteCallback {
        override fun onDelete() {}
        override fun onError(error: String) {}
    }

    companion object {
        const val RADIUS_KM = 20.0
    }
}
