package com.shiji.footprint

import android.app.Application
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Configuration.getInstance().apply {
            load(this@App, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            val base = File(cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileFileSystemCacheMaxBytes = 500L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 400L * 1024 * 1024
        }
        Repository.init(this)
    }
}
