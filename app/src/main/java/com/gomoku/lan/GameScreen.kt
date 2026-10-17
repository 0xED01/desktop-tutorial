package com.gomoku.lan

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** 对局界面。conn 为空时是本机双人模式。 */
class GameScreen(
    private val act: MainActivity,
    private val conn: Connection?,
    private val isHost: Boolean,
    private val myName: String,
    private val oppName: String,
) : Connection.Listener {

    private val online = conn != null
    private val game = Game()
    private var myColor = BLACK
    private var hostBlack = true
    private var started = !online
    private var disconnected = false
    private var waitingUndo = false
    private var waitingAgain = false
    private var resultShown = false
    private var myWins = 0
    private var oppWins = 0
    private var pending = false

    val view: View
    private val board: BoardView
    private val blackChip: PlayerChip
    private val whiteChip: PlayerChip
    private val status: TextView
    private val moveCount: TextView
    private val undoBtn: TextView
    private val resignBtn: TextView
    private val againBtn: TextView
    private val linkDot: TextView?

    init {
        linkDot = if (online) act.label("● 已连接", 12f, Palette.ONLINE, Fonts.sansMedium) else null
        val col = act.screenColumn(if (online) "联机对局" else "本机对弈", linkDot) { requestLeave() }

        val chips = LinearLayout(act).apply { setPadding(act.dp(16), act.dp(4), act.dp(16), 0) }
        blackChip = PlayerChip(act, BLACK)
        whiteChip = PlayerChip(act, WHITE)
        chips.addView(blackChip, LinearLayout.LayoutParams(0, WRAP, 1f))
        chips.addView(whiteChip, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = act.dp(10) })
        col.addView(chips, LinearLayout.LayoutParams(MATCH, WRAP))

        board = BoardView(act, game)
        board.onPlace = { r, c -> placeMine(r, c) }
        board.onPendingChanged = { p -> pending = p; updateStatus() }
        val boardBox = FrameLayout(act).apply { setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(4)) }
        boardBox.addView(board, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        col.addView(boardBox, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val statusRow = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dp(24), act.dp(8), act.dp(24), act.dp(14))
        }
        status = act.label("", 16f, Palette.INK, Fonts.serifBold, 0.04f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        moveCount = act.label("", 12f, Palette.MUTED, Fonts.sansMedium, 0.06f)
        statusRow.addView(status, LinearLayout.LayoutParams(0, WRAP, 1f))
        statusRow.addView(moveCount)
        col.addView(statusRow, LinearLayout.LayoutParams(MATCH, WRAP))

        val actions = LinearLayout(act).apply { setPadding(act.dp(20), 0, act.dp(20), act.dp(18)) }
        undoBtn = act.pill("悔棋", PillStyle.OUTLINE) { requestUndo() }
        resignBtn = act.pill("认输", PillStyle.OUTLINE) { requestResign() }
        againBtn = act.pill("再来一局", PillStyle.PRIMARY) { requestAgain() }
        actions.addView(undoBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        actions.addView(resignBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = act.dp(12) })
        actions.addView(againBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(actions, LinearLayout.LayoutParams(MATCH, WRAP))
        view = col
    }

    fun begin() {
        conn?.listener = this
        if (!online || isHost) startNewGame(hostIsBlack = true)
        else update()
    }

    fun dispose() {
        conn?.close()
    }

    // ───────────── 本方操作 ─────────────

    private fun placeMine(r: Int, c: Int) {
        if (!canIPlay() || !game.play(r, c)) return
        conn?.send("MOVE", r, c)
        afterMove()
    }

    private fun requestUndo() {
        if (!online) {
            if (game.undoFor(other(game.turn)) > 0) { board.refresh(); update() }
            return
        }
        if (waitingUndo || !undoAllowed()) return
        waitingUndo = true
        board.clearPending()
        conn?.send("UNDO?")
        act.snack("已发送悔棋请求")
        update()
    }

    private fun requestResign() {
        if (game.over || !started || disconnected) return
        val who = if (online) myColor else game.turn
        act.showDialog(
            title = "确定认输？",
            message = if (online) "本局将判对手获胜" else "${colorName(who)}认输，本局将判${colorName(other(who))}获胜",
            primary = "认输",
            onPrimary = {
                if (game.over) return@showDialog
                game.resign(who)
                conn?.send("RESIGN")
                afterResult()
            },
            secondary = "再想想",
        )
    }

    private fun requestAgain() {
        if (!game.over) return
        if (!online) { startNewGame(true); return }
        if (disconnected || waitingAgain) return
        waitingAgain = true
        conn?.send("AGAIN?")
        act.snack("已邀请对手再来一局")
        update()
    }

    fun requestLeave() {
        val inProgress = if (online) !disconnected else game.moves.isNotEmpty() && !game.over
        if (!inProgress) { act.showHome(); return }
        act.showDialog(
            title = "离开对局？",
            message = if (online) "离开后将断开与对手的连接" else "当前棋局不会被保存",
            primary = "离开",
            onPrimary = { act.showHome() },
            secondary = "继续下棋",
        )
    }

    // ───────────── 网络消息 ─────────────

    override fun onMessage(parts: List<String>) {
        val opp = other(myColor)
        when (parts[0]) {
            "NEW" -> if (!isHost) {
                act.dismissDialog()
                startNewGame(parts.getOrNull(1) == "1")
                act.snack(if (myColor == BLACK) "新的一局 · 你执黑先行" else "新的一局 · 你执白后手")
            }
            "MOVE" -> {
                val r = parts.getOrNull(1)?.toIntOrNull() ?: return
                val c = parts.getOrNull(2)?.toIntOrNull() ?: return
                if (game.turn == opp && game.play(r, c)) afterMove()
            }
            "UNDO?" -> {
                if (waitingUndo || game.over || !game.hasMoveOf(opp)) { conn?.send("UNDO_N"); return }
                act.showDialog(
                    title = "对手请求悔棋",
                    message = "同意后，对手的上一手将被撤回",
                    primary = "同意",
                    onPrimary = {
                        if (!game.over && game.hasMoveOf(opp) && !disconnected) {
                            game.undoFor(opp)
                            conn?.send("UNDO_Y")
                            board.refresh(); update()
                        } else {
                            conn?.send("UNDO_N")
                        }
                    },
                    secondary = "拒绝",
                    onSecondary = { conn?.send("UNDO_N") },
                )
            }
            "UNDO_Y" -> if (waitingUndo) {
                waitingUndo = false
                game.undoFor(myColor)
                board.refresh()
                act.snack("对手同意了悔棋")
                update()
            }
            "UNDO_N" -> if (waitingUndo) {
                waitingUndo = false
                act.snack("对手拒绝了悔棋")
                update()
            }
            "RESIGN" -> if (!game.over) {
                game.resign(opp)
                afterResult()
            }
            "AGAIN?" -> when {
                !game.over -> conn?.send("AGAIN_N")
                waitingAgain -> if (isHost) startNextOnline() else conn?.send("AGAIN_Y")
                else -> act.showDialog(
                    title = "再来一局？",
                    message = "$oppName 邀请你再下一局，双方将交换黑白",
                    primary = "好的",
                    onPrimary = {
                        if (!game.over || disconnected) return@showDialog
                        if (isHost) startNextOnline() else conn?.send("AGAIN_Y")
                    },
                    secondary = "不了",
                    onSecondary = { conn?.send("AGAIN_N") },
                )
            }
            "AGAIN_Y" -> if (waitingAgain && isHost && game.over) startNextOnline()
            "AGAIN_N" -> if (waitingAgain) {
                waitingAgain = false
                act.snack("对手暂时不想再来一局")
                update()
            }
            "BYE" -> lost("对手已离开对局")
        }
    }

    override fun onDisconnected() = lost("与对手的连接已断开")

    private fun lost(msg: String) {
        if (disconnected) return
        disconnected = true
        waitingUndo = false
        waitingAgain = false
        conn?.close(sendBye = false)
        linkDot?.apply { text = "● 已断开"; setTextColor(Palette.MUTED) }
        update()
        act.showDialog(
            title = "连接已断开",
            message = msg,
            primary = "返回首页",
            onPrimary = { act.showHome() },
            secondary = "查看棋盘",
        )
    }

    // ───────────── 对局流程 ─────────────

    private fun startNextOnline() {
        hostBlack = !hostBlack
        startNewGame(hostBlack)
        act.dismissDialog()
        act.snack(if (myColor == BLACK) "新的一局 · 你执黑先行" else "新的一局 · 你执白后手")
    }

    private fun startNewGame(hostIsBlack: Boolean) {
        game.reset()
        hostBlack = hostIsBlack
        myColor = if (isHost == hostIsBlack) BLACK else WHITE
        started = true
        waitingUndo = false
        waitingAgain = false
        resultShown = false
        if (online && isHost) conn?.send("NEW", if (hostIsBlack) 1 else 0)
        board.refresh()
        update()
    }

    private fun afterMove() {
        board.animateLastMove()
        if (game.over) afterResult() else update()
    }

    private fun afterResult() {
        if (resultShown) return
        resultShown = true
        board.clearPending()
        if (game.winLine == null) board.refresh()
        val winner = game.winner
        if (online) {
            if (winner == myColor) myWins++ else if (winner != EMPTY) oppWins++
        } else {
            if (winner == BLACK) myWins++ else if (winner == WHITE) oppWins++
        }
        update()
        val (title, msg) = resultText()
        board.postDelayed({
            if (!game.over || disconnected) return@postDelayed
            act.showDialog(title, msg, "再来一局", onPrimary = { requestAgain() }, secondary = "查看棋盘")
        }, if (game.winLine != null) 1100 else 300)
    }

    private fun resultText(): Pair<String, String> {
        val w = game.winner
        if (w == EMPTY) return "和棋" to "棋盘已满，双方平分秋色"
        if (!online) {
            return "${colorName(w)}获胜" to
                if (game.resigned) "${colorName(other(w))}认输" else "五子连珠，共 ${game.moves.size} 手"
        }
        return if (w == myColor) {
            "胜利" to if (game.resigned) "$oppName 认输了" else "五子连珠，共 ${game.moves.size} 手"
        } else {
            "惜败" to if (game.resigned) "你认输了，下一局再来" else "$oppName 率先连成五子"
        }
    }

    // ───────────── 界面状态 ─────────────

    private fun canIPlay() = started && !game.over && !disconnected && !waitingUndo &&
        (!online || game.turn == myColor)

    private fun undoAllowed() = started && !game.over && !disconnected &&
        if (online) game.hasMoveOf(myColor) else game.moves.isNotEmpty()

    private fun update() {
        board.interactive = canIPlay()
        if (online) {
            val me = myName to "你"
            val op = oppName to "对手"
            val (bName, bRole) = if (myColor == BLACK) me else op
            val (wName, wRole) = if (myColor == WHITE) me else op
            blackChip.bind(bName, "$bRole · 执黑", if (myColor == BLACK) myWins else oppWins)
            whiteChip.bind(wName, "$wRole · 执白", if (myColor == WHITE) myWins else oppWins)
        } else {
            blackChip.bind("黑方", "先手", myWins)
            whiteChip.bind("白方", "后手", oppWins)
        }
        val active = when {
            !started -> EMPTY
            game.over -> game.winner
            else -> game.turn
        }
        blackChip.setActive(active == BLACK || active == EMPTY && game.over)
        whiteChip.setActive(active == WHITE || active == EMPTY && game.over)

        val over = game.over
        undoBtn.visibility = if (over) View.GONE else View.VISIBLE
        resignBtn.visibility = if (over) View.GONE else View.VISIBLE
        againBtn.visibility = if (over) View.VISIBLE else View.GONE
        undoBtn.setEnabledLook(undoAllowed() && !waitingUndo)
        resignBtn.setEnabledLook(started && !over && !disconnected)
        againBtn.setEnabledLook(!online || (!disconnected && !waitingAgain))
        againBtn.text = if (waitingAgain) "等待对手…" else "再来一局"
        moveCount.text = if (game.moves.isEmpty()) "" else "第 ${game.moves.size} 手"
        updateStatus()
    }

    private fun updateStatus() {
        val (text, color) = when {
            disconnected -> "已断开连接" to Palette.MUTED
            !started -> "等待房主开局…" to Palette.MUTED
            game.over -> {
                val (t, _) = resultText()
                t to Palette.ACCENT
            }
            waitingUndo -> "等待对手回应悔棋…" to Palette.MUTED
            pending -> "再次点击确认落子" to Palette.ACCENT
            !online -> "${colorName(game.turn)}落子" to Palette.INK
            game.turn == myColor -> "轮到你落子" to Palette.INK
            else -> "等待对手落子…" to Palette.MUTED
        }
        status.text = text
        status.setTextColor(color)
    }

    private fun colorName(c: Int) = if (c == BLACK) "黑方" else "白方"
}

