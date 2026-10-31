package com.ebike.dash

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Voice(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    var enabled = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) ready = tts.setLanguage(Locale.getDefault()) >= 0
        }
    }

    fun speak(text: String) {
        if (enabled && ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ridedash")
    }

    fun shutdown() = tts.shutdown()
}
