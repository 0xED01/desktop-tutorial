package com.yuandian.quote.shot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yuandian.quote.MainActivity
import com.yuandian.quote.data.CompanyProfileStore
import com.yuandian.quote.data.Quote
import com.yuandian.quote.data.QuoteItem
import com.yuandian.quote.pdf.QuotePdfGenerator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class TutorialShots {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val out = File("build/shots").apply { mkdirs() }
    private val frames = StringBuilder()
    private var n = 0

    private fun waitText(t: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun settle() { rule.waitForIdle(); Thread.sleep(300); rule.mainClock.advanceTimeBy(800); rule.waitForIdle() }

    private fun tab(label: String) = rule.onNode(
        hasText(label) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
    )

    private fun esc(s: String) = s.replace("\"", "\\\"")

    /** 截一帧；tap 不为空时在该控件中心画点击提示 */
    private fun frame(title: String, sub: String, tap: SemanticsNodeInteraction? = null, hold: Double = 2.6) {
        settle()
        val name = "f%02d.png".format(n++)
        captureScreenRoboImage(File(out, name).path)
        val pos = tap?.fetchSemanticsNode()?.boundsInWindow?.let { "[${it.center.x.toInt()},${it.center.y.toInt()}]" } ?: "null"
        frames.append("""{"file":"$name","title":"${esc(title)}","sub":"${esc(sub)}","tap":$pos,"hold":$hold},""").append('\n')
    }

    @Test fun tutorial() {
        waitText("最近报价"); waitText("恒通")
        frame("原点报价助手", "首页工作台：客户、商品、报价一目了然", hold = 3.0)

        // ---- 添加客户 ----
        val tabCustomer = tab("客户")
        frame("第 1 步 · 添加客户", "点击底部「客户」", tabCustomer, hold = 2.0)
        tabCustomer.performClick(); waitText("位客户")
        val fab = rule.onNodeWithText("添加客户", useUnmergedTree = true)
        frame("第 1 步 · 添加客户", "点击右下角「添加客户」", fab, hold = 2.0)
        fab.performClick(); waitText("公司名称")
        val f = rule.onAllNodes(hasSetTextAction())
        f[0].performTextInput("星河精密机械有限公司")
        f[1].performTextInput("何静")
        f[2].performTextInput("135 0000 9012")
        f[3].performTextInput("新客户，关注交货期")
        val save = rule.onNodeWithText("保存", useUnmergedTree = true)
        frame("第 1 步 · 添加客户", "填写公司名称、联系人、电话，点「保存」", save, hold = 3.2)
        save.performClick(); waitText("星河精密")
        frame("客户已保存", "支持按公司、联系人、电话搜索", hold = 2.2)

        // ---- 添加商品 ----
        val tabProduct = tab("商品")
        tabProduct.performClick(); waitText("个商品")
        val pfab = rule.onNodeWithText("添加商品", useUnmergedTree = true)
        frame("第 2 步 · 添加商品", "进入「商品」，点击「添加商品」", pfab, hold = 2.4)
        pfab.performClick(); waitText("商品名称")
        val pf = rule.onAllNodes(hasSetTextAction())
        pf[0].performTextInput("不锈钢法兰盘")
        pf[1].performTextInput("PL-DN80")
        pf[2].performTextInput("304不锈钢 PN16 平焊")
        pf[3].performTextInput("168")
        rule.onNode(hasText("件") and hasClickAction()).performClick()
        val psave = rule.onNodeWithText("保存", useUnmergedTree = true)
        frame("第 2 步 · 添加商品", "名称、型号、规格、单价、单位，点「保存」", psave, hold = 3.2)
        psave.performClick(); waitText("不锈钢法兰盘")
        frame("商品已入库", "建一次商品库，报价时直接选用", hold = 2.2)

        // ---- 新建报价单 ----
        tab("工作台").performClick(); waitText("新建报价单")
        val newQuote = rule.onNodeWithText("新建报价单", useUnmergedTree = true)
        frame("第 3 步 · 新建报价单", "在工作台点击「新建报价单」", newQuote, hold = 2.2)
        newQuote.performClick(); waitText("请选择客户")
        val pickCustomer = rule.onAllNodesWithText("请选择客户", useUnmergedTree = true)[0]
        frame("第 3 步 · 选择客户", "报价编号自动生成，点击选择客户", pickCustomer, hold = 2.6)
        pickCustomer.performClick(); waitText("选择客户"); waitText("星河精密")
        val cust = rule.onNodeWithText("星河精密机械有限公司", useUnmergedTree = true)
        frame("第 3 步 · 选择客户", "搜索或直接点选客户", cust, hold = 2.2)
        cust.performClick(); waitText("从商品库选择商品")
        val pickProduct = rule.onNodeWithText("从商品库选择商品", useUnmergedTree = true)
        frame("第 4 步 · 选择商品", "从商品库添加商品", pickProduct, hold = 2.2)
        pickProduct.performClick(); waitText("选择商品"); waitText("不锈钢法兰盘")
        rule.onNodeWithText("不锈钢法兰盘", useUnmergedTree = true).performClick()
        rule.onNodeWithText("变频器", useUnmergedTree = true).performClick()
        rule.onNodeWithText("直线导轨", useUnmergedTree = true).performClick()
        val addSel = rule.onNodeWithText("添加所选商品", substring = true, useUnmergedTree = true)
        frame("第 4 步 · 选择商品", "可一次勾选多个商品", addSel, hold = 2.8)
        addSel.performClick(); waitText("小计")

        val e = rule.onAllNodes(hasSetTextAction())
        e[0].performTextReplacement("20")          // 法兰盘 20 个
        e[2].performTextReplacement("2")           // 变频器 2 台
        e[3].performTextReplacement("1880")        // 变频器 议价 1880
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("直线导轨"))
        settle()
        rule.onNode(hasScrollAction()).performScrollToIndex(3)
        frame("第 5 步 · 修改数量和单价", "数量、单价随意改，小计与合计自动计算", rule.onNodeWithText("合计", substring = true, useUnmergedTree = true), hold = 3.6)

        val saveQuote = rule.onNodeWithText("保存报价", useUnmergedTree = true)
        frame("第 6 步 · 保存报价", "点击「保存报价」，随时可再查看、编辑", saveQuote, hold = 2.4)
        saveQuote.performClick(); waitText("报价单详情"); waitText("PDF 预览"); waitText("共 1 页")
        frame("报价单已生成", "自动生成 PDF，含金额大写", hold = 3.0)

        rule.onNode(hasScrollAction()).performScrollToIndex(2)
        val shareBtn = rule.onNodeWithText("分享 PDF", useUnmergedTree = true)
        frame("第 7 步 · 导出 PDF", "可保存到手机，或直接分享", rule.onNodeWithText("保存到手机", useUnmergedTree = true), hold = 3.0)
        frame("第 8 步 · 发送给客户", "点击「分享 PDF」，选择微信即可发送", shareBtn, hold = 3.0)

        // 高清 PDF 页面（与 App 内使用同一套排版代码）
        val ctx = RuntimeEnvironment.getApplication()
        val profile = CompanyProfileStore(ctx).profile.value
        val q = Quote(
            quoteNo = "YD20261031-001", customerId = null, customerCompany = "星河精密机械有限公司",
            customerContact = "何静", customerPhone = "135 0000 9012",
            quoteDate = "2026-10-31", validUntil = "2026-11-15", remark = "", totalCents = 0,
            items = listOf(
                QuoteItem(name = "不锈钢法兰盘", model = "PL-DN80", spec = "304不锈钢 PN16 平焊", unit = "件", quantity = "20", unitPriceCents = 16800),
                QuoteItem(name = "变频器", model = "VFD-7.5K", spec = "7.5kW 三相380V", unit = "台", quantity = "2", unitPriceCents = 188000),
                QuoteItem(name = "直线导轨", model = "HGR20", spec = "长1000mm 含滑块2只", unit = "套", quantity = "1", unitPriceCents = 38500),
            ),
        )
        QuotePdfGenerator().renderToBitmaps(q, profile, 2f).first().let { bmp ->
            File(out, "pdf_page.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(out, "frames.json").writeText("[\n" + frames.toString().trimEnd(',', '\n') + "\n]\n")
    }
}
