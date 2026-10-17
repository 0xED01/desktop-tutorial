package com.hydro.water

import android.app.Application
import com.hydro.water.data.WaterRepo
import com.hydro.water.reminder.Notifications
import com.hydro.water.reminder.ReminderScheduler

class WaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WaterRepo.init(this)
        Notifications.createChannels(this)
        ReminderScheduler.ensureScheduled(this)
    }
}
