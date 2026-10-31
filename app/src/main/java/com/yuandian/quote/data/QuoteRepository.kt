package com.yuandian.quote.data

import android.content.ContentValues
import android.database.Cursor
import com.yuandian.quote.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * 数据仓库：所有数据库读写都在 IO 线程执行。
 * 写操作完成后递增 [changes]，所有 observeXxx() 会自动重新查询，界面随之刷新。
 */
class QuoteRepository(private val helper: AppDatabase) {

    private val changes = MutableStateFlow(0L)
    private fun notifyChanged() = changes.update { it + 1 }

    private val db get() = helper.writableDatabase

    private fun <T> observe(block: () -> T): Flow<T> =
        changes.map { block() }.flowOn(Dispatchers.IO)

    private fun like(q: String) = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    // ---------------- 客户 ----------------

    fun observeCustomers(query: String): Flow<List<Customer>> = observe {
        val q = query.trim()
        val c = if (q.isEmpty()) {
            db.rawQuery("SELECT * FROM customers ORDER BY id DESC", null)
        } else {
            val l = like(q)
            db.rawQuery(
                "SELECT * FROM customers WHERE company LIKE ? ESCAPE '\\' OR contact LIKE ? ESCAPE '\\' " +
                    "OR phone LIKE ? ESCAPE '\\' OR remark LIKE ? ESCAPE '\\' ORDER BY id DESC",
                arrayOf(l, l, l, l)
            )
        }
        c.use { cur -> buildList { while (cur.moveToNext()) add(cur.toCustomer()) } }
    }

