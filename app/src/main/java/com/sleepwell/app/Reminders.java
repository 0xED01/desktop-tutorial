package com.sleepwell.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** 每日提醒：咖啡因截止、睡前放松、上床、起床。 */
public class Reminders {
    public static final String CHANNEL = "reminders";
    public static final int CAFFEINE = 0, WINDDOWN = 1, BED = 2, WAKE = 3;

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "睡眠提醒", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private static PendingIntent pi(Context c, int kind) {
        Intent i = new Intent(c, ReminderReceiver.class).putExtra("kind", kind);
        return PendingIntent.getBroadcast(c, 100 + kind, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (int k = CAFFEINE; k <= WAKE; k++) am.cancel(pi(c, k));
        if (!Store.remindersOn(c)) return;
        int bed = Store.bedMin(c);
        schedule(c, am, CAFFEINE, SleepEntry.diff(8 * 60, bed));
        schedule(c, am, WINDDOWN, SleepEntry.diff(60, bed));
        schedule(c, am, BED, bed);
        schedule(c, am, WAKE, Store.wakeMin(c));
    }

    private static void schedule(Context c, AlarmManager am, int kind, int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        long at = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, kind));
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, kind));
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, kind));
        }
    }

    public static String title(int kind) {
        switch (kind) {
            case CAFFEINE: return "今天的咖啡因到此为止";
            case WINDDOWN: return "1小时后睡觉，开始放松吧";
            case BED: return "到睡眠时间了";
            default: return "早上好，按时起床";
        }
    }

    public static String body(int kind) {
        switch (kind) {
            case CAFFEINE:
                return "咖啡因半衰期约5-6小时。从现在起不喝咖啡、浓茶、奶茶、可乐和能量饮料。";
            case WINDDOWN:
                return "调暗灯光、放下工作、少看手机。可以洗个温水澡，或者把担心的事写进“写下烦恼”。";
            case BED:
                return "感到困了再上床。如果躺下约20分钟还睡不着，就起身到昏暗处做点无聊的事，困了再回来。";
            default:
                return "不管昨晚睡得怎样都按时起床。拉开窗帘或出门晒10-30分钟太阳，然后花1分钟记录睡眠日记。";
        }
    }
}
