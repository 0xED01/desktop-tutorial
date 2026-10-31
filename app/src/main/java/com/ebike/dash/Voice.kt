package com.ebike.dash

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice as TtsVoice
import java.util.Locale

/**
 * 语音播报。优先使用台湾口音的中文语音（声音更柔和），找不到时退回普通话。
 * 可在菜单里从手机已安装的中文语音中挑选并试听。
 */
class Voice(ctx: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    var enabled = true

    /** 语音准备好、或换了声音时回调（用于更新菜单文字、播报用词） */
    var onChanged: (() -> Unit)? = null

    /** 当前是否台湾口音（播报时用“公尺”等台湾说法） */
    var taiwanese = false; private set

    class Option(val name: String, val label: String, val taiwan: Boolean)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        apply(prefs.voiceName)
    }

    /** 手机上可用的中文语音：台湾在前，其次大陆、香港 */
    fun options(): List<Option> {
        if (!ready) return emptyList()
        val list = try { tts.voices?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        val zh = list.filter { v ->
            val lang = v.locale.language
            (lang == "zh" || lang == "cmn" || lang == "yue") &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        fun region(v: TtsVoice) = when (v.locale.country) {
            "TW" -> 0
            "CN", "" -> 1
            else -> 2
        }
        val sorted = zh.sortedWith(compareBy({ region(it) }, { it.isNetworkConnectionRequired }, { it.name }))
        val counter = IntArray(3)
        return sorted.map { v ->
            val r = region(v)
            val area = when (r) { 0 -> "台湾"; 1 -> "大陆"; else -> v.locale.displayCountry.ifBlank { "其它" } }
            val n = ++counter[r]
            val net = if (v.isNetworkConnectionRequired) "（需联网）" else ""
            Option(v.name, "$area · 声音 $n$net", r == 0)
        }
    }

    /** 当前声音的名称（给菜单显示） */
    fun currentLabel(): String {
        if (!ready) return "加载中"
        val name = try { tts.voice?.name } catch (_: Exception) { null }
        return options().firstOrNull { it.name == name }?.label ?: if (taiwanese) "台湾" else "普通话"
    }

    /** 使用指定声音；name 为空时自动挑选：优先离线的台湾语音 */
    fun apply(name: String?) {
        if (!ready) return
        val voices = try { tts.voices?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        val chosen = voices.firstOrNull { it.name == name }
            ?: voices.filter { it.locale.country == "TW" && (it.locale.language == "zh" || it.locale.language == "cmn") }
                .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                .sortedBy { it.isNetworkConnectionRequired }
                .firstOrNull()
        if (chosen != null) {
            tts.voice = chosen
            taiwanese = chosen.locale.country == "TW"
        } else {
            // 引擎不提供声音列表时，按语言设置：先台湾，再大陆
            val tw = tts.setLanguage(Locale.TAIWAN)
            taiwanese = tw >= TextToSpeech.LANG_AVAILABLE && tw != TextToSpeech.LANG_MISSING_DATA
            if (!taiwanese) {
                val cn = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (cn == TextToSpeech.LANG_MISSING_DATA || cn == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault())
            }
        }
        // 略高的音调、略慢的语速，听起来更柔和
        tts.setPitch(if (taiwanese) 1.12f else 1.05f)
        tts.setSpeechRate(0.95f)
        onChanged?.invoke()
    }

    /** 选好声音后试听一句 */
    fun preview() {
        if (!ready) return
        val text = if (taiwanese) "前方三百公尺右转，进入中山北路，骑车注意安全喔" else "前方三百米右转，进入中山北路，骑行注意安全"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "driver-preview")
    }

    fun speak(text: String) {
        if (enabled && ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "driver")
    }

    fun shutdown() = tts.shutdown()
}
