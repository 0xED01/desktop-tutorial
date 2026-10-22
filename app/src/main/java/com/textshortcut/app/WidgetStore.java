package com.textshortcut.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 保存每个小部件显示的图标和文字。
 * 添加小部件前先把内容存为“待绑定”，桌面确认后拿到小部件 id 再绑定上。
 */
public class WidgetStore {
    private static final String PREFS = "widgets";
    private static final long PENDING_TIMEOUT_MS = 5 * 60 * 1000;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File iconFile(Context c, String key) {
        File dir = new File(c.getFilesDir(), "widgets");
        dir.mkdirs();
        return new File(dir, key + ".png");
    }

    /** 保存一个待添加的图标，返回它的 token。 */
    public static String savePending(Context c, Bitmap icon, String label, String url) throws Exception {
        String token = "p" + System.nanoTime();
        try (OutputStream out = new FileOutputStream(iconFile(c, token))) {
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        prefs(c).edit()
                .putString(token + ".label", label)
                .putString(token + ".url", url)
                .putString("lastPending", token)
                .putLong("lastPendingTime", System.currentTimeMillis())
                .apply();
        return token;
    }

    /** 把待添加的内容绑定到小部件 id 上。 */
    public static boolean bind(Context c, String token, int widgetId) {
        File src = iconFile(c, token);
        if (!src.exists()) return false;
        SharedPreferences p = prefs(c);
        String key = "w" + widgetId;
        src.renameTo(iconFile(c, key));
        SharedPreferences.Editor e = p.edit()
                .putString(key + ".label", p.getString(token + ".label", ""))
                .putString(key + ".url", p.getString(token + ".url", ""))
                .remove(token + ".label")
                .remove(token + ".url");
        if (token.equals(p.getString("lastPending", null))) e.remove("lastPending");
        e.apply();
        return true;
    }

    /** 部分系统不回调添加结果，这时把最近一次待添加的内容给新出现的小部件。 */
    public static void bindLastPendingIfUnknown(Context c, int widgetId) {
        SharedPreferences p = prefs(c);
        if (iconFile(c, "w" + widgetId).exists()) return;
        String token = p.getString("lastPending", null);
        long time = p.getLong("lastPendingTime", 0);
        if (token != null && System.currentTimeMillis() - time < PENDING_TIMEOUT_MS) {
            bind(c, token, widgetId);
        }
    }

    public static void delete(Context c, int widgetId) {
        String key = "w" + widgetId;
        iconFile(c, key).delete();
        prefs(c).edit().remove(key + ".label").remove(key + ".url").apply();
    }

    public static void update(Context c, AppWidgetManager mgr, int widgetId) {
        String key = "w" + widgetId;
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_icon);
        File f = iconFile(c, key);
        Bitmap bmp = f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
        if (bmp != null) {
            rv.setImageViewBitmap(R.id.widget_icon, bmp);
            String label = prefs(c).getString(key + ".label", "");
            rv.setTextViewText(R.id.widget_label, label);
            rv.setViewVisibility(R.id.widget_label, label.trim().isEmpty() ? View.GONE : View.VISIBLE);
            String url = prefs(c).getString(key + ".url", "");
            Intent i = new Intent(c, ShortcutActivity.class);
            i.putExtra(ShortcutActivity.EXTRA_URL, url);
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, widgetId, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            // 从桌面小部件列表直接拖出来的：提示去 App 里添加
            rv.setTextViewText(R.id.widget_label, "请在App中添加");
            rv.setViewVisibility(R.id.widget_label, View.VISIBLE);
            Intent i = new Intent(c, MainActivity.class);
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, widgetId, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        mgr.updateAppWidget(widgetId, rv);
    }
}
