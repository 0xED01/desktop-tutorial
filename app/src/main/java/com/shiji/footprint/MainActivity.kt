package com.shiji.footprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shiji.footprint.ui.FootprintRoot
import com.shiji.footprint.ui.theme.FootprintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FootprintTheme {
                FootprintRoot()
            }
        }
    }
}
