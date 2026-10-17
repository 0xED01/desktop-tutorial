package com.islandpet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.islandpet.model.PetRepo
import com.islandpet.ui.AdoptScreen
import com.islandpet.ui.BagScreen
import com.islandpet.ui.HomeScreen
import com.islandpet.ui.IslandSetupScreen
import com.islandpet.ui.Orange
import com.islandpet.ui.ProfileScreen
import com.islandpet.ui.ShopScreen
import com.islandpet.ui.backgroundBrush
import com.islandpet.ui.games.GamesScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Color(0xFF0D0D1A), surface = Color(0xFF1C1C2E))) {
                Surface(color = Color.Transparent, contentColor = Color.White) { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PetRepo.refresh()
    }
}

private val tabs = listOf("🏠" to "家", "🎮" to "游乐场", "🛍️" to "商店", "🎒" to "背包", "📖" to "档案")

@Composable
fun AppRoot() {
    val state by PetRepo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        PetRepo.messages.collect { toast = it }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }
    // App 打开时每 30 秒结算一次，让属性缓慢变化
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            PetRepo.refresh()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(state.backgroundBrush())
    ) {
        when {
            state.pet == null -> AdoptScreen()
            showSetup -> {
                BackHandler { showSetup = false }
                IslandSetupScreen(onBack = { showSetup = false })
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (tab) {
                        0 -> HomeScreen(state, onOpenSetup = { showSetup = true })
                        1 -> GamesScreen(state)
                        2 -> ShopScreen(state)
                        3 -> BagScreen(state)
                        else -> ProfileScreen(state, onOpenSetup = { showSetup = true })
                    }
                }
                NavigationBar(containerColor = Color.Black.copy(alpha = 0.35f)) {
                    tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = Orange,
                                unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                indicatorColor = Orange.copy(alpha = 0.25f),
                            ),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            Text(
                toast ?: "",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE2A2A40))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
