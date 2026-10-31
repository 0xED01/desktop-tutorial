package com.yuandian.quote.data

import com.yuandian.quote.util.Money

data class Customer(
    val id: Long = 0,
    val company: String,
    val contact: String = "",
    val phone: String = "",
    val remark: String = "",
    val createdAt: Long = 0,
)

data class Product(
    val id: Long = 0,
    val name: String,
    val model: String = "",
    val spec: String = "",
    val unit: String = "个",
    val priceCents: Long = 0,
    val createdAt: Long = 0,
)

/** 报价明细。商品信息以“快照”形式保存，商品库后续修改/删除不影响历史报价。 */
data class QuoteItem(
    val id: Long = 0,
    val quoteId: Long = 0,
    val productId: Long? = null,
    val name: String,
    val model: String = "",
    val spec: String = "",
    val unit: String = "",
    /** 数量以文本形式精确存储，如 "2.5" */
    val quantity: String,
    val unitPriceCents: Long,
) {
    val amountCents: Long
        get() = Money.parseQuantity(quantity)?.let { Money.lineAmount(it, unitPriceCents) } ?: 0L
}

/** 报价单。客户信息同样以快照保存。 */
data class Quote(
    val id: Long = 0,
    val quoteNo: String,
    val customerId: Long?,
    val customerCompany: String,
    val customerContact: String,
    val customerPhone: String,
    val quoteDate: String,
    val validUntil: String,
    val remark: String,
    val totalCents: Long,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val items: List<QuoteItem> = emptyList(),
)

/** 报价列表用的轻量摘要 */
data class QuoteSummary(
    val id: Long,
    val quoteNo: String,
    val customerCompany: String,
    val quoteDate: String,
    val validUntil: String,
    val totalCents: Long,
    val itemCount: Int,
)

data class DashboardStats(
    val customerCount: Int = 0,
    val productCount: Int = 0,
    val quoteCount: Int = 0,
    val monthTotalCents: Long = 0,
    val monthQuoteCount: Int = 0,
    val recent: List<QuoteSummary> = emptyList(),
)

/** 本公司（报价方）信息，打印在 PDF 抬头 */
data class CompanyProfile(
    val name: String,
    val contact: String,
    val phone: String,
    val address: String,
    val terms: String,
)
