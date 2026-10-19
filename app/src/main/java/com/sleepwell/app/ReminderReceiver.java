package com.sleepwell.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int kind = intent.getIntExtra("kind", -1);
        if (kind >= 0 && Store.remindersOn(c)) notify(c, kind);
        Reminders.scheduleAll(c);
    }

    private void notify(Context c, int kind) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Reminders.ensureChannel(c);
        int tab = kind == Reminders.WAKE ? 1 : (kind == Reminders.CAFFEINE ? 0 : 2);
        Intent open = new Intent(c, MainActivity.class).putExtra("tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 200 + kind, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, Reminders.CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(Reminders.title(kind))
                .setContentText(Reminders.body(kind))
                .setStyle(new Notification.BigTextStyle().bigText(Reminders.body(kind)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(kind + 1, n);
    }
}