/** 顶部玩家信息卡，当前行棋方高亮。 */
class PlayerChip(ctx: android.content.Context, color: Int) : LinearLayout(ctx) {
    private val name: TextView
    private val role: TextView
    private val score: TextView
    private val bg = GradientDrawable().apply {
        cornerRadius = ctx.dp(16).toFloat()
        setColor(Palette.SURFACE)
        setStroke(ctx.dp(1), Palette.HAIRLINE)
    }
    private var active: Boolean? = null
    private var anim: ValueAnimator? = null

    init {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(14), dp(10))
        background = bg
        bg.alpha = 0
        addView(StoneIcon(ctx, color), LayoutParams(dp(28), dp(28)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        name = ctx.label("", 15f, Palette.INK, Fonts.sansMedium).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        role = ctx.label("", 11f, Palette.MUTED, Fonts.sans, 0.04f).apply { maxLines = 1 }
        texts.addView(name)
        texts.addView(role, LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        addView(texts, LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) })
        score = ctx.label("0", 20f, Palette.INK, Fonts.serifBold)
        addView(score, LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
    }

    fun bind(n: String, r: String, s: Int) {
        name.text = n
        role.text = r
        score.text = s.toString()
    }

    fun setActive(a: Boolean) {
        if (active == a) return
        active = a
        anim?.cancel()
        anim = ValueAnimator.ofInt(bg.alpha, if (a) 255 else 0).apply {
            duration = 220
            addUpdateListener { bg.alpha = it.animatedValue as Int; invalidate() }
            start()
        }
        animate().alpha(if (a) 1f else 0.5f).setDuration(220).start()
        elevation = 0f
    }
}
