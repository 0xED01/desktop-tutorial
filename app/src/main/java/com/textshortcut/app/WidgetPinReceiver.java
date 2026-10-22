package com.textshortcut.app;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 桌面确认添加小部件后的回调：把图标绑定到新小部件上，并通知界面继续添加下一个。 */
public class WidgetPinReceiver extends BroadcastReceiver {
    public static final String EXTRA_TOKEN = "token";

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String token = intent.getStringExtra(EXTRA_TOKEN);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && token != null) {
            WidgetStore.bind(context, token, id);
            WidgetStore.update(context, AppWidgetManager.getInstance(context), id);
        }
        context.sendBroadcast(new Intent(MainActivity.ACTION_PINNED).setPackage(context.getPackageName()));
    }
}
