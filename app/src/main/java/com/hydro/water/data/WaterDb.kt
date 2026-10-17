package com.hydro.water.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneId

class WaterDb(context: Context) : SQLiteOpenHelper(context, "water.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, " +
                "day INTEGER NOT NULL, amount INTEGER NOT NULL, type INTEGER NOT NULL, effective INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_day ON records(day)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun insert(ts: Long, amount: Int, type: Int, id: Long? = null): Long {
        val day = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().key()
        val v = ContentValues().apply {
            id?.let { put("id", it) }
            put("ts", ts)
            put("day", day)
            put("amount", amount)
            put("type", type)
            put("effective", Math.round(amount * drinkType(type).factor))
        }
        return writableDatabase.insert("records", null, v)
    }

    fun delete(id: Long) {
        writableDatabase.delete("records", "id=?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("records", null, null)
    }

    fun recordsBetween(fromDay: Int, toDay: Int): List<Record> {
        val list = mutableListOf<Record>()
        readableDatabase.rawQuery(
            "SELECT id, ts, amount, type, effective FROM records WHERE day BETWEEN ? AND ? ORDER BY ts DESC",
            arrayOf(fromDay.toString(), toDay.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list += Record(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getInt(4))
            }
        }
        return list
    }

    fun dailyTotals(): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        readableDatabase.rawQuery("SELECT day, SUM(effective) FROM records GROUP BY day", null).use { c ->
            while (c.moveToNext()) map[c.getInt(0)] = c.getInt(1)
        }
        return map
    }

    fun totalCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM records", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
}
