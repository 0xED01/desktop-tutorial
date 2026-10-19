package com.balancesheet.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Asset/liability items plus one net-worth snapshot per month. Amounts are in cents.
 * The current month's snapshot is rewritten on every change, so the trend builds itself.
 */
public class Store {

    public static final int ASSET = 0;
    public static final int LIABILITY = 1;

    public static final String[] ASSET_CATEGORIES = {"现金活期", "存款理财", "股票基金", "房产", "车辆", "借出款", "公积金", "其他资产"};
    public static final String[] LIABILITY_CATEGORIES = {"信用卡", "房贷", "车贷", "消费贷", "借入款", "其他负债"};

    public static class Item {
        public long id;
        public int side;
        public String category;
        public String name;
        public long amount;
    }

    public static class Snapshot {
        public int yearMonth;
        public long assets;
        public long liabilities;

        public long net() { return assets - liabilities; }
    }

    private final SharedPreferences prefs;
    private final List<Item> items = new ArrayList<>();
    private final TreeMap<Integer, Snapshot> snapshots = new TreeMap<>();

    public Store(Context ctx) {
        prefs = ctx.getSharedPreferences("balance", Context.MODE_PRIVATE);
        load();
    }

    public List<Item> items(int side, String category) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.side == side && i.category.equals(category)) out.add(i);
        Collections.sort(out, (a, b) -> Long.compare(b.amount, a.amount));
        return out;
    }

    public long total(int side) {
        long sum = 0;
        for (Item i : items) if (i.side == side) sum += i.amount;
        return sum;
    }

    public boolean isEmpty() { return items.isEmpty(); }

    /** Oldest first. */
    public List<Snapshot> snapshots() { return new ArrayList<>(snapshots.values()); }

    public void put(Item item) {
        if (item.id == 0) {
            item.id = System.currentTimeMillis();
            items.add(item);
        }
        save();
    }

    public void delete(Item item) {
        items.remove(item);
        save();
    }

    public void deleteSnapshot(int ym) {
        if (ym == currentYm()) return; // current month always mirrors live data
        snapshots.remove(ym);
        save();
    }

    public static int currentYm() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1;
    }

    private void updateCurrentSnapshot() {
        Snapshot s = new Snapshot();
        s.yearMonth = currentYm();
        s.assets = total(ASSET);
        s.liabilities = total(LIABILITY);
        snapshots.put(s.yearMonth, s);
    }

    private void save() {
        updateCurrentSnapshot();
        try {
            JSONArray arr = new JSONArray();
            for (Item i : items) {
                JSONObject o = new JSONObject();
                o.put("id", i.id);
                o.put("side", i.side);
                o.put("cat", i.category);
                o.put("name", i.name);
                o.put("amount", i.amount);
                arr.put(o);
            }
            JSONObject snaps = new JSONObject();
            for (Snapshot s : snapshots.values()) {
                JSONObject o = new JSONObject();
                o.put("a", s.assets);
                o.put("l", s.liabilities);
                snaps.put(String.valueOf(s.yearMonth), o);
            }
            prefs.edit().putString("items", arr.toString()).putString("snapshots", snaps.toString()).apply();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void load() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("items", "[]"));
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                Item i = new Item();
                i.id = o.getLong("id");
                i.side = o.getInt("side");
                i.category = o.getString("cat");
                i.name = o.optString("name", "");
                i.amount = o.getLong("amount");
                items.add(i);
            }
            JSONObject snaps = new JSONObject(prefs.getString("snapshots", "{}"));
            Iterator<String> keys = snaps.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject o = snaps.getJSONObject(k);
                Snapshot s = new Snapshot();
                s.yearMonth = Integer.parseInt(k);
                s.assets = o.getLong("a");
                s.liabilities = o.getLong("l");
                snapshots.put(s.yearMonth, s);
            }
        } catch (JSONException ignored) {
        }
        // Carry existing data into a new month as soon as the app is opened.
        if (!items.isEmpty() && !snapshots.containsKey(currentYm())) save();
    }
}
