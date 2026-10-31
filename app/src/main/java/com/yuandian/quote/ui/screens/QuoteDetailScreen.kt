package com.yuandian.quote.ui.screens

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.yuandian.quote.AppContainer
import com.yuandian.quote.data.Quote
import com.yuandian.quote.ui.Routes
import com.yuandian.quote.ui.components.*
import com.yuandian.quote.ui.theme.*
import com.yuandian.quote.util.Dates
import com.yuandian.quote.util.Money
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class QuoteDetailViewModel(private val c: AppContainer, id: Long) : ViewModel() {

    /** null = 加载中；Loaded(null) = 已删除/不存在 */
    data class Loaded(val quote: Quote?)

    val quote: StateFlow<Loaded?> = c.repository.observeQuote(id).map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var pdfFile by mutableStateOf<File?>(null); private set
    var pages by mutableStateOf<List<Bitmap>>(emptyList()); private set
    var pdfError by mutableStateOf<String?>(null); private set

    init {
        // 报价单或公司信息有变化时，自动重新生成 PDF 与预览
        viewModelScope.launch {
            combine(quote.filterNotNull(), c.companyStore.profile) { q, p -> q.quote to p }
                .collectLatest { (q, profile) ->
                    if (q == null) return@collectLatest
                    try {
                        val f = c.pdfExporter.generate(q, profile)
                        pages = c.pdfExporter.renderPreview(f, 1080)
                        pdfFile = f
                        pdfError = null
                    } catch (e: Exception) {
                        pdfError = "PDF 生成失败：${e.message}"
                    }
                }
        }
    }

    fun saveToPhone(onResult: (String) -> Unit) {
        val f = pdfFile ?: return
        viewModelScope.launch {
            onResult(
                try { "已保存到：" + c.pdfExporter.saveToDownloads(f) }
                catch (e: Exception) { "保存失败：${e.message}" }
            )
        }
    }

    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch { c.repository.deleteQuote(id); onDone() }
}

@Composable
fun QuoteDetailScreen(nav: NavHostController, id: Long) {
    val container = appContainer()
    val vm: QuoteDetailViewModel = viewModel(key = "quote-detail-$id") { QuoteDetailViewModel(container, id) }
    val loaded by vm.quote.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val quote = loaded?.quote

    // 报价单被删除后自动返回
    LaunchedEffect(loaded) { if (loaded != null && loaded?.quote == null) nav.popBackStack() }

    ScreenScaffold(
        title = "报价单详情",
        onBack = { nav.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            if (quote != null) {
                IconButton(onClick = { nav.navigate(Routes.quoteEdit(id = id)) }) { Icon(Icons.Outlined.Edit, "编辑") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(
                            text = { Text("复制为新报价") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; nav.navigate(Routes.quoteEdit(copyFrom = id)) },
                        )
                        DropdownMenuItem(
                            text = { Text("用其他应用打开") },
                            leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                            enabled = vm.pdfFile != null,
                            onClick = {
                                menu = false
                                vm.pdfFile?.let {
                                    try { context.startActivity(container.pdfExporter.viewIntent(it)) }
                                    catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "手机上没有可以打开 PDF 的应用", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除报价单", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Danger) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ready = vm.pdfFile != null
                OutlinedButton(
                    onClick = { vm.saveToPhone { msg -> scope.launch { snackbar.showSnackbar(msg) } } },
                    enabled = ready,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (ready) Green else Divider),
                ) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("保存到手机")
                }
                Button(
                    onClick = {
                        val f = vm.pdfFile ?: return@Button
                        val q = quote ?: return@Button
                        context.startActivity(
                            container.pdfExporter.shareIntent(f, "报价单 ${q.quoteNo} - ${q.customerCompany}")
                        )
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("分享 PDF")
                }
            }
        },
    ) { padding ->
        if (quote == null) { Box(Modifier.padding(padding)) { FullScreenLoading() }; return@ScreenScaffold }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().background(PageGray),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(quote) }
            item {
                SectionTitle("PDF 预览") {
                    if (vm.pages.isNotEmpty()) Text("共 ${vm.pages.size} 页", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }
            vm.pdfError?.let { err -> item { Text(err, color = Danger) } }
            if (vm.pages.isEmpty() && vm.pdfError == null) {
                item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            itemsIndexed(vm.pages) { _, bmp ->
                val img = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = img, contentDescription = "PDF 页面",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height)
                        .background(Color.White)
                        .border(1.dp, Divider),
                )
            }
        }
    }

    if (confirmDelete && quote != null) ConfirmDialog(
        "删除报价单", "确定删除报价单「${quote.quoteNo}」吗？此操作不可恢复。", "删除", danger = true,
        onConfirm = { confirmDelete = false; vm.delete(id) {} },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun SummaryCard(q: Quote) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.quoteNo, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            if (Dates.isExpired(q.validUntil)) Tag("已过期", TextSecondary, PageGray)
            else Tag("有效期内", GreenDark, GreenSoft)
        }
        Spacer(Modifier.height(8.dp))
        Text(q.customerCompany, style = MaterialTheme.typography.titleMedium)
        val sub = listOf(q.customerContact, q.customerPhone).filter { it.isNotBlank() }.joinToString("  ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("报价日期", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(q.quoteDate, style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("有效期至", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(q.validUntil, style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(0.6f)) {
                Text("商品", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text("${q.items.size} 项", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("合计金额", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(Money.formatWithSymbol(q.totalCents), color = Green, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("人民币" + Money.toChineseUppercase(q.totalCents), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
