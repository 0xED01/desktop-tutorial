package com.yuandian.quote.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地 SQLite 数据库。数据保存在手机内部存储，关闭 App 后依然存在。
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                company TEXT NOT NULL,
                contact TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT '',
                remark TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                model TEXT NOT NULL DEFAULT '',
                spec TEXT NOT NULL DEFAULT '',
                unit TEXT NOT NULL DEFAULT '',
                price_cents INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE quotes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                quote_no TEXT NOT NULL UNIQUE,
                customer_id INTEGER,
                customer_company TEXT NOT NULL,
                customer_contact TEXT NOT NULL DEFAULT '',
                customer_phone TEXT NOT NULL DEFAULT '',
                quote_date TEXT NOT NULL,
                valid_until TEXT NOT NULL,
                remark TEXT NOT NULL DEFAULT '',
                total_cents INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE quote_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                quote_id INTEGER NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
                product_id INTEGER,
                name TEXT NOT NULL,
                model TEXT NOT NULL DEFAULT '',
                spec TEXT NOT NULL DEFAULT '',
                unit TEXT NOT NULL DEFAULT '',
                quantity TEXT NOT NULL,
                unit_price_cents INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_items_quote ON quote_items(quote_id)")
        DemoData.seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 第一版无需迁移；后续版本在这里按版本号逐步升级表结构。
    }

    companion object {
        const val DB_NAME = "yuandian_quote.db"
        const val DB_VERSION = 1
    }
}
