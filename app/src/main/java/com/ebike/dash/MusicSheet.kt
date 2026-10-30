package com.ebike.dash

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.ebike.dash.databinding.SheetMusicBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import java.util.Locale

/** 全屏播放器：大封面、歌名、可拖动的进度条和播放控制 */
class MusicSheet(
    private val act: AppCompatActivity,
    private val music: MusicController,
    private val onRequestAccess: () -> Unit,
) {
    private val b = SheetMusicBinding.inflate(LayoutInflater.from(act))
    private val dlg = BottomSheetDialog(act)
    private val ui = Handler(Looper.getMainLooper())
    private var dragging = false
    private var first = true

    val isShowing get() = dlg.isShowing

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            ui.postDelayed(this, 500)
        }
    }

    fun show() {
        dlg.setContentView(b.root)
        dlg.setOnShowListener {
            dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dlg.behavior.skipCollapsed = true
        }
        dlg.setOnDismissListener { ui.removeCallbacks(ticker) }

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

        update(music.now)
        dlg.show()
        ui.post(ticker)
    }

    fun update(np: NowPlaying?) {
        Motion.setText(b.tvTitle, np?.title?.ifBlank { null } ?: if (np == null) "未在播放" else "未知歌曲")
        Motion.setText(b.tvArtist, np?.artist?.ifBlank { null } ?: "Apple Music")
        setArt(np)
        val playing = np?.playing == true
        Motion.swapIcon(b.btnPlay, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        // 封面随播放“呼吸”：播放时舒展，暂停时微微收起
        val artScale = if (playing) 1f else 0.88f
        if (first) {
            b.ivArt.scaleX = artScale; b.ivArt.scaleY = artScale
        } else if (b.ivArt.scaleX != artScale) {
            b.ivArt.animate().scaleX(artScale).scaleY(artScale)
                .setDuration(Motion.STANDARD).setInterpolator(Motion.EASE).start()
        }
        first = false

        val hint = when {
            !music.hasAccess() -> "点这里开启「通知使用权」，即可显示歌名、封面和进度"
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

    private fun setArt(np: NowPlaying?) {
        val art = np?.art
        if (art != null) {
            b.ivArt.setPadding(0, 0, 0, 0)
            b.ivArt.background = null
            b.ivArt.imageTintList = null
            b.ivArt.setImageBitmap(art)
        } else {
            // 没有封面时：浅灰底 + 音符
            val pad = (act.resources.displayMetrics.density * 96).toInt()
            b.ivArt.setPadding(pad, pad, pad, pad)
            b.ivArt.setBackgroundResource(R.drawable.bg_art_large)
            b.ivArt.setImageResource(R.drawable.ic_music)
            b.ivArt.imageTintList = act.getColorStateList(R.color.fg_dim)
        }
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
