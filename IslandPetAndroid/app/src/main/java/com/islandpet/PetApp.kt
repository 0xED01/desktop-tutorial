package com.islandpet

import android.app.Application
import com.islandpet.island.IslandPrefs
import com.islandpet.island.IslandService
import com.islandpet.model.PetRepo

class PetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PetRepo.init(this)
        IslandService.createChannels(this)
        if (IslandPrefs.enabled(this)) IslandService.start(this)
    }
}
