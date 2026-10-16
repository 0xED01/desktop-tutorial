package com.applestock.monitor

data class AlertInfo(
    val part: String,
    val title: String,
    val note: String,
    val stores: List<StoreStatus>,
    val isNew: Boolean,
) {
    val url get() = Apple.productUrl(part)

    fun text(): Pair<String, String> {
        val name = title.ifEmpty { note.ifEmpty { part } }
        val list = stores.joinToString("、") { "${it.name}（${it.quote.ifEmpty { "可取货" }}）" }
        return "有货啦！$name" to "型号 $part 可门店取货：$list\n赶快下单：$url"
    }
}

/** 每个型号一行的汇总结果，显示在界面上。 */
data class PartRow(
    val part: String,
    val label: String,
    val inStock: List<StoreStatus>,
    val total: Int,
    val time: String,
)

interface MonitorEvents {
    fun log(text: String)
    fun row(row: PartRow)
    fun alert(info: AlertInfo)
}

/** 一轮检查的逻辑：分批查询、对比状态、只在「无货→有货」时提醒（可设置仍有货时重复提醒）。 */
class Monitor(
    private val parts: List<Pair<String, String>>,
    private val location: String,
    private val storeFilter: String,
    repeatMinutes: Int,
    private val events: MonitorEvents,
    private val checker: (List<String>, String) -> Map<String, List<StoreStatus>> = Apple::check,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val repeatMs = repeatMinutes.coerceAtLeast(0) * 60_000L
    private val state = HashMap<String, Boolean>()
    private val lastAlert = HashMap<String, Long>()
    private val invalidWarned = HashSet<String>()

    /** 返回 true 表示本轮没有出错。 */
    fun checkRound(): Boolean {
        var allOk = true
        var checked = 0
        var stocked = 0
        val stores = HashSet<String>()
        val batches = parts.chunked(Apple.BATCH_SIZE)
        for ((i, batch) in batches.withIndex()) {
            if (i > 0) pause((1500..3000).random().toLong())
            val names = if (batch.size <= 2) batch.joinToString("、") { it.first }
            else "${batch[0].first} 等 ${batch.size} 个型号"
            val results = try {
                checker(batch.map { it.first }, location)
            } catch (e: ApiException) {
                allOk = false
                events.log("[$names] 查询失败：${e.message}")
                continue
            } catch (e: Exception) {
                allOk = false
                events.log("[$names] 未知错误：$e")
                continue
            }
            for ((part, note) in batch) {
                val list = results[part]
                if (list == null) {
                    if (invalidWarned.add(part)) {
                        events.log("[$part] 官网没有返回这个型号，可能型号代码有误或不支持门店取货")
                    }
                    continue
                }
                val filtered = Apple.filterStores(list, storeFilter)
                filtered.forEach { stores.add(it.store) }
                checked++
                if (handle(part, note, filtered)) stocked++
            }
        }
        if (checked > 0) {
            events.log("本轮检查 $checked 个型号 × ${stores.size} 家门店：" +
                if (stocked > 0) "$stocked 个型号有货！" else "全部无货")
        }
        return allOk
    }

    private fun handle(part: String, note: String, results: List<StoreStatus>): Boolean {
        val title = results.firstOrNull { it.title.isNotEmpty() }?.title ?: ""
        val newly = ArrayList<StoreStatus>()
        val inStock = ArrayList<StoreStatus>()
        for (r in results) {
            val prev = state.put("$part|${r.store}", r.available)
            if (r.available) {
                inStock.add(r)
                if (prev != true) newly.add(r)
            }
        }
        val label = title.ifEmpty { note.ifEmpty { part } }
        events.row(PartRow(part, label, inStock, results.size, timeNow()))
        if (inStock.isNotEmpty()) {
            events.log("[$part] $label：${inStock.size} 家门店有货 → " + inStock.joinToString("、") { it.name })
        }
        val t = now()
        val due = repeatMs > 0 && inStock.isNotEmpty() && t - (lastAlert[part] ?: 0L) >= repeatMs
        if (newly.isNotEmpty() || due) {
            lastAlert[part] = t
            events.alert(AlertInfo(part, title, note, inStock, newly.isNotEmpty()))
        }
        return inStock.isNotEmpty()
    }
}

fun timeNow(pattern: String = "HH:mm:ss"): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.CHINA).format(java.util.Date())
