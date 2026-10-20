package com.applestock.pagewatch

import org.junit.Assert.assertEquals
import org.junit.Test

class PageCheckTest {
    private val inKw = PageCheck.keywords(PageCheck.DEFAULT_IN)
    private val outKw = PageCheck.keywords(PageCheck.DEFAULT_OUT)

    private fun check(text: String) = PageCheck.evaluate(text, inKw, outKw).stock

    @Test
    fun loginPage() {
        // 真实抓到的中国移动登录页文字
        assertEquals(Stock.NEED_LOGIN, check("获取验证码\n查看并同意《服务协议》、《隐私政策》\n登录\n 3天免登录\n自助查询、业务办理"))
    }

    @Test
    fun inAndOutOfStock() {
        assertEquals(Stock.IN_STOCK, check("iPhone Duo 512GB 夜空色 ¥15999 选择颜色 立即购买"))
        assertEquals(Stock.OUT_OF_STOCK, check("iPhone Duo 512GB 夜空色 ¥15999 已售罄 到货通知"))
        // 两种都出现时按无货处理
        assertEquals(Stock.OUT_OF_STOCK, check("立即购买 当前规格暂时无货"))
        assertEquals(Stock.UNKNOWN, check("iPhone Duo 商品详情 参数"))
    }

    @Test
    fun customKeywords() {
        assertEquals(listOf("立即购买", "抢购"), PageCheck.keywords(" 立即购买，抢购 "))
    }
}
