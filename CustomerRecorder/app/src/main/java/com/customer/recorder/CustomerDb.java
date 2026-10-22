package com.customer.recorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 本地 SQLite 存储。 */
public class CustomerDb extends SQLiteOpenHelper {

    private static final String TABLE = "customers";

    public CustomerDb(Context context) {
        super(context, "customers.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT, product TEXT, amount REAL, rate TEXT, approve_date TEXT,"
                + "status TEXT, debit_card TEXT, mobile_bank TEXT, note TEXT, raw TEXT,"
                + "created_at INTEGER)");
        addV2Columns(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) addV2Columns(db);
    }

    private static void addV2Columns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN activity TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift_cost REAL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift_given INTEGER DEFAULT 0");
    }

    /**
     * 保存记录。姓名+产品+首次审批日期相同视为同一条，更新而不重复添加。
     *
     * @return true 表示新增，false 表示更新已有记录
     */
    public boolean upsert(Customer c) {
        SQLiteDatabase db = getWritableDatabase();
        long existing = -1;
        try (Cursor cur = db.query(TABLE, new String[]{"id"},
                "name=? AND product=? AND approve_date=?",
                new String[]{c.name, c.product, c.approveDate}, null, null, null)) {
            if (cur.moveToFirst()) existing = cur.getLong(0);
        }
        ContentValues v = toValues(c);
        if (existing >= 0) {
            // 已有记录只更新审批信息，保留用户填写的活动、礼品、备注
            v.remove("note");
            v.remove("created_at");
            v.remove("activity");
            v.remove("gift");
            v.remove("gift_cost");
            v.remove("gift_given");
            db.update(TABLE, v, "id=?", new String[]{String.valueOf(existing)});
            c.id = existing;
            return false;
        }
        c.id = db.insert(TABLE, null, v);
        return true;
    }

    public void update(Customer c) {
        getWritableDatabase().update(TABLE, toValues(c), "id=?",
                new String[]{String.valueOf(c.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Customer> query(String keyword, int filter) {
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (name LIKE ? OR product LIKE ? OR note LIKE ?"
                    + " OR activity LIKE ? OR gift LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) args.add(k);
        }
        if (filter == 1) {
            where.append(" AND debit_card=?");
            args.add(MessageParser.NOT_OPENED);
        } else if (filter == 2) {
            where.append(" AND mobile_bank=?");
            args.add(MessageParser.NOT_OPENED);
        } else if (filter == 3) {
            where.append(" AND (debit_card=? OR mobile_bank=?)");
            args.add(MessageParser.NOT_OPENED);
            args.add(MessageParser.NOT_OPENED);
        } else if (filter == 4) {
            where.append(" AND gift<>'' AND gift_given=0");
        }
        List<Customer> list = new ArrayList<>();
        try (Cursor cur = getReadableDatabase().query(TABLE, null, where.toString(),
                args.toArray(new String[0]), null, null, "created_at DESC, id DESC")) {
            while (cur.moveToNext()) list.add(fromCursor(cur));
        }
        return list;
    }

    /** 某列出现过的不重复值，最近使用的在前，用于输入提示。 */
    public List<String> distinct(String column) {
        List<String> list = new ArrayList<>();
        try (Cursor cur = getReadableDatabase().rawQuery("SELECT " + column + " FROM " + TABLE
                + " WHERE " + column + "<>'' GROUP BY " + column
                + " ORDER BY MAX(created_at) DESC", null)) {
            while (cur.moveToNext()) list.add(cur.getString(0));
        }
        return list;
    }

    private static ContentValues toValues(Customer c) {
        ContentValues v = new ContentValues();
        v.put("name", c.name);
        v.put("product", c.product);
        v.put("amount", c.amount);
        v.put("rate", c.rate);
        v.put("approve_date", c.approveDate);
        v.put("status", c.status);
        v.put("debit_card", c.debitCard);
        v.put("mobile_bank", c.mobileBank);
        v.put("note", c.note);
        v.put("activity", c.activity);
        v.put("gift", c.gift);
        v.put("gift_cost", c.giftCost);
        v.put("gift_given", c.giftGiven ? 1 : 0);
        v.put("raw", c.raw);
        v.put("created_at", c.createdAt == 0 ? System.currentTimeMillis() : c.createdAt);
        return v;
    }

    private static Customer fromCursor(Cursor cur) {
        Customer c = new Customer();
        c.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
        c.name = str(cur, "name");
        c.product = str(cur, "product");
        c.amount = cur.getDouble(cur.getColumnIndexOrThrow("amount"));
        c.rate = str(cur, "rate");
        c.approveDate = str(cur, "approve_date");
        c.status = str(cur, "status");
        c.debitCard = str(cur, "debit_card");
        c.mobileBank = str(cur, "mobile_bank");
        c.note = str(cur, "note");
        c.activity = str(cur, "activity");
        c.gift = str(cur, "gift");
        c.giftCost = cur.getDouble(cur.getColumnIndexOrThrow("gift_cost"));
        c.giftGiven = cur.getInt(cur.getColumnIndexOrThrow("gift_given")) == 1;
        c.raw = str(cur, "raw");
        c.createdAt = cur.getLong(cur.getColumnIndexOrThrow("created_at"));
        return c;
    }

    private static String str(Cursor cur, String col) {
        String s = cur.getString(cur.getColumnIndexOrThrow(col));
        return s == null ? "" : s;
    }
}
