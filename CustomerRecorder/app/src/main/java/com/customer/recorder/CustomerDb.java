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
    private static final String ACTIVITIES = "activities";

    public static final int FILTER_ALL = 0;
    public static final int FILTER_NO_DEBIT = 1;
    public static final int FILTER_NO_MOBILE = 2;
    public static final int FILTER_NO_CREDIT = 3;
    public static final int FILTER_GIFT_PENDING = 4;

    /** 扫码活动：名称、礼品、单个礼品成本。 */
    public static class Act {
        public long id;
        public String name = "";
        public String gift = "";
        public double cost;
    }

    public CustomerDb(Context context) {
        super(context, "customers.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT, product TEXT, amount REAL, rate TEXT, approve_date TEXT,"
                + "status TEXT, debit_card TEXT, mobile_bank TEXT, note TEXT, raw TEXT,"
                + "created_at INTEGER)");
        addV2Columns(db);
        addV3Columns(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) addV2Columns(db);
        if (oldVersion < 3) addV3Columns(db);
    }

    private static void addV2Columns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN activity TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift_cost REAL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gift_given INTEGER DEFAULT 0");
    }

    private static void addV3Columns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN phone TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN credit_card TEXT DEFAULT '"
                + MessageParser.UNKNOWN + "'");
        db.execSQL("CREATE TABLE " + ACTIVITIES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, gift TEXT,"
                + "cost REAL, created_at INTEGER)");
        // 旧版本里填过的活动自动加入活动列表
        db.execSQL("INSERT OR IGNORE INTO " + ACTIVITIES + " (name, gift, cost, created_at)"
                + " SELECT activity, MAX(gift), MAX(gift_cost), MAX(created_at) FROM " + TABLE
                + " WHERE activity<>'' GROUP BY activity");
    }

    // ---------------- 客户 ----------------

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
            // 已有记录：只更新新填的内容，保留之前录入的备注、礼品发放等
            v.remove("note");
            v.remove("created_at");
            v.remove("gift_given");
            if (c.activity.isEmpty()) v.remove("activity");
            if (c.gift.isEmpty()) {
                v.remove("gift");
                v.remove("gift_cost");
            }
            if (c.phone.isEmpty()) v.remove("phone");
            if (MessageParser.UNKNOWN.equals(c.creditCard)) v.remove("credit_card");
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

    /**
     * 按首次审批通过日期筛选和排序。
     *
     * @param fromDay  审批日期下限（含），yyyy-MM-dd；null 表示不限日期
     * @param toDay    审批日期上限（不含），yyyy-MM-dd；null 表示不设上限
     * @param activity 为空表示全部活动
     */
    public List<Customer> query(String keyword, int filter, String fromDay, String toDay,
                                String activity) {
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (fromDay != null) {
            where.append(" AND approve_date>=?");
            args.add(fromDay);
        }
        if (toDay != null) {
            where.append(" AND approve_date<?");
            args.add(toDay);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (name LIKE ? OR product LIKE ? OR note LIKE ? OR phone LIKE ?"
                    + " OR activity LIKE ? OR gift LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) args.add(k);
        }
        if (activity != null && !activity.isEmpty()) {
            where.append(" AND activity=?");
            args.add(activity);
        }
        switch (filter) {
            case FILTER_NO_DEBIT:
                where.append(" AND debit_card=?");
                args.add(MessageParser.NOT_OPENED);
                break;
            case FILTER_NO_MOBILE:
                where.append(" AND mobile_bank=?");
                args.add(MessageParser.NOT_OPENED);
                break;
            case FILTER_NO_CREDIT:
                where.append(" AND credit_card=?");
                args.add(MessageParser.NOT_OPENED);
                break;
            case FILTER_GIFT_PENDING:
                where.append(" AND gift<>'' AND gift_given=0");
                break;
            default:
                break;
        }
        List<Customer> list = new ArrayList<>();
        try (Cursor cur = getReadableDatabase().query(TABLE, null, where.toString(),
                args.toArray(new String[0]), null, null,
                "approve_date='', approve_date DESC, created_at DESC, id DESC")) {
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

    // ---------------- 活动 ----------------

    public List<Act> activities() {
        List<Act> list = new ArrayList<>();
        try (Cursor cur = getReadableDatabase().query(ACTIVITIES, null, null, null, null, null,
                "created_at DESC, id DESC")) {
            while (cur.moveToNext()) {
                Act a = new Act();
                a.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
                a.name = str(cur, "name");
                a.gift = str(cur, "gift");
                a.cost = cur.getDouble(cur.getColumnIndexOrThrow("cost"));
                list.add(a);
            }
        }
        return list;
    }

    public Act findActivity(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Act a : activities()) {
            if (a.name.equals(name)) return a;
        }
        return null;
    }

    /** 新增或修改活动；改名时同步更新客户记录里的活动名。 */
    public void saveActivity(Act a, String oldName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", a.name);
        v.put("gift", a.gift);
        v.put("cost", a.cost);
        if (a.id > 0) {
            db.update(ACTIVITIES, v, "id=?", new String[]{String.valueOf(a.id)});
            if (oldName != null && !oldName.equals(a.name)) {
                ContentValues cv = new ContentValues();
                cv.put("activity", a.name);
                db.update(TABLE, cv, "activity=?", new String[]{oldName});
            }
        } else {
            v.put("created_at", System.currentTimeMillis());
            a.id = db.insertWithOnConflict(ACTIVITIES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public void deleteActivity(long id) {
        getWritableDatabase().delete(ACTIVITIES, "id=?", new String[]{String.valueOf(id)});
    }

    /** @return {客户数, 礼品成本合计, 礼品未送人数} */
    public double[] activityStats(String name) {
        try (Cursor cur = getReadableDatabase().rawQuery("SELECT COUNT(*), TOTAL(gift_cost),"
                + " TOTAL(CASE WHEN gift<>'' AND gift_given=0 THEN 1 ELSE 0 END)"
                + " FROM " + TABLE + " WHERE activity=?", new String[]{name})) {
            cur.moveToFirst();
            return new double[]{cur.getInt(0), cur.getDouble(1), cur.getDouble(2)};
        }
    }

    // ---------------- 转换 ----------------

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
        v.put("credit_card", c.creditCard);
        v.put("phone", c.phone);
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
        c.creditCard = str(cur, "credit_card");
        c.phone = str(cur, "phone");
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
