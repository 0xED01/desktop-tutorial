package com.hydro.water.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hydro.water.MainActivity
import com.hydro.water.R
import com.hydro.water.data.WaterRepo
import com.hydro.water.reminder.ActionReceiver

class WaterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WaterRepo.init(context)
        WaterRepo.refresh()
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, WaterWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(ctx)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val s = WaterRepo.settings.value
            val total = WaterRepo.todayTotal()
            val amount = s.cups[1]
            val pct = if (s.goal > 0) (total * 100 / s.goal).coerceAtMost(100) else 0
            return RemoteViews(ctx.packageName, R.layout.widget_water).apply {
                setTextViewText(R.id.w_title, if (total >= s.goal) "今日目标已达成 🎉" else "今日饮水 · $pct%")
                setTextViewText(R.id.w_amount, "$total / ${s.goal} ml")
                setProgressBar(R.id.w_progress, 100, pct, false)
                setTextViewText(R.id.w_add_text, "+$amount")
                setOnClickPendingIntent(
                    R.id.w_add, PendingIntent.getBroadcast(
                        ctx, 10,
                        Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DRINK)
                            .putExtra(ActionReceiver.EXTRA_AMOUNT, amount),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widget_root, PendingIntent.getActivity(
                        ctx, 11, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }
}
