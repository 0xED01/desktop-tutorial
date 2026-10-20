package com.applestock.pagewatch

/** 根据页面上的文字判断状态。纯函数，便于测试。 */
enum class Stock { IN_STOCK, OUT_OF_STOCK, NEED_LOGIN, UNKNOWN }

data class CheckResult(val stock: Stock, val matched: String, val excerpt: String)

object PageCheck {
    const val DEFAULT_IN = "立即购买,立即抢购,马上抢,立即下单,加入购物车"
    const val DEFAULT_OUT = "到货通知,到货提醒,已售罄,售罄,缺货,暂时无货,无货,已抢光,即将开售,敬请期待"

    fun keywords(text: String): List<String> =
        text.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun evaluate(pageText: String, inKeywords: List<String>, outKeywords: List<String>): CheckResult {
        val text = pageText.replace(Regex("\\s+"), " ").trim()
        val excerpt = text.take(160)
        if (text.contains("获取验证码") && (text.contains("免登录") || text.contains("短信验证码登录") || text.length < 300)) {
            return CheckResult(Stock.NEED_LOGIN, "获取验证码", excerpt)
        }
        // 先看“无货”关键字：页面同时出现“立即购买”和“到货通知”时，按无货处理，宁可漏报也不乱响
        outKeywords.firstOrNull { text.contains(it) }?.let { return CheckResult(Stock.OUT_OF_STOCK, it, excerpt) }
        inKeywords.firstOrNull { text.contains(it) }?.let { return CheckResult(Stock.IN_STOCK, it, excerpt) }
        return CheckResult(Stock.UNKNOWN, "", excerpt)
    }
}
