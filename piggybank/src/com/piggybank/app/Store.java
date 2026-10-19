package com.piggybank.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists settings and records in SharedPreferences. Amounts are stored in cents. */
public class Store {

    public static final int TYPE_INCOME = 0;
    public static final int TYPE_WITHDRAW = 1;

    public static class Record {
        public long id;
        public int type;
        public int yearMonth;   // e.g. 202610
        public long main;       // income: main job income; withdraw: amount taken out
        public long side;       // side job income
        public double mainPct;  // ratio snapshot at time of recording, so later changes don't rewrite history
        public double sidePct;
        public String note = "";

        public long saved() {
            if (type == TYPE_WITHDRAW) return -main;
            return Math.round(main * mainPct / 100.0) + Math.round(side * sidePct / 100.0);
        }
    }

    private final SharedPreferences prefs;
    private final List<Record> records = new ArrayList<>();

    public Store(Context ctx) {
        prefs = ctx.getSharedPreferences("piggy", Context.MODE_PRIVATE);
        load();
    }

    public boolean isConfigured() { return prefs.getBoolean("configured", false); }
    public long getInitial() { return prefs.getLong("initial", 0); }
    public double getMainPct() { return Double.longBitsToDouble(prefs.getLong("mainPct", Double.doubleToLongBits(30))); }
    public double getSidePct() { return Double.longBitsToDouble(prefs.getLong("sidePct", Double.doubleToLongBits(50))); }

    public void saveSettings(long initial, double mainPct, double sidePct) {
        prefs.edit()
                .putBoolean("configured", true)
                .putLong("initial", initial)
                .putLong("mainPct", Double.doubleToLongBits(mainPct))
                .putLong("sidePct", Double.doubleToLongBits(sidePct))
                .apply();
    }

    /** Newest month first; within a month, newest entry first. */
    public List<Record> getRecords() { return records; }

    public long totalSaved() {
        long sum = 0;
        for (Record r : records) if (r.type == TYPE_INCOME) sum += r.saved();
        return sum;
    }

    public long totalWithdrawn() {
        long sum = 0;
        for (Record r : records) if (r.type == TYPE_WITHDRAW) sum += r.main;
        return sum;
    }

    public long savedInMonth(int ym) {
        long sum = 0;
        for (Record r : records) if (r.type == TYPE_INCOME && r.yearMonth == ym) sum += r.saved();
        return sum;
    }

    public long balance() {
        long b = getInitial();
        for (Record r : records) b += r.saved();
        return b;
    }

    public void put(Record r) {
        if (r.id == 0) {
            r.id = System.currentTimeMillis();
            records.add(r);
        }
        sortAndSave();
    }

    public void delete(Record r) {
        records.remove(r);
        sortAndSave();
    }

    private void sortAndSave() {
        Collections.sort(records, (a, b) -> {
            if (a.yearMonth != b.yearMonth) return Integer.compare(b.yearMonth, a.yearMonth);
            return Long.compare(b.id, a.id);
        });
        JSONArray arr = new JSONArray();
        try {
            for (Record r : records) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("type", r.type);
                o.put("ym", r.yearMonth);
                o.put("main", r.main);
                o.put("side", r.side);
                o.put("mainPct", r.mainPct);
                o.put("sidePct", r.sidePct);
                o.put("note", r.note);
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        prefs.edit().putString("records", arr.toString()).apply();
    }

    private void load() {
        records.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("records", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Record r = new Record();
                r.id = o.getLong("id");
                r.type = o.optInt("type", TYPE_INCOME);
                r.yearMonth = o.getInt("ym");
                r.main = o.getLong("main");
                r.side = o.optLong("side", 0);
                r.mainPct = o.optDouble("mainPct", 0);
                r.sidePct = o.optDouble("sidePct", 0);
                r.note = o.optString("note", "");
                records.add(r);
            }
        } catch (JSONException ignored) {
        }
    }
}
