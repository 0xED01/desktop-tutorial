package com.yuandian.quote.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.yuandian.quote.util.Dates
import com.yuandian.quote.util.Money

/**
 * 首次安装时写入的演示数据。
 * 以下公司、人名、电话均为虚构，仅用于功能演示，如有雷同纯属巧合。
 */
internal object DemoData {

    private val customers = listOf(
        arrayOf("青禾机电设备有限公司", "林晓峰", "138 0000 1201", "老客户，月结 30 天"),
        arrayOf("远帆五金制品厂", "周明远", "139 0000 2302", "主要采购紧固件"),
        arrayOf("东岳液压技术有限公司", "陈思雨", "137 0000 3403", "关注交期，需提前备货"),
        arrayOf("北辰智能装备有限公司", "王立新", "136 0000 4504", ""),
        arrayOf("鼎盛建材贸易商行", "赵建国", "135 0000 5605", "小批量多批次"),
        arrayOf("恒通工业配件有限公司", "孙雅琴", "188 0000 6706", "开票需提供盖章报价单"),
        arrayOf("瑞丰农机服务中心", "刘志强", "187 0000 7807", ""),
        arrayOf("启明自动化工程有限公司", "郑可欣", "186 0000 8908", "项目型客户"),
    )

    // 名称, 型号, 规格, 单位, 单价(元)
    private val products = listOf(
        arrayOf("深沟球轴承", "6205-2RS", "25×52×15mm 双面胶封", "个", "12.80"),
        arrayOf("深沟球轴承", "6308-ZZ", "40×90×23mm 双面铁封", "个", "46.50"),
        arrayOf("外六角螺栓", "M12×50", "8.8级 镀锌", "盒", "68.00"),
        arrayOf("不锈钢六角螺母", "M10", "304不锈钢 100只/包", "包", "35.00"),
        arrayOf("三相异步电动机", "YE3-132M-4", "7.5kW 380V 1450r/min", "台", "2680.00"),
        arrayOf("齿轮减速机", "RV63-30", "速比1:30 输入孔径19mm", "台", "860.00"),
        arrayOf("液压油管总成", "DN16-2SN", "两层钢丝编织 长1.5m", "根", "128.00"),
        arrayOf("气动电磁阀", "4V210-08", "AC220V 两位五通", "个", "95.00"),
        arrayOf("不锈钢球阀", "Q41F-16P", "DN50 法兰连接", "个", "420.00"),
        arrayOf("同步带", "HTD 8M-1200", "宽30mm 橡胶", "条", "76.00"),
        arrayOf("工业链条", "16A-1", "单排 1米/条", "米", "58.00"),
        arrayOf("角磨机切割片", "350×3.2×25.4", "金属专用 25片/箱", "箱", "215.00"),
        arrayOf("变频器", "VFD-7.5K", "7.5kW 三相380V", "台", "1960.00"),
        arrayOf("直线导轨", "HGR20", "长1000mm 含滑块2只", "套", "385.00"),
        arrayOf("镀锌钢管", "DN25", "壁厚3.25mm 6米/根", "根", "142.00"),
    )

    fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val customerIds = customers.mapIndexed { i, c ->
                db.insert("customers", null, ContentValues().apply {
                    put("company", c[0]); put("contact", c[1]); put("phone", c[2]); put("remark", c[3])
                    put("created_at", now - (customers.size - i) * 60_000L)
                })
            }
            val productIds = products.mapIndexed { i, p ->
                db.insert("products", null, ContentValues().apply {
                    put("name", p[0]); put("model", p[1]); put("spec", p[2]); put("unit", p[3])
                    put("price_cents", Money.parseYuanToCents(p[4]))
                    put("created_at", now - (products.size - i) * 60_000L)
                })
            }

            // 三张演示报价单：(客户下标, 距今天数, 明细[商品下标, 数量, 报价单价(元)])
            val demoQuotes = listOf(
                Triple(0, 6L, listOf(Triple(4, "2", "2580.00"), Triple(12, "2", "1880.00"), Triple(0, "50", "12.00"))),
                Triple(2, 3L, listOf(Triple(6, "20", "120.00"), Triple(8, "6", "405.00"), Triple(7, "12", "92.00"))),
                Triple(5, 1L, listOf(Triple(2, "10", "65.00"), Triple(3, "15", "33.50"), Triple(10, "25.5", "56.00"), Triple(9, "8", "72.00"))),
            )
            val today = Dates.today()
            val seqPerDay = HashMap<String, Int>()
            demoQuotes.forEach { (ci, daysAgo, lines) ->
                val date = today.minusDays(daysAgo)
                val key = Dates.compact(date)
                val seq = (seqPerDay[key] ?: 0) + 1
                seqPerDay[key] = seq
                val c = customers[ci]
                val items = lines.map { (pi, qty, price) ->
                    val p = products[pi]
                    QuoteItem(
                        productId = productIds[pi], name = p[0], model = p[1], spec = p[2], unit = p[3],
                        quantity = qty, unitPriceCents = Money.parseYuanToCents(price)!!,
                    )
                }
                val total = items.sumOf { it.amountCents }
                val ts = now - daysAgo * 86_400_000L
                val quoteId = db.insert("quotes", null, ContentValues().apply {
                    put("quote_no", "YD$key-%03d".format(seq))
                    put("customer_id", customerIds[ci])
                    put("customer_company", c[0]); put("customer_contact", c[1]); put("customer_phone", c[2])
                    put("quote_date", Dates.format(date))
                    put("valid_until", Dates.format(date.plusDays(15)))
                    put("remark", "含13%增值税专用发票，含运费送货上门。")
                    put("total_cents", total)
                    put("created_at", ts); put("updated_at", ts)
                })
                items.forEachIndexed { idx, it ->
                    db.insert("quote_items", null, ContentValues().apply {
                        put("quote_id", quoteId); put("product_id", it.productId)
                        put("name", it.name); put("model", it.model); put("spec", it.spec); put("unit", it.unit)
                        put("quantity", it.quantity); put("unit_price_cents", it.unitPriceCents)
                        put("sort_order", idx)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