    suspend fun getCustomer(id: Long): Customer? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM customers WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.toCustomer() else null
        }
    }

    suspend fun saveCustomer(c: Customer): Long = withContext(Dispatchers.IO) {
        val v = ContentValues().apply {
            put("company", c.company.trim()); put("contact", c.contact.trim())
            put("phone", c.phone.trim()); put("remark", c.remark.trim())
        }
        val id = if (c.id == 0L) {
            v.put("created_at", System.currentTimeMillis())
            db.insertOrThrow("customers", null, v)
        } else {
            db.update("customers", v, "id = ?", arrayOf(c.id.toString())); c.id
        }
        notifyChanged(); id
    }

    suspend fun deleteCustomer(id: Long) = withContext(Dispatchers.IO) {
        db.delete("customers", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------------- 商品 ----------------

    fun observeProducts(query: String): Flow<List<Product>> = observe {
        val q = query.trim()
        val c = if (q.isEmpty()) {
            db.rawQuery("SELECT * FROM products ORDER BY id DESC", null)
        } else {
            val l = like(q)
            db.rawQuery(
                "SELECT * FROM products WHERE name LIKE ? ESCAPE '\\' OR model LIKE ? ESCAPE '\\' " +
                    "OR spec LIKE ? ESCAPE '\\' ORDER BY id DESC",
                arrayOf(l, l, l)
            )
        }
        c.use { cur -> buildList { while (cur.moveToNext()) add(cur.toProduct()) } }
    }

    suspend fun getProduct(id: Long): Product? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM products WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.toProduct() else null
        }
    }

    suspend fun saveProduct(p: Product): Long = withContext(Dispatchers.IO) {
        val v = ContentValues().apply {
            put("name", p.name.trim()); put("model", p.model.trim()); put("spec", p.spec.trim())
            put("unit", p.unit.trim()); put("price_cents", p.priceCents)
        }
        val id = if (p.id == 0L) {
            v.put("created_at", System.currentTimeMillis())
            db.insertOrThrow("products", null, v)
        } else {
            db.update("products", v, "id = ?", arrayOf(p.id.toString())); p.id
        }
        notifyChanged(); id
    }

    suspend fun deleteProduct(id: Long) = withContext(Dispatchers.IO) {
        db.delete("products", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------------- 报价单 ----------------

    private val summarySql =
        "SELECT q.id, q.quote_no, q.customer_company, q.quote_date, q.valid_until, q.total_cents, " +
            "(SELECT COUNT(*) FROM quote_items i WHERE i.quote_id = q.id) AS item_count FROM quotes q"

    fun observeQuotes(query: String): Flow<List<QuoteSummary>> = observe { queryQuoteSummaries(query, null) }

    private fun queryQuoteSummaries(query: String, limit: Int?): List<QuoteSummary> {
        val q = query.trim()
        val lim = limit?.let { " LIMIT $it" } ?: ""
        val c = if (q.isEmpty()) {
            db.rawQuery("$summarySql ORDER BY q.quote_date DESC, q.id DESC$lim", null)
        } else {
            val l = like(q)
            db.rawQuery(
                "$summarySql WHERE q.quote_no LIKE ? ESCAPE '\\' OR q.customer_company LIKE ? ESCAPE '\\' " +
                    "OR q.customer_contact LIKE ? ESCAPE '\\' ORDER BY q.quote_date DESC, q.id DESC$lim",
                arrayOf(l, l, l)
            )
        }
        return c.use { cur ->
            buildList {
                while (cur.moveToNext()) add(
                    QuoteSummary(
                        id = cur.getLong(0), quoteNo = cur.getString(1), customerCompany = cur.getString(2),
                        quoteDate = cur.getString(3), validUntil = cur.getString(4),
                        totalCents = cur.getLong(5), itemCount = cur.getInt(6),
                    )
                )
            }
        }
    }

    fun observeDashboard(): Flow<DashboardStats> = observe {
        fun count(table: String) = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
        val monthPrefix = Dates.format(Dates.today()).substring(0, 7) + "%"
        val (monthCount, monthTotal) = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(total_cents), 0) FROM quotes WHERE quote_date LIKE ?",
            arrayOf(monthPrefix)
        ).use { it.moveToFirst(); it.getInt(0) to it.getLong(1) }
        DashboardStats(
            customerCount = count("customers"),
            productCount = count("products"),
            quoteCount = count("quotes"),
            monthTotalCents = monthTotal,
            monthQuoteCount = monthCount,
            recent = queryQuoteSummaries("", 5),
        )
    }

    suspend fun getQuote(id: Long): Quote? = withContext(Dispatchers.IO) { queryQuote(id) }

    /** 报价单内容变化（编辑保存）后自动推送最新数据；被删除时推送 null */
    fun observeQuote(id: Long): Flow<Quote?> = observe { queryQuote(id) }

    private fun queryQuote(id: Long): Quote? {
        val quote = db.rawQuery("SELECT * FROM quotes WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.toQuote() else null
        } ?: return null
        val items = db.rawQuery(
            "SELECT * FROM quote_items WHERE quote_id = ? ORDER BY sort_order, id", arrayOf(id.toString())
        ).use { cur -> buildList { while (cur.moveToNext()) add(cur.toQuoteItem()) } }
        return quote.copy(items = items)
    }

    /** 生成下一个报价编号：YD + 日期 + 当日流水号，例如 YD20261031-001 */
    suspend fun nextQuoteNo(date: String = Dates.format(Dates.today())): String = withContext(Dispatchers.IO) {
        val prefix = "YD" + Dates.compact(Dates.parse(date)) + "-"
        val last = db.rawQuery(
            "SELECT quote_no FROM quotes WHERE quote_no LIKE ? ORDER BY quote_no DESC LIMIT 1",
            arrayOf("$prefix%")
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        val seq = last?.substringAfterLast('-')?.toIntOrNull() ?: 0
        prefix + "%03d".format(seq + 1)
    }

    /** 保存报价单（新建或更新），明细整体替换。返回报价单 id。 */
    suspend fun saveQuote(q: Quote): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val total = q.items.sumOf { it.amountCents }
        db.beginTransaction()
        try {
            val v = ContentValues().apply {
                put("quote_no", q.quoteNo)
                if (q.customerId != null) put("customer_id", q.customerId) else putNull("customer_id")
                put("customer_company", q.customerCompany); put("customer_contact", q.customerContact)
                put("customer_phone", q.customerPhone)
                put("quote_date", q.quoteDate); put("valid_until", q.validUntil)
                put("remark", q.remark.trim()); put("total_cents", total)
                put("updated_at", now)
            }
            val id = if (q.id == 0L) {
                v.put("created_at", now)
                db.insertOrThrow("quotes", null, v)
            } else {
                db.update("quotes", v, "id = ?", arrayOf(q.id.toString()))
                db.delete("quote_items", "quote_id = ?", arrayOf(q.id.toString()))
                q.id
            }
            q.items.forEachIndexed { idx, it ->
                db.insertOrThrow("quote_items", null, ContentValues().apply {
                    put("quote_id", id)
                    if (it.productId != null) put("product_id", it.productId) else putNull("product_id")
                    put("name", it.name); put("model", it.model); put("spec", it.spec); put("unit", it.unit)
                    put("quantity", it.quantity); put("unit_price_cents", it.unitPriceCents)
                    put("sort_order", idx)
                })
            }
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
            notifyChanged()
        }
    }

    suspend fun deleteQuote(id: Long) = withContext(Dispatchers.IO) {
        db.delete("quotes", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------------- Cursor 映射 ----------------

    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.longOrNull(col: String): Long? =
        getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.toCustomer() = Customer(
        id = long("id"), company = str("company"), contact = str("contact"),
        phone = str("phone"), remark = str("remark"), createdAt = long("created_at"),
    )

    private fun Cursor.toProduct() = Product(
        id = long("id"), name = str("name"), model = str("model"), spec = str("spec"),
        unit = str("unit"), priceCents = long("price_cents"), createdAt = long("created_at"),
    )

    private fun Cursor.toQuote() = Quote(
        id = long("id"), quoteNo = str("quote_no"), customerId = longOrNull("customer_id"),
        customerCompany = str("customer_company"), customerContact = str("customer_contact"),
        customerPhone = str("customer_phone"), quoteDate = str("quote_date"), validUntil = str("valid_until"),
        remark = str("remark"), totalCents = long("total_cents"),
        createdAt = long("created_at"), updatedAt = long("updated_at"),
    )

    private fun Cursor.toQuoteItem() = QuoteItem(
        id = long("id"), quoteId = long("quote_id"), productId = longOrNull("product_id"),
        name = str("name"), model = str("model"), spec = str("spec"), unit = str("unit"),
        quantity = str("quantity"), unitPriceCents = long("unit_price_cents"),
    )
}
