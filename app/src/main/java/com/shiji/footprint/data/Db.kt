package com.shiji.footprint.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "footprint.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE points(id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER NOT NULL, " +
                "lat REAL NOT NULL, lon REAL NOT NULL, acc REAL, spd REAL, alt REAL, seg INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX idx_points_t ON points(t)")
        db.execSQL("CREATE TABLE cells(k INTEGER PRIMARY KEY)")
        db.execSQL(
            "CREATE TABLE days(d TEXT PRIMARY KEY, dist REAL NOT NULL DEFAULT 0, " +
                "n INTEGER NOT NULL DEFAULT 0, first INTEGER, last INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE places(country TEXT NOT NULL, province TEXT NOT NULL, city TEXT NOT NULL, " +
                "t INTEGER, PRIMARY KEY(country, province, city))"
        )
        db.execSQL("CREATE TABLE geokeys(k INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
