package com.yuandian.quote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.yuandian.quote.data.Customer
import com.yuandian.quote.data.Product
import com.yuandian.quote.data.QuoteSummary
import com.yuandian.quote.ui.Routes
import com.yuandian.quote.ui.components.*
import com.yuandian.quote.ui.theme.*
import com.yuandian.quote.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

/** 通用“带搜索的列表” ViewModel：输入关键字后自动重新查询数据库 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchListViewModel<T>(source: (String) -> Flow<List<T>>) : ViewModel() {
    val query = MutableStateFlow("")
    val items: StateFlow<List<T>?> = query
        .debounce { if (it.isEmpty()) 0L else 200L } // 首次进入立即加载，输入时防抖
        .flatMapLatest { source(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun <T> SearchList(
    vm: SearchListViewModel<T>,
    placeholder: String,
    padding: PaddingValues,
    empty: @Composable (searching: Boolean) -> Unit,
    countLabel: (Int) -> String,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    Column(Modifier.padding(padding).fillMaxSize()) {
        SearchBox(query, { vm.query.value = it }, placeholder, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        val list = items
        when {
            list == null -> FullScreenLoading()
            list.isEmpty() -> empty(query.isNotBlank())
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(countLabel(list.size), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                items(list, key = key) { row(it) }
            }
        }
    }
}

@Composable
private fun AddFab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(text) },
    )
}

// ---------------- 客户列表 ----------------

@Composable
fun CustomerListScreen(nav: NavHostController) {
    val repo = appContainer().repository
    val vm: SearchListViewModel<Customer> = viewModel(key = "customers") { SearchListViewModel(repo::observeCustomers) }
    ScreenScaffold(
        title = "客户管理", isTab = true,
        floatingActionButton = { AddFab("添加客户") { nav.navigate(Routes.customerEdit()) } },
    ) { padding ->
        SearchList(
            vm, "搜索公司、联系人、电话", padding,
            empty = { searching ->
                if (searching) EmptyState(Icons.Outlined.Groups, "未找到匹配的客户", "换个关键字试试")
                else EmptyState(Icons.Outlined.Groups, "还没有客户", "点击右下角「添加客户」录入第一个客户")
            },
            countLabel = { "共 $it 位客户" },
            key = { it.id },
        ) { c -> CustomerRow(c) { nav.navigate(Routes.customerEdit(c.id)) } }
    }
}

@Composable
fun CustomerRow(c: Customer, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.company)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.company, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.contact.ifBlank { "未填联系人" }, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (c.phone.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Outlined.Phone, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(c.phone, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                if (c.remark.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(c.remark, style = MaterialTheme.typography.bodySmall, color = TextTertiary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------- 商品列表 ----------------

@Composable
fun ProductListScreen(nav: NavHostController) {
    val repo = appContainer().repository
    val vm: SearchListViewModel<Product> = viewModel(key = "products") { SearchListViewModel(repo::observeProducts) }
    ScreenScaffold(
        title = "商品管理", isTab = true,
        floatingActionButton = { AddFab("添加商品") { nav.navigate(Routes.productEdit()) } },
    ) { padding ->
        SearchList(
            vm, "搜索名称、型号、规格", padding,
            empty = { searching ->
                if (searching) EmptyState(Icons.Outlined.Inventory2, "未找到匹配的商品", "换个关键字试试")
                else EmptyState(Icons.Outlined.Inventory2, "还没有商品", "点击右下角「添加商品」建立商品库")
            },
            countLabel = { "共 $it 个商品" },
            key = { it.id },
        ) { p -> ProductRow(p) { nav.navigate(Routes.productEdit(p.id)) } }
    }
}

@Composable
fun ProductRow(p: Product, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.model.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Tag(p.model, GreenDark, GreenSoft)
                    }
                }
                if (p.spec.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(p.spec, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.formatWithSymbol(p.priceCents), style = MaterialTheme.typography.titleSmall)
                Text("/" + p.unit.ifBlank { "件" }, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

// ---------------- 报价单列表 ----------------

@Composable
fun QuoteListScreen(nav: NavHostController) {
    val repo = appContainer().repository
    val vm: SearchListViewModel<QuoteSummary> = viewModel(key = "quotes") { SearchListViewModel(repo::observeQuotes) }
    ScreenScaffold(
        title = "报价单", isTab = true,
        floatingActionButton = { AddFab("新建报价") { nav.navigate(Routes.quoteEdit()) } },
    ) { padding ->
        SearchList(
            vm, "搜索报价编号、客户", padding,
            empty = { searching ->
                if (searching) EmptyState(Icons.Outlined.Description, "未找到匹配的报价单", "换个关键字试试")
                else EmptyState(Icons.Outlined.Description, "还没有报价单", "点击右下角「新建报价」开始")
            },
            countLabel = { "共 $it 张报价单" },
            key = { it.id },
        ) { q -> QuoteSummaryCard(q) { nav.navigate(Routes.quoteDetail(q.id)) } }
    }
}
