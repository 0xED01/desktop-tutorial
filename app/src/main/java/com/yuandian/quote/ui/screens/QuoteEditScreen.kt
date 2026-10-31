package com.yuandian.quote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.yuandian.quote.data.Customer
import com.yuandian.quote.data.Product
import com.yuandian.quote.data.Quote
import com.yuandian.quote.data.QuoteItem
import com.yuandian.quote.data.QuoteRepository
import com.yuandian.quote.ui.Routes
import com.yuandian.quote.ui.components.*
import com.yuandian.quote.ui.theme.*
import com.yuandian.quote.util.Dates
import com.yuandian.quote.util.Money
import kotlinx.coroutines.launch

/** 编辑中的一行明细：数量和单价以文本形式保存，便于用户自由输入 */
data class EditItem(
    val key: Long,
    val productId: Long?,
    val name: String,
    val model: String,
    val spec: String,
    val unit: String,
    val qtyText: String,
    val priceText: String,
) {
    val quantity get() = Money.parseQuantity(qtyText)
    val priceCents get() = Money.parseYuanToCents(priceText)
    val amountCents: Long?
        get() {
            val q = quantity ?: return null
            val p = priceCents ?: return null
            return Money.lineAmount(q, p)
        }
    val valid get() = amountCents != null
}

