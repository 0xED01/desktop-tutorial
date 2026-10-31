package com.yuandian.quote.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yuandian.quote.ui.theme.Divider

/**
 * 统一页面骨架：顶部栏 + 内容 + 可选底部操作栏。
 * - 底部操作栏会自动避开系统导航栏和软键盘。
 * - isTab = true 时，外层底部导航已处理系统栏，这里不再重复留白。
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    isTab: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title, onBack, actions) },
        bottomBar = {
            if (bottomBar != null) {
                Surface(color = Color.White) {
                    Column {
                        HorizontalDivider(color = Divider)
                        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                            bottomBar()
                        }
                    }
                }
            }
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        containerColor = Color.White,
        contentWindowInsets = if (isTab) WindowInsets(0) else WindowInsets.systemBars.union(WindowInsets.ime),
        content = content,
    )
}
