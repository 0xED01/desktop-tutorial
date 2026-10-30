package com.ebike.dash

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.ebike.dash.databinding.SheetMusicBinding
import com.google.android.material.slider.Slider
import java.util.Locale

/** 悬浮播放器：跳动音乐柱、歌名、可拖动的进度条和播放控制 */
class MusicSheet(
    private val act: AppCompatActivity,
    private val music: MusicController,
    private val onRequestAccess: () -> Unit,
) {
    private val ui = Handler(Looper.getMainLooper())
    private var dragging = false
    private var first = true
    private lateinit var b: SheetMusicBinding

    /** 内容按当前配色生成；切换浅色 / 深色时会重新生成并重新绑定 */
    private val panel = FloatingPanel(act) { inf, _ ->
        b = SheetMusicBinding.inflate(inf)
        bind()
        first = true
        update(music.now)
        b.root
    }

    val isShowing get() = panel.isShowing

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            ui.postDelayed(this, 500)
        }
    }

    fun show() {
        panel.onDismiss = { ui.removeCallbacks(ticker) }
        panel.show()
        ui.post(ticker)
    }

    private fun bind() {
        Motion.pressable(b.btnPlay, b.btnPrev, b.btnNext, b.btnOpenApp)
        b.btnPlay.setOnClickListener { music.playPause() }
        b.btnPrev.setOnClickListener { music.previous() }
        b.btnNext.setOnClickListener { music.next() }
        b.btnOpenApp.setOnClickListener {
            if (!music.openPlayerApp()) Toast.makeText(act, "没有找到 Apple Music", Toast.LENGTH_SHORT).show()
        }
        b.tvHint.setOnClickListener {
            if (!music.hasAccess()) onRequestAccess()
        }
        b.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) { dragging = true }
            override fun onStopTrackingTouch(slider: Slider) {
                music.seekTo((slider.value * 1000).toLong())
                dragging = false
            }
        })
        b.slider.addOnChangeListener { _, v, fromUser -> if (fromUser) b.tvPos.text = fmt((v * 1000).toLong()) }
    }

    fun update(np: NowPlaying?) {
        Motion.setText(b.tvTitle, np?.title?.ifBlank { null } ?: if (np == null) "未在播放" else "未知歌曲")
        Motion.setText(b.tvArtist, np?.artist?.ifBlank { null } ?: "Apple Music")
        val playing = np?.playing == true
        Motion.swapIcon(b.btnPlay, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        // 音乐柱：播放时跳动，暂停时落回成圆点；刚打开时直接显示当前状态
        b.eq.setColor(b.root.context.getColor(R.color.fg))
        b.eq.setPlaying(playing, animate = !first)
        first = false

        val hint = when {
            !music.hasAccess() -> "点这里开启「通知使用权」，即可显示歌名和进度"
            np == null -> "还没有正在播放的音乐，点 ▶ 继续播放，或打开 Apple Music"
            else -> null
        }
        b.tvHint.isVisible = hint != null
        b.tvHint.text = hint

        val hasTimeline = np != null && np.durationMs > 0
        b.slider.isEnabled = hasTimeline
        b.timeRow.alpha = if (hasTimeline) 1f else 0f
        updateProgress()
    }

    private fun updateProgress() {
        val np = music.now ?: run {
            b.tvPos.text = fmt(0); b.tvDur.text = fmt(0); return
        }
        if (np.durationMs <= 0 || dragging) return
        val dur = np.durationMs / 1000f
        if (b.slider.valueTo != dur) {
            b.slider.valueFrom = 0f
            b.slider.valueTo = dur.coerceAtLeast(1f)
        }
        val pos = np.livePosition() / 1000f
        b.slider.value = pos.coerceIn(0f, b.slider.valueTo)
        b.tvPos.text = fmt(np.livePosition())
        b.tvDur.text = "-" + fmt(np.durationMs - np.livePosition())
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
