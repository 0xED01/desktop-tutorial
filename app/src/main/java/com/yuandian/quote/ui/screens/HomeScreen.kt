package com.yuandian.quote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.yuandian.quote.data.DashboardStats
import com.yuandian.quote.data.QuoteRepository
import com.yuandian.quote.data.QuoteSummary
import com.yuandian.quote.ui.Routes
import com.yuandian.quote.ui.components.EmptyState
import com.yuandian.quote.ui.components.SectionCard
import com.yuandian.quote.ui.components.SectionTitle
import com.yuandian.quote.ui.components.appContainer
import com.yuandian.quote.ui.switchTab
import com.yuandian.quote.ui.theme.*
import com.yuandian.quote.util.Dates
import com.yuandian.quote.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repo: QuoteRepository) : ViewModel() {
    val stats: StateFlow<DashboardStats?> = repo.observeDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val company by container.companyStore.profile.collectAsStateWithLifecycle()
    val s = stats ?: DashboardStats()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("原点报价助手", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        company.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = TextSecondary)
                }
            }
        }

        // 本月概览卡片
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Green, Color(0xFF2BB37E))), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text("本月报价总额（元）", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    Money.format(s.monthTotalCents), color = Color.White,
                    fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text("本月共 ${s.monthQuoteCount} 张报价单", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { nav.navigate(Routes.quoteEdit()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GreenDark),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.PostAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建报价单")
                }
            }
        }

        // 统计
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("客户", s.customerCount, Icons.Outlined.Groups, Modifier.weight(1f)) { nav.switchTab(Routes.CUSTOMERS) }
                StatCard("商品", s.productCount, Icons.Outlined.Inventory2, Modifier.weight(1f)) { nav.switchTab(Routes.PRODUCTS) }
                StatCard("报价单", s.quoteCount, Icons.Outlined.Description, Modifier.weight(1f)) { nav.switchTab(Routes.QUOTES) }
            }
        }

        // 快捷操作
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("添加客户", Icons.Outlined.PersonAdd, Modifier.weight(1f)) { nav.navigate(Routes.customerEdit()) }
                QuickAction("添加商品", Icons.Outlined.Add, Modifier.weight(1f)) { nav.navigate(Routes.productEdit()) }
            }
        }

        item {
            SectionTitle("最近报价", Modifier.padding(top = 8.dp)) {
                Text(
                    "查看全部", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { nav.switchTab(Routes.QUOTES) }.padding(4.dp),
                )
            }
        }
        if (stats != null && s.recent.isEmpty()) {
            item { EmptyState(Icons.Outlined.Description, "还没有报价单", "点击上方「新建报价单」开始第一张报价") }
        }
        items(s.recent, key = { it.id }) { q ->
            QuoteSummaryCard(q) { nav.navigate(Routes.quoteDetail(q.id)) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    SectionCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    SectionCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(GreenSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextTertiary)
        }
    }
}

/** 报价单摘要卡片（首页与报价列表共用） */
@Composable
fun QuoteSummaryCard(q: QuoteSummary, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.customerCompany, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(Money.formatWithSymbol(q.totalCents), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.quoteNo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text("· ${q.itemCount} 项", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            if (Dates.isExpired(q.validUntil)) {
                com.yuandian.quote.ui.components.Tag("已过期", TextSecondary, PageGray)
                Spacer(Modifier.width(6.dp))
            }
            Text(q.quoteDate, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
}
