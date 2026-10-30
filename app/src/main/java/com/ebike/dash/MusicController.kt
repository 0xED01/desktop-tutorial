package com.ebike.dash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat

/** 正在播放的歌曲 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAt: Long,
    val speed: Float,
    val pkg: String,
) {
    /** 按播放速度推算出的当前进度 */
    fun livePosition(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

/**
 * 读取并控制系统里正在播放的音乐（优先 Apple Music）。
 * 有“通知使用权”时可拿到歌名、封面、进度并精确控制；
 * 没有时也能用系统媒体按键实现播放 / 暂停 / 切歌。
 */
class MusicController(private val ctx: Context, private val onChange: (NowPlaying?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(ctx, MusicListenerService::class.java)
    private var controller: MediaController? = null
    private var listening = false
    private var artFor: String? = null
    private var art: Bitmap? = null

    var now: NowPlaying? = null; private set

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun appleMusicInstalled(): Boolean = ctx.packageManager.getLaunchIntentForPackage(APPLE_MUSIC) != null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
        override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
        override fun onSessionDestroyed() {
            setController(null)
            refresh()
        }
    }

    fun start() {
        if (!hasAccess()) {
            setController(null)
            return
        }
        try {
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
                listening = true
            }
            refresh()
        } catch (_: SecurityException) {
            setController(null)
        }
    }

    fun stop() {
        if (listening) {
            try { msm.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: Exception) {}
            listening = false
        }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun refresh() {
        try { pick(msm.getActiveSessions(listener)) } catch (_: SecurityException) { setController(null) }
    }

    /** 优先 Apple Music，其次正在播放的，最后任意一个 */
    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.packageName == APPLE_MUSIC }
            ?: list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        setController(c)
    }

    private fun setController(c: MediaController?) {
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback, main)
        }
        emit()
    }

    private fun emit() {
        val c = controller
        val md = c?.metadata
        val st = c?.playbackState
        now = if (c == null || md == null) null else {
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            NowPlaying(
                title = title,
                artist = artist,
                art = artOf(md, "$title|$artist"),
                playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = st?.position ?: 0,
                updatedAt = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
                speed = st?.playbackSpeed?.takeIf { it > 0 } ?: 1f,
                pkg = c.packageName,
            )
        }
        onChange(now)
    }

    /** 封面：优先直接给的图片，其次本地 content:// 地址；同一首歌只解码一次 */
    private fun artOf(md: MediaMetadata, key: String): Bitmap? {
        if (key == artFor && art != null) return art
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: listOf(
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            ).firstNotNullOfOrNull { k -> md.getString(k)?.let { loadUri(it) } }
        artFor = key
        art = bmp
        return bmp
    }

    private fun loadUri(s: String): Bitmap? {
        val uri = Uri.parse(s)
        if (uri.scheme != "content" && uri.scheme != "file" && uri.scheme != "android.resource") return null
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- 控制

    fun playPause() {
        val c = controller
        when {
            c == null -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            now?.playing == true -> c.transportControls.pause()
            else -> c.transportControls.play()
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    private fun mediaKey(code: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** 打开正在播放的 App（默认 Apple Music），返回是否成功 */
    fun openPlayerApp(): Boolean {
        try {
            controller?.sessionActivity?.let { it.send(); return true }
        } catch (_: Exception) {
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(controller?.packageName ?: APPLE_MUSIC)
            ?: ctx.packageManager.getLaunchIntentForPackage(APPLE_MUSIC)
            ?: return false
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        const val APPLE_MUSIC = "com.apple.android.music"
    }
}
