package com.applestock.monitor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 有货时在锁屏上弹出的全屏提醒。 */
class AlertActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val url = intent.getStringExtra("url") ?: Apple.BASE
        if (intent.getBooleanExtra("go", false)) {
            go(url)
            return
        }
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FFF3CD"))
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
        }
        root.addView(TextView(this).apply {
            text = intent.getStringExtra("title") ?: "有货啦！"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#C00000"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = intent.getStringExtra("body") ?: ""
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, (16 * dp).toInt(), 0, (24 * dp).toInt())
        })
        root.addView(Button(this).apply {
            text = "去抢购"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0071E3"))
            setOnClickListener { go(url) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (64 * dp).toInt()))
        root.addView(Button(this).apply {
            text = "停止响铃"
            textSize = 16f
            setOnClickListener {
                AlarmPlayer.stop()
                finish()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt()).apply {
            topMargin = (12 * dp).toInt()
        })
        setContentView(root)
    }

    private fun go(url: String) {
        AlarmPlayer.stop()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        finish()
    }
}
