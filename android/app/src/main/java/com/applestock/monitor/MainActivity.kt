package com.applestock.monitor

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var parts: EditText
    private lateinit var location: EditText
    private lateinit var storeFilter: EditText
    private lateinit var interval: EditText
    private lateinit var repeat: EditText
    private lateinit var maxVolume: CheckBox
    private lateinit var serverchan: EditText
    private lateinit var pushplus: EditText
    private lateinit var bark: EditText
    private lateinit var status: TextView
    private lateinit var rows: LinearLayout
    private lateinit var log: TextView
    private lateinit var webInfo: TextView
    private lateinit var webToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        MonitorService.createChannels(this)

        parts = findViewById(R.id.parts)
        location = findViewById(R.id.location)
        storeFilter = findViewById(R.id.storeFilter)
        interval = findViewById(R.id.interval)
        repeat = findViewById(R.id.repeat)
        maxVolume = findViewById(R.id.maxVolume)
        serverchan = findViewById(R.id.serverchan)
        pushplus = findViewById(R.id.pushplus)
        bark = findViewById(R.id.bark)
        status = findViewById(R.id.status)
        rows = findViewById(R.id.rows)
        log = findViewById(R.id.log)


        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<Button>(R.id.stop).setOnClickListener { MonitorService.stop(this) }
        findViewById<Button>(R.id.test).setOnClickListener {
            save()
            MonitorService.fireAlert(this, AlertInfo("TEST", "测试提醒", "",
                listOf(StoreStatus("R000", "测试门店", "", true, "今天可取货", "")), false))
        }
        findViewById<Button>(R.id.stopAlarm).setOnClickListener { AlarmPlayer.stop() }
        findViewById<Button>(R.id.background).setOnClickListener { backgroundSettings() }
        webInfo = findViewById(R.id.webInfo)
        webToggle = findViewById(R.id.webToggle)
        webToggle.setOnClickListener {
            save()
            MonitorService.setWeb(this, !MonitorService.webRunning)
            webToggle.postDelayed({ render() }, 800)
        }
        findViewById<Button>(R.id.webKey).setOnClickListener {
            prefs.newWebKey()
            toast("访问码已更换，网页上需要重新输入")
            render()
        }
        findViewById<Button>(R.id.lookup).setOnClickListener { lookupDialog() }

        if (State.logs().isEmpty()) {
            State.log("欢迎使用！填写型号和地区后点「开始监控」。第一次使用请先点「后台设置」并「测试提醒」。")
        }
        requestPermissions()
    }

    /** 从保存的设置读取到输入框（网页上可能改过设置，所以每次回到前台都重新读）。 */
    private fun loadFields() {
        parts.setText(prefs.parts)
        location.setText(prefs.location)
        storeFilter.setText(prefs.storeFilter)
        interval.setText(prefs.interval.toString())
        repeat.setText(prefs.repeatMinutes.toString())
        maxVolume.isChecked = prefs.maxVolume
        serverchan.setText(prefs.serverchanKey)
        pushplus.setText(prefs.pushplusToken)
        bark.setText(prefs.barkUrl)
    }

    override fun onResume() {
        super.onResume()
        loadFields()
        State.listener = { render() }
        render()
    }

    override fun onPause() {
        super.onPause()
        State.listener = null
        save()
    }

    private fun save() {
        prefs.parts = parts.text.toString().trim()
        prefs.location = location.text.toString().trim()
        prefs.storeFilter = storeFilter.text.toString().trim()
        prefs.interval = interval.text.toString().toIntOrNull()?.coerceIn(15, 3600) ?: 30
        prefs.repeatMinutes = repeat.text.toString().toIntOrNull()?.coerceIn(0, 600) ?: 5
        prefs.maxVolume = maxVolume.isChecked
        prefs.serverchanKey = serverchan.text.toString().trim()
        prefs.pushplusToken = pushplus.text.toString().trim()
        prefs.barkUrl = bark.text.toString().trim()
    }

    private fun start() {
        save()
        if (Apple.parseParts(prefs.parts).isEmpty()) {
            toast("请先填写至少一个型号代码（如 MG8L4CH/A），不知道可以点「型号查询」")
            return
        }
        if (prefs.location.isBlank()) {
            toast("请填写取货地区，例如：上海 上海 黄浦区")
            return
        }
        if (State.running) MonitorService.stop(this)
        MonitorService.start(this)
    }

    private fun render() {
        if (MonitorService.webRunning) {
            webInfo.text = "✅ 已开启。在同一 Wi-Fi 的电脑或 iPhone 浏览器打开：\n${MonitorService.webAddress()}\n访问码：${prefs.webKey}"
            webToggle.text = "关闭网页控制"
        } else {
            webInfo.text = "未开启。开启后，同一 Wi-Fi 下的电脑、iPhone 可以用浏览器查看状态、修改设置、开始/停止监控。"
            webToggle.text = "开启网页控制"
        }
        status.text = if (State.running) State.status.takeIf { it.startsWith("监控中") } ?: "监控中…"
        else "未在监控"
        status.setTextColor(if (State.running) Color.parseColor("#0A7D00") else Color.GRAY)
        rows.removeAllViews()
        val dp = resources.displayMetrics.density
        for (r in State.rows()) {
            val yes = r.inStock.isNotEmpty()
            rows.addView(TextView(this).apply {
                text = if (yes) "✅ ${r.label}\n    有货：${r.inStock.joinToString("、") { "${it.name}(${it.quote})" }}  ${r.time}"
                else "⚪ ${r.label}  无货（${r.total}店）  ${r.time}"
                textSize = 13f
                setPadding((4 * dp).toInt(), (5 * dp).toInt(), (4 * dp).toInt(), (5 * dp).toInt())
                setTextColor(if (yes) Color.parseColor("#0A5D00") else Color.DKGRAY)
                if (yes) setBackgroundColor(Color.parseColor("#C8F7C5"))
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Apple.productUrl(r.part))))
                }
            })
        }
        log.text = State.logs().takeLast(120).reversed().joinToString("\n")
    }

    private fun lookupDialog() {
        val input = EditText(this).apply {
            setText("https://www.apple.com.cn/shop/buy-iphone/iphone-17-pro")
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("型号查询")
            .setMessage("粘贴 Apple 官网购买页地址（选机型的那个页面）：")
            .setView(input)
            .setPositiveButton("获取型号") { _, _ -> fetchParts(input.text.toString().trim()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun fetchParts(url: String) {
        if (!url.startsWith("http")) return
        toast("正在获取…")
        thread {
            val result = runCatching { Apple.lookupParts(url) }
            runOnUiThread {
                val items = result.getOrNull()
                if (items.isNullOrEmpty()) {
                    toast(if (result.isFailure) "获取失败：${result.exceptionOrNull()?.message}" else "没找到型号，换个页面地址试试")
                    return@runOnUiThread
                }
                val labels = items.map { "${it.first}  ${it.second}" }.toTypedArray()
                val checked = BooleanArray(items.size)
                AlertDialog.Builder(this)
                    .setTitle("选择要监控的型号")
                    .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
                    .setPositiveButton("添加") { _, _ ->
                        val existing = Apple.parseParts(parts.text.toString()).map { it.first }.toSet()
                        val add = items.filterIndexed { i, it -> checked[i] && it.first !in existing }
                            .joinToString("\n") { "${it.first}  ${it.second}".trim() }
                        if (add.isNotEmpty()) {
                            val cur = parts.text.toString().trim()
                            parts.setText(if (cur.isEmpty()) add else "$cur\n$add")
                            save()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun backgroundSettings() {
        val pm = getSystemService(PowerManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        val fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        val msg = buildString {
            append("为了半夜锁屏时也能持续监控，请完成以下设置：\n\n")
            append("1. 电池优化：").append(if (ignoring) "✅ 已允许" else "❌ 未允许（点下方按钮设置）").append("\n")
            append("2. 锁屏全屏提醒：").append(if (fullScreen) "✅ 已允许" else "❌ 未允许（点「全屏提醒」开启）").append("\n")
            append("3. 国产手机（华为/小米/OPPO/vivo/荣耀）还需要手动：\n")
            append("   · 设置 → 应用 → 苹果库存监控 → 耗电/电池 → 选「无限制」或「允许后台活动」\n")
            append("   · 打开「自启动」/「允许关联启动」\n")
            append("   · 在最近任务里把本应用「锁定」（下拉或长按卡片）\n")
            append("4. 睡前插上充电器，并点「测试提醒」确认能响。")
        }
        AlertDialog.Builder(this)
            .setTitle("后台运行设置")
            .setMessage(msg)
            .setPositiveButton("电池优化") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    openAppSettings()
                }
            }
            .setNeutralButton("全屏提醒") { _, _ ->
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")))
                        return@setNeutralButton
                    } catch (_: Exception) {
                    }
                }
                openAppSettings()
            }
            .setNegativeButton("应用设置") { _, _ -> openAppSettings() }
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
