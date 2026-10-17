package com.islandpet.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.islandpet.MainActivity
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.PetRepo
import com.islandpet.model.clean
import com.islandpet.model.heal
import com.islandpet.model.pat
import com.islandpet.model.play
import com.islandpet.model.quickFeed
import com.islandpet.model.toggleSleep
import kotlin.math.roundToInt

/** 设置项：灵动岛位置 / 大小 */
object IslandPrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("enabled", v).apply()

    /** 垂直偏移（dp），正数向下 */
    fun offsetY(ctx: Context) = prefs(ctx).getInt("offsetY", 0)
    fun setOffsetY(ctx: Context, v: Int) = prefs(ctx).edit().putInt("offsetY", v).apply()

    /** 收起时胶囊宽度（dp） */
    fun width(ctx: Context) = prefs(ctx).getInt("width", 150)
    fun setWidth(ctx: Context, v: Int) = prefs(ctx).edit().putInt("width", v).apply()

    /** 收起时胶囊高度（dp），0 = 按摄像头自动 */
    fun height(ctx: Context) = prefs(ctx).getInt("height", 0)
    fun setHeight(ctx: Context, v: Int) = prefs(ctx).edit().putInt("height", v).apply()
}

/** 执行灵动岛 / 通知上的按钮动作 */
fun performIslandAction(ctx: Context, action: IslandAction) {
    when (action) {
        IslandAction.FEED -> PetRepo.act { it.quickFeed() }
        IslandAction.PLAY -> PetRepo.act { it.play() }
        IslandAction.PAT -> PetRepo.act { it.pat() }
        IslandAction.CLEAN -> PetRepo.act { it.clean() }
        IslandAction.SLEEP -> PetRepo.act { it.toggleSleep() }
        IslandAction.HEAL -> PetRepo.act { it.heal() }
        IslandAction.OPEN_APP -> ctx.startActivity(
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}

/**
 * 把 IslandView 作为悬浮窗挂到屏幕顶部摄像头的位置。
 * windowType 为 TYPE_APPLICATION_OVERLAY（普通模式）或 TYPE_ACCESSIBILITY_OVERLAY（增强模式，可覆盖状态栏）。
 */
class IslandController(private val ctx: Context, val windowType: Int) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val d = ctx.resources.displayMetrics.density
    private var view: IslandView? = null
    private val params = WindowManager.LayoutParams()
    private var lastMood: Mood? = null
    private var lastMessageAt: Long? = null
    private var large = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            view?.setAnimating(i.action != Intent.ACTION_SCREEN_OFF)
        }
    }

    val isShowing get() = view != null

    fun show() {
        if (view != null) return
        val g = geometry()
        val v = IslandView(ctx, g.w, g.h, g.screenW, onAction = { performIslandAction(ctx, it) }, onWindowMode = { setLarge(it) })
        params.apply {
            type = windowType
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            x = g.x
            y = g.y
            width = (g.w + 2 * IslandView.PAD * d).roundToInt()
            height = (g.h + 2 * IslandView.PAD * d).roundToInt()
            title = "IslandPet"
        }
        large = false
        view = v
        runCatching { wm.addView(v, params) }.onFailure { view = null; return }
        v.update(PetRepo.state.value)
        ContextCompat.registerReceiver(ctx, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_EXPORTED)
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm.removeView(v) }
        runCatching { ctx.unregisterReceiver(screenReceiver) }
        view = null
    }

    /** 设置里调整了位置 / 大小 */
    fun relayout() {
        val v = view ?: return
        val g = geometry()
        v.relayout(g.w, g.h, g.screenW)
        params.x = g.x
        params.y = g.y
        applySize()
    }

    fun update(s: GameState) {
        val v = view ?: return
        v.update(s)
        val pet = s.pet ?: return
        // 互动反馈 / 宠物突然变饿、生病时，胶囊弹开提醒
        val mood = pet.mood()
        if (s.lastMessageAt != null && s.lastMessageAt != lastMessageAt && lastMessageAt != null) {
            s.lastMessage?.let { v.flash(it) }
        } else if (mood.urgent && mood != lastMood && lastMood != null) {
            v.flash("${mood.emoji} ${pet.name}${mood.title}了")
        }
        lastMessageAt = s.lastMessageAt
        lastMood = mood
    }

    private fun setLarge(on: Boolean) {
        large = on
        applySize()
    }

    private fun applySize() {
        val v = view ?: return
        val g = geometry()
        if (large) {
            params.width = g.screenW.roundToInt()
            params.height = ((IslandView.EXPANDED_H + 2 * IslandView.PAD) * d).roundToInt()
        } else {
            params.width = (g.w + 2 * IslandView.PAD * d).roundToInt()
            params.height = (g.h + 2 * IslandView.PAD * d).roundToInt()
        }
        runCatching { wm.updateViewLayout(v, params) }
    }

    private data class Geo(val x: Int, val y: Int, val w: Float, val h: Float, val screenW: Float)

    /** 根据前置摄像头挖孔的位置计算胶囊位置 */
    private fun geometry(): Geo {
        val bounds = wm.currentWindowMetrics.bounds
        val screenW = bounds.width().toFloat()
        val cutout = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
            ?.firstOrNull { it.top < 200 * d && it.centerX() in (bounds.width() / 4)..(bounds.width() * 3 / 4) }
        val userH = IslandPrefs.height(ctx) * d
        val h = when {
            userH > 0 -> userH
            cutout != null -> (cutout.height() + 10 * d).coerceIn(30 * d, 44 * d)
            else -> 34 * d
        }
        val w = IslandPrefs.width(ctx) * d
        val centerY = cutout?.exactCenterY() ?: (12 * d + h / 2)
        val y = (centerY - h / 2 - IslandView.PAD * d + IslandPrefs.offsetY(ctx) * d).roundToInt().coerceAtLeast(0)
        val x = cutout?.let { (it.exactCenterX() - screenW / 2).roundToInt() } ?: 0
        return Geo(x, y, w, h, screenW)
    }
}
