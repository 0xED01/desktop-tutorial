package com.textshortcut.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存每个小部件显示的图标和文字。
 * 添加小部件前先把内容存为“待绑定”，桌面确认后拿到小部件 id 再绑定上。
 */
public class WidgetStore {
    private static final String PREFS = "widgets";
    private static final long PENDING_TIMEOUT_MS = 60 * 60 * 1000;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File iconFile(Context c, String key) {
        File dir = new File(c.getFilesDir(), "widgets");
        dir.mkdirs();
        return new File(dir, key + ".png");
    }

    /** 保存一个待添加的图标，排到待添加队列末尾，返回它的 token。 */
    public static String savePending(Context c, Bitmap icon, String label, String url) throws Exception {
        String token = "p" + System.nanoTime();
        try (OutputStream out = new FileOutputStream(iconFile(c, token))) {
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        List<String> list = pendingList(c);
        list.add(token);
        prefs(c).edit()
                .putString(token + ".label", label)
                .putString(token + ".url", url)
                .putString("pending", TextUtils.join(",", list))
                .putLong("pendingTime", System.currentTimeMillis())
                .apply();
        return token;
    }

    /** 清空待添加队列（开始新一轮添加前调用）。 */
    public static void clearPending(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String token : pendingList(c)) {
            iconFile(c, token).delete();
            e.remove(token + ".label").remove(token + ".url");
        }
        e.remove("pending").apply();
    }

    private static List<String> pendingList(Context c) {
        String s = prefs(c).getString("pending", "");
        List<String> list = new ArrayList<>();
        for (String t : s.split(",")) if (!t.isEmpty()) list.add(t);
        return list;
    }

    /** 把待添加的内容绑定到小部件 id 上。 */
    public static boolean bind(Context c, String token, int widgetId) {
        File src = iconFile(c, token);
        if (!src.exists()) return false;
        SharedPreferences p = prefs(c);
        String key = "w" + widgetId;
        src.renameTo(iconFile(c, key));
        List<String> list = pendingList(c);
        list.remove(token);
        p.edit()
                .putString(key + ".label", p.getString(token + ".label", ""))
                .putString(key + ".url", p.getString(token + ".url", ""))
                .remove(token + ".label")
                .remove(token + ".url")
                .putString("pending", TextUtils.join(",", list))
                .commit();
        return true;
    }

    /**
     * 新出现但还没有内容的小部件（桌面没回调，或者用户从小部件列表手动拖出来的），
     * 按顺序领取待添加队列里的下一个字。
     */
    public static void bindNextPendingIfUnknown(Context c, int widgetId) {
        if (iconFile(c, "w" + widgetId).exists()) return;
        long time = prefs(c).getLong("pendingTime", 0);
        if (System.currentTimeMillis() - time > PENDING_TIMEOUT_MS) return;
        for (String token : pendingList(c)) {
            if (bind(c, token, widgetId)) return;
        }
    }

    public static int pendingCount(Context c) {
        return pendingList(c).size();
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
