package com.gomoku.lan

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private var screenView: View? = null
    private var backAction: (() -> Unit)? = null
    private var dialog: DialogHandle? = null
    private var snackView: View? = null
    private var dialogScrim: View? = null
    private var sysInsets = IntArray(4)

    private var host: Host? = null
    private var scanner: Scanner? = null
    private var pendingConn: Connection? = null
    private var game: GameScreen? = null
    private var connectToken = 0

    private val prefs by lazy { getSharedPreferences("gomoku", Context.MODE_PRIVATE) }
    val myName: String
        get() = prefs.getString("name", null) ?: cleanName(
            runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
                ?: Build.MODEL ?: "玩家"
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        setupWindow()
        root = FrameLayout(this).apply { setBackgroundColor(Palette.PAPER) }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        root.setOnApplyWindowInsetsListener { _, insets ->
            sysInsets = systemInsets(insets)
            val (l, t, r, b) = sysInsets
            content.setPadding(l, t, r, b)
            dialogScrim?.setPadding(l, t, r, b)
            insets
        }
        setContentView(root)
        showHome(animate = false)
    }

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    @Suppress("DEPRECATION")
    private fun systemInsets(insets: WindowInsets): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
            return intArrayOf(i.left, i.top, i.right, i.bottom)
        }
        return intArrayOf(
            insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
        )
    }

    // ───────────────────────── 导航 ─────────────────────────

    private fun go(view: View, animate: Boolean = true, back: (() -> Unit)?) {
        dismissDialog()
        hideKeyboard()
        val old = screenView
        screenView = view
        backAction = back
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        if (animate) {
            view.alpha = 0f
            view.translationY = dp(10).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        if (old != null) {
            old.isEnabled = false
            old.animate().alpha(0f).setDuration(160).withEndAction { content.removeView(old) }.start()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val d = dialog
        if (d != null) {
            if (d.cancelable) d.cancel()
            return
        }
        val b = backAction
        if (b != null) b() else super.onBackPressed()
    }

    override fun onDestroy() {
        stopNetworking()
        super.onDestroy()
    }

    private fun stopNetworking() {
        connectToken++
        host?.stop(); host = null
        scanner?.stop(); scanner = null
        pendingConn?.close(); pendingConn = null
        game?.dispose(); game = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ───────────────────────── 首页 ─────────────────────────

    fun showHome(animate: Boolean = true) {
        stopNetworking()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), 0, dp(28), dp(20))
        }
        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val hero = HeroView(this)
        col.addView(hero, LinearLayout.LayoutParams(dp(220), dp(220)))
        col.addView(label("五子棋", 44f, face = Fonts.serifBold, spacing = 0.28f),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        col.addView(label("GOMOKU  ·  LAN", 11f, Palette.MUTED, Fonts.sansMedium, 0.42f),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1.2f))

        val full = { top: Int -> LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) } }
        col.addView(pill("创建房间", PillStyle.PRIMARY) { showHost() }, full(0))
        col.addView(pill("加入房间", PillStyle.OUTLINE) { showJoin() }, full(12))
        col.addView(pill("本机双人", PillStyle.GHOST) { startLocal() }, full(6))

        val nameRow = label("", 12f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { editName(this) }
        }
        updateNameRow(nameRow)
        col.addView(nameRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) })
        col.addView(label("同一 Wi-Fi 或热点下即可联机对战", 11f, withAlpha(Palette.MUTED, 0.75f)),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })

        go(col, animate, back = null)
        hero.play()
    }

    private fun updateNameRow(tv: TextView) {
        tv.text = "昵称  ${myName}  ·  修改"
    }

    private fun editName(row: TextView) {
        val field = textField("昵称", myName)
        field.inputType = InputType.TYPE_CLASS_TEXT
        showDialog(
            title = "修改昵称",
            message = "联机时对手会看到这个名字",
            extra = field,
            primary = "保存",
            onPrimary = {
                val v = cleanName(field.text.toString())
                prefs.edit().putString("name", v).apply()
                updateNameRow(row)
            },
            secondary = "取消",
        )
        field.post { field.requestFocus(); field.setSelection(field.text.length); showKeyboard(field) }
    }

    // ───────────────────────── 创建房间 ─────────────────────────

    private fun showHost() {
        stopNetworking()
        val col = screenColumn("创建房间") { showHome() }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), 0, dp(28), dp(20))
        }
        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 0.8f))
        body.addView(PulseView(this), LinearLayout.LayoutParams(dp(200), dp(200)))
        body.addView(label("等待对手加入", 22f, face = Fonts.serifBold, spacing = 0.08f),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) })
        body.addView(label("请对方在同一网络下打开「加入房间」", 13f, Palette.MUTED).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) })

        val ipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Palette.SURFACE, dp(16).toFloat(), Palette.HAIRLINE, dp(1))
            setPadding(dp(20), dp(16), dp(20), dp(18))
        }
        ipCard.addView(label("本机地址", 11f, Palette.MUTED, Fonts.sansMedium, 0.2f))
        val ipText = label("", 22f, Palette.INK, Fonts.sansMedium, 0.04f).apply { gravity = Gravity.CENTER }
        ipCard.addView(ipText, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        body.addView(ipCard, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(28) })
        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        body.addView(pill("取消", PillStyle.OUTLINE) { showHome() }, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        go(col, back = { showHome() })

        val h = Host(myName, onClient = { socket -> onGuestConnected(socket) }, onError = { msg ->
            showDialog("创建失败", msg, "返回", onPrimary = { showHome() }, cancelable = false)
        })
        host = h
        h.start()
        val ips = localIpv4()
        ipText.text = if (ips.isEmpty()) "未连接网络" else ips.joinToString("\n") {
            if (h.port == TCP_PORT) it else "$it:${h.port}"
        }
        if (ips.isEmpty()) {
            ipText.setTextColor(Palette.ACCENT)
            ipText.textSize = 16f
        }
    }

    private fun onGuestConnected(socket: java.net.Socket) {
        host?.stop(); host = null
        val c = Connection(socket)
        pendingConn = c
        c.listener = object : Connection.Listener {
            override fun onMessage(parts: List<String>) {
                if (parts[0] != "HELLO") return
                if (parts.getOrNull(1) != PROTO) {
                    c.send("ERR", "版本不兼容"); c.close(); pendingConn = null
                    snack("对方的版本不兼容"); showHost(); return
                }
                pendingConn = null
                c.send("WELCOME", cleanName(myName))
                startOnline(c, isHost = true, oppName = cleanName(parts.getOrNull(2) ?: "对手"))
            }

            override fun onDisconnected() {
                pendingConn = null
                snack("对方连接中断"); showHost()
            }
        }
        c.start()
    }

    // ───────────────────────── 加入房间 ─────────────────────────

    private fun showJoin() {
        stopNetworking()
        val col = screenColumn("加入房间") { showHome() }
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(20))
        }
        scroll.addView(body, FrameLayout.LayoutParams(MATCH, WRAP))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("附近的房间", 13f, Palette.MUTED, Fonts.sansMedium, 0.12f), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(PulseView(this), LinearLayout.LayoutParams(dp(26), dp(26)))
        body.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        val empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg(Color.TRANSPARENT, dp(16).toFloat(), Palette.HAIRLINE, dp(1))
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        empty.addView(label("正在搜索…", 15f, Palette.INK_SOFT, Fonts.serif))
        empty.addView(label("房主点击「创建房间」后会出现在这里", 12f, Palette.MUTED).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })

        val manualLabel = label("手动输入地址", 13f, Palette.MUTED, Fonts.sansMedium, 0.12f)
        val field = textField("例如 192.168.1.8", prefs.getString("lastIp", "") ?: "")
        field.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field.keyListener = DigitsKeyListener.getInstance("0123456789.:")
        field.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        field.imeOptions = EditorInfo.IME_ACTION_GO
        lateinit var connectBtn: TextView
        val roomViews = ArrayList<View>()

        fun setBusy(busy: Boolean) {
            connectBtn.text = if (busy) "连接中…" else "连接"
            connectBtn.setEnabledLook(!busy)
            roomViews.forEach { it.setEnabledLook(!busy) }
        }

        fun join(ip: String, port: Int) {
            hideKeyboard()
            setBusy(true)
            val token = ++connectToken
            connectTo(ip, port) { socket, err ->
                if (token != connectToken) { runCatching { socket?.close() }; return@connectTo }
                if (socket == null) {
                    setBusy(false)
                    showDialog("连接失败", "${err ?: "无法连接"}\n请确认双方在同一网络，且对方已创建房间。", "知道了")
                    return@connectTo
                }
                onJoined(socket, ip) { setBusy(false) }
            }
        }

        connectBtn = pill("连接", PillStyle.PRIMARY) {
            val raw = field.text.toString().trim()
            val ip = raw.substringBefore(':')
            val port = raw.substringAfter(':', "").toIntOrNull() ?: TCP_PORT
            if (!ip.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
                snack("请输入正确的 IP 地址"); return@pill
            }
            prefs.edit().putString("lastIp", raw).apply()
            join(ip, port)
        }
        field.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { connectBtn.performClick(); true } else false
        }

        fun render(rooms: List<Room>) {
            list.removeAllViews()
            roomViews.clear()
            if (rooms.isEmpty()) {
                list.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))
                return
            }
            rooms.forEachIndexed { i, room ->
                val card = roomCard(room) { join(room.ip, room.port) }
                roomViews.add(card)
                list.addView(card, LinearLayout.LayoutParams(MATCH, WRAP).apply { if (i > 0) topMargin = dp(10) })
            }
        }
        render(emptyList())

        body.addView(manualLabel, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(32) })
        body.addView(field, LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(12) })
        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        body.addView(connectBtn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(20) })

        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        go(col, back = { showHome() })

        scanner = Scanner(this) { rooms -> render(rooms) }.also { it.start() }
    }

    private fun roomCard(room: Room, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x12000000),
                roundedBg(Palette.SURFACE, dp(16).toFloat(), Palette.HAIRLINE, dp(1)),
                roundedBg(Color.WHITE, dp(16).toFloat()),
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { onClick() }
        }
        card.addView(StoneIcon(this, BLACK), LinearLayout.LayoutParams(dp(30), dp(30)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(room.name, 16f, Palette.INK, Fonts.sansMedium).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        texts.addView(label(room.ip, 12f, Palette.MUTED), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(5) })
        card.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(14) })
        card.addView(label("加入", 14f, Palette.ACCENT, Fonts.sansMedium, 0.1f))
        return card
    }

    private fun onJoined(socket: java.net.Socket, ip: String, onFail: () -> Unit) {
        val c = Connection(socket)
        pendingConn = c
        val token = connectToken
        val timeout = Runnable {
            if (pendingConn === c) {
                c.close(); pendingConn = null; onFail()
                showDialog("连接失败", "对方没有响应，请确认对方已创建房间。", "知道了")
            }
        }
        root.postDelayed(timeout, 6000)
        c.listener = object : Connection.Listener {
            override fun onMessage(parts: List<String>) {
                if (token != connectToken) return
                when (parts[0]) {
                    "WELCOME" -> {
                        root.removeCallbacks(timeout)
                        pendingConn = null
                        scanner?.stop(); scanner = null
                        startOnline(c, isHost = false, oppName = cleanName(parts.getOrNull(1) ?: "房主"))
                    }
                    "ERR" -> {
                        root.removeCallbacks(timeout)
                        c.close(); pendingConn = null; onFail()
                        showDialog("无法加入", parts.getOrNull(1) ?: "对方拒绝了连接", "知道了")
                    }
                }
            }

            override fun onDisconnected() {
                root.removeCallbacks(timeout)
                if (pendingConn !== c) return
                pendingConn = null; onFail()
                showDialog("无法加入", "房间可能已经开始对局或已关闭。", "知道了")
            }
        }
        c.start()
        c.send("HELLO", PROTO, cleanName(myName))
        prefs.edit().putString("lastIp", ip).apply()
    }

    // ───────────────────────── 对局 ─────────────────────────

    private fun startOnline(c: Connection, isHost: Boolean, oppName: String) {
        val g = GameScreen(this, c, isHost, cleanName(myName), oppName)
        game = g
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        go(g.view, back = { g.requestLeave() })
        g.begin()
    }

    private fun startLocal() {
        stopNetworking()
        val g = GameScreen(this, null, true, "黑方", "白方")
        game = g
        go(g.view, back = { g.requestLeave() })
        g.begin()
    }

    // ───────────────────────── 通用组件 ─────────────────────────

    /** 顶部栏 + 内容列。 */
    fun screenColumn(title: String, right: View? = null, onBack: () -> Unit): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = FrameLayout(this)
        bar.addView(ChevronButton(this, onBack), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(6)
        })
        bar.addView(label(title, 17f, face = Fonts.serifBold, spacing = 0.12f),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        if (right != null) {
            bar.addView(right, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(20)
            })
        }
        col.addView(bar, LinearLayout.LayoutParams(MATCH, dp(56)))
        return col
    }

    private fun textField(hint: String, value: String) = EditText(this).apply {
        setText(value)
        this.hint = hint
        textSize = 16f
        setTextColor(Palette.INK)
        setHintTextColor(withAlpha(Palette.MUTED, 0.7f))
        typeface = Fonts.sans
        isSingleLine = true
        background = roundedBg(Palette.SURFACE, dp(14).toFloat(), Palette.HAIRLINE, dp(1))
        setPadding(dp(18), 0, dp(18), 0)
        minHeight = dp(52)
        if (Build.VERSION.SDK_INT >= 29) textCursorDrawable = roundedBg(Palette.ACCENT, 0f).apply { setSize(dp(2), 0) }
    }

    class DialogHandle(val cancelable: Boolean, val cancel: () -> Unit, val dismiss: () -> Unit)

    fun showDialog(
        title: String,
        message: String,
        primary: String,
        onPrimary: (() -> Unit)? = null,
        secondary: String? = null,
        onSecondary: (() -> Unit)? = null,
        cancelable: Boolean = true,
        extra: View? = null,
    ): DialogHandle {
        dismissDialog()
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0x66141210)
            alpha = 0f
            isClickable = true
            val (l, t, r, b) = sysInsets
            setPadding(l, t, r, b)
        }
        dialogScrim = scrim
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Palette.SURFACE, dp(22).toFloat())
            elevation = dp(16).toFloat()
            setPadding(dp(24), dp(26), dp(24), dp(18))
            isClickable = true
        }
        card.addView(label(title, 20f, face = Fonts.serifBold, spacing = 0.06f))
        if (message.isNotEmpty()) {
            card.addView(label(message, 14f, Palette.INK_SOFT).apply { setLineSpacing(dp(4).toFloat(), 1f) },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        }
        if (extra != null) card.addView(extra, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })

        lateinit var handle: DialogHandle
        val close = { after: (() -> Unit)? ->
            if (dialog === handle) { dialog = null; dialogScrim = null }
            hideKeyboard()
            scrim.isClickable = false
            scrim.animate().alpha(0f).setDuration(150).withEndAction { root.removeView(scrim) }.start()
            after?.invoke()
        }
        val buttons = LinearLayout(this).apply { gravity = Gravity.END }
        if (secondary != null) {
            buttons.addView(pill(secondary, PillStyle.GHOST) { close(onSecondary) }.apply { minHeight = dp(44) },
                LinearLayout.LayoutParams(WRAP, WRAP))
        }
        buttons.addView(pill(primary, PillStyle.PRIMARY) { close(onPrimary) }.apply { minHeight = dp(44) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) })
        card.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) })

        scrim.addView(card, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply {
            leftMargin = dp(28); rightMargin = dp(28)
        })
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        scrim.animate().alpha(1f).setDuration(180).start()
        card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()

        val onCancel = onSecondary
        handle = DialogHandle(cancelable, cancel = { close(onCancel) }, dismiss = { close(null) })
        if (cancelable) scrim.setOnClickListener { handle.cancel() }
        dialog = handle
        return handle
    }

    fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    fun snack(text: String) {
        snackView?.let { root.removeView(it) }
        val tv = label(text, 14f, Palette.PAPER, Fonts.sansMedium).apply {
            background = roundedBg(withAlpha(Palette.INK, 0.92f), dp(22).toFloat())
            setPadding(dp(20), dp(12), dp(20), dp(12))
            gravity = Gravity.CENTER
            elevation = dp(8).toFloat()
            alpha = 0f
            translationY = dp(12).toFloat()
        }
        snackView = tv
        root.addView(tv, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = content.paddingBottom + dp(96)
            leftMargin = dp(24); rightMargin = dp(24)
        })
        tv.animate().alpha(1f).translationY(0f).setDuration(200).start()
        tv.postDelayed({
            tv.animate().alpha(0f).setDuration(220).withEndAction {
                root.removeView(tv)
                if (snackView === tv) snackView = null
            }.start()
        }, 2200)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun showKeyboard(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }
}
