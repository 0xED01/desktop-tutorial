package com.applestock.monitor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/** 保存的设置。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    var parts by Str("parts", "")
    var location by Str("location", "上海 上海 黄浦区")
    var storeFilter by Str("store_filter", "")
    var interval by IntPref("interval", 30)
    var repeatMinutes by IntPref("repeat_minutes", 5)
    var maxVolume by BoolPref("max_volume", true)
    var serverchanKey by Str("serverchan_key", "")
    var pushplusToken by Str("pushplus_token", "")
    var barkUrl by Str("bark_url", "")
    /** 监控是否应在运行（被系统杀掉后自动恢复用）。 */
    var running by BoolPref("running", false)

    private inner class Str(val key: String, val def: String) {
        operator fun getValue(t: Any, p: Any): String = sp.getString(key, def) ?: def
        operator fun setValue(t: Any, p: Any, v: String) = sp.edit().putString(key, v).apply()
    }

    private inner class IntPref(val key: String, val def: Int) {
        operator fun getValue(t: Any, p: Any): Int = sp.getInt(key, def)
        operator fun setValue(t: Any, p: Any, v: Int) = sp.edit().putInt(key, v).apply()
    }

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(t: Any, p: Any): Boolean = sp.getBoolean(key, def)
        operator fun setValue(t: Any, p: Any, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    }
}

/** 服务和界面共享的运行状态（同一进程内）。 */
object State {
    private val main = Handler(Looper.getMainLooper())
    private val logs = ArrayDeque<String>()
    private val rows = LinkedHashMap<String, PartRow>()

    @Volatile var status = "未开始"
    @Volatile var running = false
    var listener: (() -> Unit)? = null

    fun log(text: String) {
        synchronized(logs) {
            logs.addLast("${timeNow("MM-dd HH:mm:ss")}  $text")
            while (logs.size > 300) logs.removeFirst()
        }
        changed()
    }

    fun logs(): List<String> = synchronized(logs) { logs.toList() }

    fun putRow(row: PartRow) {
        synchronized(rows) { rows[row.part] = row }
        changed()
    }

    fun clearRows() = synchronized(rows) { rows.clear() }

    /** 有货的排在前面。 */
    fun rows(): List<PartRow> = synchronized(rows) { rows.values.sortedBy { it.inStock.isEmpty() } }

    fun changed() {
        main.post { listener?.invoke() }
    }
}

/** 闹钟铃声 + 振动，静音模式下也会响（走闹钟音量），最长 10 分钟。 */
object AlarmPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    @Volatile private var playing = false

    val isPlaying get() = playing

    fun start(context: Context, maxVolume: Boolean) = main.post {
        if (playing) return@post
        playing = true
        val ctx = context.applicationContext
        if (maxVolume) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (_: SecurityException) {
            }
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
            State.log("播放铃声失败：$e")
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
        main.postDelayed({ stop() }, 10 * 60_000L)
    }

    fun stop() = main.post {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        playing = false
        vibrator?.cancel()
        vibrator = null
        main.removeCallbacksAndMessages(null)
    }
}

/** 推送到其他设备：Server酱 / PushPlus（微信）、Bark（iPhone）。 */
object Push {
    fun send(prefs: Prefs, title: String, body: String, url: String) = thread {
        val sent = ArrayList<String>()
        val key = prefs.serverchanKey.trim()
        if (key.isNotEmpty() && post("https://sctapi.ftqq.com/$key.send",
                "title=${enc(title.take(32))}&desp=${enc(body)}", "application/x-www-form-urlencoded")) {
            sent.add("Server酱")
        }
        val token = prefs.pushplusToken.trim()
        if (token.isNotEmpty()) {
            val json = org.json.JSONObject()
                .put("token", token).put("title", title).put("content", body).put("template", "txt")
            if (post("https://www.pushplus.plus/send", json.toString(), "application/json")) sent.add("PushPlus")
        }
        val bark = prefs.barkUrl.trim().trimEnd('/')
        if (bark.isNotEmpty() && get("$bark/${enc(title)}/${enc(body)}?url=${enc(url)}" +
                "&level=critical&volume=10&call=1&group=${enc("苹果库存")}")) {
            sent.add("Bark")
        }
        if (sent.isNotEmpty()) State.log("已推送：" + sent.joinToString("、"))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(url: String) = request(url, null, null)
    private fun post(url: String, data: String, type: String) = request(url, data, type)

    private fun request(url: String, data: String?, type: String?): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        if (data != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "$type; charset=utf-8")
            c.outputStream.use { it.write(data.toByteArray()) }
        }
        val code = c.responseCode
        c.disconnect()
        if (code !in 200..299) State.log("推送失败：${URL(url).host} HTTP $code")
        code in 200..299
    } catch (e: Exception) {
        State.log("推送失败：${URL(url).host} ${e.javaClass.simpleName}")
        false
    }
}
