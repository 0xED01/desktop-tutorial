package com.yuandian.quote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.yuandian.quote.data.Customer
import com.yuandian.quote.data.Product
import com.yuandian.quote.data.QuoteRepository
import com.yuandian.quote.ui.components.*
import com.yuandian.quote.ui.theme.GreenDark
import com.yuandian.quote.ui.theme.GreenSoft
import com.yuandian.quote.ui.theme.TextSecondary
import com.yuandian.quote.util.Money
import kotlinx.coroutines.launch

@Composable
private fun SaveBar(text: String = "保存", enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp),
        shape = RoundedCornerShape(10.dp),
    ) { Text(text) }
}

// ======================= 客户编辑 =======================

class CustomerEditViewModel(private val repo: QuoteRepository, private val id: Long) : ViewModel() {
    var company by mutableStateOf(""); var contact by mutableStateOf("")
    var phone by mutableStateOf(""); var remark by mutableStateOf("")
    var companyError by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(id == 0L)
    var saving by mutableStateOf(false)
    private var createdAt = 0L

    init {
        if (id != 0L) viewModelScope.launch {
            repo.getCustomer(id)?.let {
                company = it.company; contact = it.contact; phone = it.phone; remark = it.remark; createdAt = it.createdAt
            }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        if (company.isBlank()) { companyError = "请输入公司名称"; return }
        saving = true
        viewModelScope.launch {
            repo.saveCustomer(Customer(id, company, contact, phone, remark, createdAt))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteCustomer(id); onDone() }
}

@Composable
fun CustomerEditScreen(nav: NavHostController, id: Long) {
    val repo = appContainer().repository
    val vm: CustomerEditViewModel = viewModel(key = "customer-$id") { CustomerEditViewModel(repo, id) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = if (id == 0L) "添加客户" else "编辑客户",
        onBack = { nav.popBackStack() },
        bottomBar = { SaveBar(enabled = !vm.saving) { vm.save { nav.popBackStack() } } },
    ) { padding ->
        if (!vm.loaded) { Box(Modifier.padding(padding)) { FullScreenLoading() }; return@ScreenScaffold }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField("公司名称", vm.company, { vm.company = it; vm.companyError = null }, required = true,
                placeholder = "例如：青禾机电设备有限公司", error = vm.companyError)
            FormField("联系人", vm.contact, { vm.contact = it }, placeholder = "例如：林经理")
            FormField("联系电话", vm.phone, { vm.phone = it }, keyboardType = KeyboardType.Phone, placeholder = "手机或座机")
            FormField("备注", vm.remark, { vm.remark = it }, singleLine = false, minLines = 3,
                placeholder = "付款方式、采购偏好等")
            if (id != 0L) {
                Spacer(Modifier.height(8.dp))
                DangerTextButton("删除该客户", { confirmDelete = true })
                Text("删除客户不会影响已生成的报价单。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "删除客户", "确定删除「${vm.company}」吗？此操作不可恢复。", "删除", danger = true,
        onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}

// ======================= 商品编辑 =======================

class ProductEditViewModel(private val repo: QuoteRepository, private val id: Long) : ViewModel() {
    var name by mutableStateOf(""); var model by mutableStateOf(""); var spec by mutableStateOf("")
    var unit by mutableStateOf("个"); var price by mutableStateOf("")
    var nameError by mutableStateOf<String?>(null); var priceError by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(id == 0L)
    var saving by mutableStateOf(false)
    private var createdAt = 0L

    init {
        if (id != 0L) viewModelScope.launch {
            repo.getProduct(id)?.let {
                name = it.name; model = it.model; spec = it.spec; unit = it.unit
                price = Money.centsToInput(it.priceCents); createdAt = it.createdAt
            }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        val cents = Money.parseYuanToCents(price)
        nameError = if (name.isBlank()) "请输入商品名称" else null
        priceError = if (cents == null) "请输入正确的单价" else null
        if (nameError != null || priceError != null) return
        saving = true
        viewModelScope.launch {
            repo.saveProduct(Product(id, name, model, spec, unit.ifBlank { "个" }, cents!!, createdAt))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteProduct(id); onDone() }
}

private val commonUnits = listOf("个", "台", "件", "套", "只", "米", "根", "箱", "盒", "包", "千克", "吨")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditScreen(nav: NavHostController, id: Long) {
    val repo = appContainer().repository
    val vm: ProductEditViewModel = viewModel(key = "product-$id") { ProductEditViewModel(repo, id) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = if (id == 0L) "添加商品" else "编辑商品",
        onBack = { nav.popBackStack() },
        bottomBar = { SaveBar(enabled = !vm.saving) { vm.save { nav.popBackStack() } } },
    ) { padding ->
        if (!vm.loaded) { Box(Modifier.padding(padding)) { FullScreenLoading() }; return@ScreenScaffold }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField("商品名称", vm.name, { vm.name = it; vm.nameError = null }, required = true,
                placeholder = "例如：深沟球轴承", error = vm.nameError)
            FormField("型号", vm.model, { vm.model = it }, placeholder = "例如：6205-2RS")
            FormField("规格", vm.spec, { vm.spec = it }, singleLine = false, minLines = 2,
                placeholder = "尺寸、材质、参数等")
            FormField(
                "单价（元）", vm.price,
                { vm.price = Money.filterDecimalInput(it, 2); vm.priceError = null },
                required = true, keyboardType = KeyboardType.Decimal, prefix = "¥ ", error = vm.priceError,
            )
            FormField("单位", vm.unit, { vm.unit = it.take(6) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commonUnits.forEach { u ->
                    FilterChip(
                        selected = vm.unit == u, onClick = { vm.unit = u }, label = { Text(u) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GreenSoft, selectedLabelColor = GreenDark,
                        ),
                    )
                }
            }
            if (id != 0L) {
                Spacer(Modifier.height(8.dp))
                DangerTextButton("删除该商品", { confirmDelete = true })
                Text("删除商品不会影响已生成的报价单。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "删除商品", "确定删除「${vm.name}」吗？此操作不可恢复。", "删除", danger = true,
        onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
    )
}