class QuoteEditViewModel(
    private val repo: QuoteRepository,
    private val id: Long,
    private val copyFrom: Long,
) : ViewModel() {

    val isNew = id == 0L
    var loaded by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set

    var quoteNo by mutableStateOf(""); private set
    var customer by mutableStateOf<Customer?>(null); private set
    var quoteDate by mutableStateOf(Dates.format(Dates.today())); private set
    var validUntil by mutableStateOf(Dates.format(Dates.today().plusDays(15))); private set
    var remark by mutableStateOf("")
        private set
    val items: SnapshotStateList<EditItem> = mutableStateListOf()

    var customerError by mutableStateOf(false); private set
    var itemsError by mutableStateOf<String?>(null); private set
    var showItemErrors by mutableStateOf(false); private set

    private var keySeq = 0L
    private var createdAt = 0L

    val totalCents: Long get() = items.sumOf { it.amountCents ?: 0L }

    init {
        viewModelScope.launch {
            val src = when {
                id != 0L -> repo.getQuote(id)
                copyFrom != 0L -> repo.getQuote(copyFrom)
                else -> null
            }
            if (src != null) {
                customer = Customer(src.customerId ?: 0L, src.customerCompany, src.customerContact, src.customerPhone)
                remark = src.remark
                src.items.forEach { items.add(it.toEditItem()) }
                if (id != 0L) {
                    quoteNo = src.quoteNo; quoteDate = src.quoteDate; validUntil = src.validUntil
                    createdAt = src.createdAt
                }
            }
            if (id == 0L) quoteNo = repo.nextQuoteNo(quoteDate)
            loaded = true
        }
    }

    private fun QuoteItem.toEditItem() = EditItem(
        ++keySeq, productId, name, model, spec, unit,
        Money.formatQuantity(quantity), Money.centsToInput(unitPriceCents),
    )

    fun selectCustomer(c: Customer) { customer = c; customerError = false; dirty = true }

    fun updateQuoteDate(d: String) {
        val days = Dates.daysBetween(quoteDate, validUntil).coerceAtLeast(0)
        quoteDate = d
        validUntil = Dates.format(Dates.parse(d).plusDays(days))
        dirty = true
        if (isNew) viewModelScope.launch { quoteNo = repo.nextQuoteNo(d) }
    }

    fun updateValidUntil(d: String) {
        validUntil = if (Dates.parse(d).isBefore(Dates.parse(quoteDate))) quoteDate else d
        dirty = true
    }

    fun setValidDays(days: Long) = updateValidUntil(Dates.format(Dates.parse(quoteDate).plusDays(days)))

    fun updateRemark(s: String) { remark = s; dirty = true }

    fun addProducts(products: List<Product>) {
        products.forEach { p ->
            items.add(EditItem(++keySeq, p.id, p.name, p.model, p.spec, p.unit, "1", Money.centsToInput(p.priceCents)))
        }
        if (products.isNotEmpty()) { itemsError = null; dirty = true }
    }

    fun updateQty(key: Long, text: String) = updateItem(key) { it.copy(qtyText = Money.filterDecimalInput(text, 3, 7)) }
    fun updatePrice(key: Long, text: String) = updateItem(key) { it.copy(priceText = Money.filterDecimalInput(text, 2)) }
    fun removeItem(key: Long) { items.removeAll { it.key == key }; dirty = true }

    private fun updateItem(key: Long, f: (EditItem) -> EditItem) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) { items[i] = f(items[i]); dirty = true }
    }

    /** 校验并保存；成功后回调报价单 id */
    fun save(onSaved: (Long) -> Unit) {
        customerError = customer == null
        itemsError = when {
            items.isEmpty() -> "请至少添加一个商品"
            items.any { !it.valid } -> "请检查标红的数量或单价"
            else -> null
        }
        showItemErrors = true
        if (customerError || itemsError != null || saving) return
        val c = customer!!
        saving = true
        viewModelScope.launch {
            // 新报价在保存时重新取号，确保编号不重复
            val no = if (isNew) repo.nextQuoteNo(quoteDate) else quoteNo
            val quote = Quote(
                id = id, quoteNo = no,
                customerId = c.id.takeIf { it != 0L },
                customerCompany = c.company, customerContact = c.contact, customerPhone = c.phone,
                quoteDate = quoteDate, validUntil = validUntil, remark = remark,
                totalCents = totalCents, createdAt = createdAt,
                items = items.map {
                    QuoteItem(
                        productId = it.productId, name = it.name, model = it.model, spec = it.spec, unit = it.unit,
                        quantity = Money.formatQuantity(it.quantity!!), unitPriceCents = it.priceCents!!,
                    )
                },
            )
            val newId = repo.saveQuote(quote)
            dirty = false
            saving = false
            onSaved(newId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditScreen(nav: NavHostController, id: Long, copyFrom: Long) {
    val repo = appContainer().repository
    val vm: QuoteEditViewModel = viewModel(key = "quote-edit-$id-$copyFrom") { QuoteEditViewModel(repo, id, copyFrom) }

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) } // "quote" / "valid"
    var confirmLeave by remember { mutableStateOf(false) }
    var removeKey by remember { mutableStateOf<Long?>(null) }

    val leave = { if (vm.dirty) confirmLeave = true else nav.popBackStack() }
    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    ScreenScaffold(
        title = if (vm.isNew) "新建报价单" else "编辑报价单",
        onBack = { leave() },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("合计（${vm.items.size} 项）", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        Money.formatWithSymbol(vm.totalCents), color = Green,
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
                Button(
                    onClick = {
                        vm.save { newId ->
                            nav.navigate(Routes.quoteDetail(newId)) {
                                popUpTo(Routes.QUOTE_EDIT) { inclusive = true }
                                // 从详情页进入编辑时，避免返回栈出现两个详情页
                                if (!vm.isNew) popUpTo(Routes.QUOTE_DETAIL) { inclusive = true }
                            }
                        }
                    },
                    enabled = vm.loaded && !vm.saving,
                    modifier = Modifier.height(48.dp).widthIn(min = 128.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(if (vm.saving) "保存中…" else "保存报价") }
            }
        },
    ) { padding ->
        if (!vm.loaded) { Box(Modifier.padding(padding)) { FullScreenLoading() }; return@ScreenScaffold }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().background(PageGray),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- 客户 ----------
            item {
                SectionCard(onClick = { showCustomerPicker = true }) {
                    Text("客户", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = vm.customer
                        if (c == null) {
                            Text(
                                "请选择客户", style = MaterialTheme.typography.titleSmall,
                                color = if (vm.customerError) Danger else TextTertiary, modifier = Modifier.weight(1f),
                            )
                        } else {
                            Avatar(c.company)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.company, style = MaterialTheme.typography.titleSmall)
                                val sub = listOf(c.contact, c.phone).filter { it.isNotBlank() }.joinToString("  ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
                    }
                    if (vm.customerError) {
                        Text("请选择客户", color = Danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---------- 报价信息 ----------
            item {
                SectionCard {
                    InfoRow("报价编号", vm.quoteNo + if (vm.isNew) "（保存时确认）" else "")
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
                    DateRow("报价日期", vm.quoteDate) { pickDate = "quote" }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
                    DateRow("有效期至", vm.validUntil) { pickDate = "valid" }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = Dates.daysBetween(vm.quoteDate, vm.validUntil)
                        listOf(7L, 15L, 30L, 60L).forEach { d ->
                            FilterChip(
                                selected = current == d, onClick = { vm.setValidDays(d) },
                                label = { Text("${d}天") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenSoft, selectedLabelColor = GreenDark,
                                ),
                            )
                        }
                    }
                }
            }

            // ---------- 商品明细 ----------
            item {
                SectionTitle("商品明细") {
                    TextButton(onClick = { showProductPicker = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加商品")
                    }
                }
                vm.itemsError?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            }
            if (vm.items.isEmpty()) {
                item {
                    SectionCard(onClick = { showProductPicker = true }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Inventory2, null, tint = Green, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("从商品库选择商品", color = Green, style = MaterialTheme.typography.titleSmall)
                            Text("可多选，数量与单价可自由修改", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(vm.items, key = { it.key }) { item ->
                val index = vm.items.indexOfFirst { it.key == item.key }
                ItemEditor(
                    index = index + 1,
                    item = item,
                    showErrors = vm.showItemErrors,
                    onQty = { vm.updateQty(item.key, it) },
                    onPrice = { vm.updatePrice(item.key, it) },
                    onRemove = { removeKey = item.key },
                )
            }

            // ---------- 备注 ----------
            item {
                SectionTitle("备注", Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = vm.remark, onValueChange = vm::updateRemark,
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("如：含税含运费、交货期、付款方式等", color = TextTertiary) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                    ),
                )
            }
        }
    }

    if (showCustomerPicker) CustomerPickerSheet(
        repo = repo,
        onDismiss = { showCustomerPicker = false },
        onPick = { vm.selectCustomer(it); showCustomerPicker = false },
        onCreate = { showCustomerPicker = false; nav.navigate(Routes.customerEdit()) },
    )
    if (showProductPicker) ProductPickerSheet(
        repo = repo,
        onDismiss = { showProductPicker = false },
        onConfirm = { vm.addProducts(it); showProductPicker = false },
        onCreate = { showProductPicker = false; nav.navigate(Routes.productEdit()) },
    )
    pickDate?.let { which ->
        val initial = if (which == "quote") vm.quoteDate else vm.validUntil
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(initial))
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Dates.fromPickerMillis(it)
                        if (which == "quote") vm.updateQuoteDate(d) else vm.updateValidUntil(d)
                    }
                    pickDate = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("取消") } },
            colors = DatePickerDefaults.colors(containerColor = Color.White),
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Color.White))
        }
    }
    if (confirmLeave) ConfirmDialog(
        "放弃编辑？", "当前修改尚未保存，确定要离开吗？", "离开", danger = true,
        onConfirm = { confirmLeave = false; nav.popBackStack() },
        onDismiss = { confirmLeave = false },
    )
    removeKey?.let { key ->
        val name = vm.items.firstOrNull { it.key == key }?.name ?: ""
        ConfirmDialog(
            "移除商品", "确定从报价单中移除「$name」吗？", "移除", danger = true,
            onConfirm = { vm.removeItem(key); removeKey = null },
            onDismiss = { removeKey = null },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(80.dp))
        Spacer(Modifier.weight(1f))
        Text(Dates.chinese(value), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.CalendarMonth, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ItemEditor(
    index: Int,
    item: EditItem,
    showErrors: Boolean,
    onQty: (String) -> Unit,
    onPrice: (String) -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$index.", style = MaterialTheme.typography.titleSmall, color = TextTertiary)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOf(item.model, item.spec).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "移除", tint = TextTertiary) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "数量", value = item.qtyText, onChange = onQty, suffix = item.unit,
                isError = showErrors && item.quantity == null, modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "报价单价", value = item.priceText, onChange = onPrice, prefix = "¥",
                isError = showErrors && item.priceCents == null, modifier = Modifier.weight(1.3f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("小计 ", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                item.amountCents?.let { Money.formatWithSymbol(it) } ?: "—",
                style = MaterialTheme.typography.titleSmall, color = TextPrimary,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier,
    isError: Boolean, prefix: String? = null, suffix: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label) }, singleLine = true, isError = isError,
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.takeIf { it.isNotBlank() }?.let { { Text(it, color = TextSecondary) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(10.dp),
    )
}

// ======================= 选择客户 =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPickerSheet(
    repo: QuoteRepository,
    onDismiss: () -> Unit,
    onPick: (Customer) -> Unit,
    onCreate: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val list by remember(query) { repo.observeCustomers(query) }.collectAsStateWithLifecycle(initialValue = null)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("选择客户", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreate) {
                    Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("新建客户")
                }
            }
            SearchBox(query, { query = it }, "搜索公司、联系人、电话", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val l = list
            when {
                l == null -> FullScreenLoading()
                l.isEmpty() -> EmptyState(Icons.Outlined.Groups, "没有找到客户", "可点击右上角「新建客户」")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(l, key = { it.id }) { c -> CustomerRow(c) { onPick(c) } }
                }
            }
        }
    }
}

// ======================= 选择商品（多选） =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
    repo: QuoteRepository,
    onDismiss: () -> Unit,
    onConfirm: (List<Product>) -> Unit,
    onCreate: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val list by remember(query) { repo.observeProducts(query) }.collectAsStateWithLifecycle(initialValue = null)
    // 保持选择顺序
    val selected = remember { mutableStateMapOf<Long, Product>() }
    val order = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("选择商品", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("新建商品")
                }
            }
            SearchBox(query, { query = it }, "搜索名称、型号、规格", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Box(Modifier.weight(1f)) {
                val l = list
                when {
                    l == null -> FullScreenLoading()
                    l.isEmpty() -> EmptyState(Icons.Outlined.Inventory2, "没有找到商品", "可点击右上角「新建商品」")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(l, key = { it.id }) { p ->
                            val checked = selected.containsKey(p.id)
                            val toggle: () -> Unit = {
                                if (checked) {
                                    selected.remove(p.id); order.remove(p.id)
                                } else {
                                    selected[p.id] = p; order.add(p.id)
                                }
                            }
                            ProductRow(p, trailing = { Checkbox(checked = checked, onCheckedChange = { toggle() }) }, onClick = toggle)
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider)
            Button(
                onClick = { onConfirm(order.mapNotNull { selected[it] }) },
                enabled = order.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(if (order.isEmpty()) "请选择商品" else "添加所选商品（${order.size}）") }
        }
    }
}
