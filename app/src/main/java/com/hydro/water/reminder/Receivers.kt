package com.hydro.water.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hydro.water.data.WaterRepo

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterRepo.init(context)
        WaterRepo.refresh()
        val s = WaterRepo.settings.value
        val reached = WaterRepo.todayTotal() >= s.goal
        if (s.reminderOn && ReminderScheduler.inWindow(s) && !(s.stopWhenDone && reached)) {
            Notifications.showReminder(context)
        }
        ReminderScheduler.schedule(context)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterRepo.init(context)
        when (intent.action) {
            ACTION_DRINK -> {
                val amount = intent.getIntExtra(EXTRA_AMOUNT, WaterRepo.settings.value.notifyAmount)
                WaterRepo.add(amount)
                // smartDelay 关闭时 add() 不会重排，这里也不强制
            }
            ACTION_SNOOZE -> {
                Notifications.cancelReminder(context)
                ReminderScheduler.schedule(context, delayMinutes = 15)
            }
        }
    }

    companion object {
        const val ACTION_DRINK = "com.hydro.water.DRINK"
        const val ACTION_SNOOZE = "com.hydro.water.SNOOZE"
        const val EXTRA_AMOUNT = "amount"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterRepo.init(context)
        ReminderScheduler.schedule(context)
    }
}
