package com.applestock.pagewatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 网页有货监控：在内置浏览器里打开商品页（你自己登录），定时刷新，
 * 根据页面上的文字（「立即购买」/「到货通知」等）判断是否有货，有货就响铃提醒。
 * 只刷新和读取页面文字，不点击任何按钮、不下单。
 */
class MainActivity : Activity() {
    companion object {
        const val DEFAULT_URL = "https://wx.10086.cn/website/businessOnline/physicalDetail" +
            "?terminalPageId=2026092316440991402007663&pageSource=100&channelId=P00000133891"
        private const val RENDER_WAIT_MS = 6000L   // 页面加载完后再等一会儿，让页面内容渲染出来
        private const val LOAD_TIMEOUT_MS = 40000L
    }

    private val prefs by lazy { getSharedPreferences("config", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private lateinit var url: EditText
    private lateinit var interval: EditText
    private lateinit var inKw: EditText
    private lateinit var outKw: EditText
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var settings: LinearLayout

    private var running = false
    private var cycle = 0
    private var inspectedCycle = -1
    private var renderScheduled = false
    private var rounds = 0
    private var lastStock: Stock? = null
    private val logs = ArrayDeque<String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Notifier.channels(this)

        web = findViewById(R.id.web)
        url = findViewById(R.id.url)
        interval = findViewById(R.id.interval)
        inKw = findViewById(R.id.inKw)
        outKw = findViewById(R.id.outKw)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)
        settings = findViewById(R.id.settings)

        url.setText(prefs.getString("url", DEFAULT_URL))
        interval.setText(prefs.getInt("interval", 60).toString())
        inKw.setText(prefs.getString("in", PageCheck.DEFAULT_IN))
        outKw.setText(prefs.getString("out", PageCheck.DEFAULT_OUT))

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, u: String) {
                CookieManager.getInstance().flush() // 保存登录状态
                if (running && !renderScheduled) {
                    renderScheduled = true
                    val c = cycle
                    handler.postDelayed({ inspect(c) }, RENDER_WAIT_MS)
                }
            }
        }

        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<Button>(R.id.stop).setOnClickListener { stop() }
        findViewById<Button>(R.id.open).setOnClickListener {
            save()
            web.loadUrl(url.text.toString().trim())
            addLog("已打开页面：如需登录，请在下方页面里登录中国移动账号（勾选免登录）")
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            alarm("测试提醒", "这是一条测试提醒，能听到铃声说明设置正常。")
        }
        findViewById<Button>(R.id.stopAlarm).setOnClickListener {
            AlarmPlayer.stop()
        }
        findViewById<Button>(R.id.toggle).setOnClickListener {
            settings.visibility = if (settings.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.battery).setOnClickListener { batterySettings() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        web.loadUrl(url.text.toString().trim())
        addLog("第一次使用：先在下方页面登录中国移动账号，确认能看到商品页，再点「开始监控」")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("stop_alarm", false) == true) AlarmPlayer.stop()
    }

    override fun onPause() {
        super.onPause()
        save()
        // 注意：不调用 web.onPause()，这样切到后台时页面仍能继续刷新
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else moveTaskToBack(true) // 返回键不退出，避免误停监控
    }

    private fun save() {
        prefs.edit()
            .putString("url", url.text.toString().trim())
            .putInt("interval", interval.text.toString().toIntOrNull()?.coerceIn(20, 3600) ?: 60)
            .putString("in", inKw.text.toString())
            .putString("out", outKw.text.toString())
            .apply()
    }

    private fun start() {
        save()
        if (!url.text.toString().trim().startsWith("http")) {
            addLog("请先填写商品页链接")
            return
        }
        running = true
        lastStock = null
        rounds = 0
        startForegroundService(Intent(this, KeepAliveService::class.java))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings.visibility = View.GONE
        status.setTextColor(Color.parseColor("#0A7D00"))
        addLog("开始监控，间隔约 ${prefs.getInt("interval", 60)} 秒")
        nextCycle()
    }

    private fun stop() {
        running = false
        cycle++
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, KeepAliveService::class.java))
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status.text = "未在监控"
        status.setTextColor(Color.GRAY)
        addLog("监控已停止")
    }

    /** 开始新一轮：重新加载页面，加载完成后读取页面文字。 */
    private fun nextCycle() {
        if (!running) return
        cycle++
        renderScheduled = false
        val c = cycle
        web.loadUrl(prefs.getString("url", DEFAULT_URL)!!)
        // 网络卡住时页面可能一直加载不完，到时间也要检查并进入下一轮
        handler.postDelayed({ if (!renderScheduled) inspect(c) }, LOAD_TIMEOUT_MS)
    }

    private fun inspect(c: Int) {
        if (!running || c != cycle || inspectedCycle == c) return
        inspectedCycle = c
        web.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
            val text = try {
                JSONArray("[$raw]").optString(0, "")
            } catch (_: Exception) {
                ""
            }
            onPageText(text)
            val base = prefs.getInt("interval", 60)
            handler.postDelayed({ nextCycle() }, (base * Random.nextDouble(0.85, 1.15) * 1000).toLong())
        }
    }

    private fun onPageText(text: String) {
        rounds++
        val r = PageCheck.evaluate(text, PageCheck.keywords(inKw.text.toString()),
            PageCheck.keywords(outKw.text.toString()))
        val now = time()
        val desc = when (r.stock) {
            Stock.IN_STOCK -> "有货！（页面出现「${r.matched}」）"
            Stock.OUT_OF_STOCK -> "无货（页面出现「${r.matched}」）"
            Stock.NEED_LOGIN -> "需要登录：请停止监控，在下方页面登录后再开始"
            Stock.UNKNOWN -> "没认出状态：页面上没有找到设置的关键字"
        }
        status.text = "第 $rounds 次检查 $now：$desc"
        Notifier.updateRunning(this, "第 $rounds 次检查 $now：$desc")
        if (r.stock != lastStock) {
            addLog(desc + if (r.stock == Stock.UNKNOWN) "。页面开头文字：${r.excerpt}" else "")
            when (r.stock) {
                Stock.IN_STOCK -> alarm("有货啦！", "页面出现「${r.matched}」，赶快去下单！")
                Stock.NEED_LOGIN -> Notifier.alert(this, "网页监控需要重新登录", "登录已失效，请打开 App 重新登录。", urgent = false)
                else -> {}
            }
        }
        lastStock = r.stock
    }

    private fun alarm(title: String, text: String) {
        addLog("★★★ $title $text")
        AlarmPlayer.start(this)
        Notifier.alert(this, title, text, urgent = true)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("停止响铃") { _, _ -> AlarmPlayer.stop() }
            .show()
    }

    private fun batterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        val ok = pm.isIgnoringBatteryOptimizations(packageName)
        AlertDialog.Builder(this)
            .setTitle("后台运行设置")
            .setMessage("电池优化：" + (if (ok) "✅ 已允许" else "❌ 未允许") +
                "\n\n为了稳定刷新，建议：\n· 允许忽略电池优化\n· 国产手机把本应用耗电设为「无限制」、打开「自启动」\n" +
                "· 监控时最好让本 App 停留在屏幕上、插上充电器（App 会保持屏幕常亮，可以把亮度调低）")
            .setPositiveButton("电池优化") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun addLog(s: String) {
        logs.addFirst("${time()}  $s")
        while (logs.size > 60) logs.removeLast()
        log.text = logs.joinToString("\n")
    }

    private fun time() = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
}
