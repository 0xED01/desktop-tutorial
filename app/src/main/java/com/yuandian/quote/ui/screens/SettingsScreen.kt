package com.yuandian.quote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.yuandian.quote.data.CompanyProfile
import com.yuandian.quote.data.CompanyProfileStore
import com.yuandian.quote.ui.components.*
import com.yuandian.quote.ui.theme.TextSecondary
import com.yuandian.quote.ui.theme.TextTertiary

@Composable
fun SettingsScreen(nav: NavHostController) {
    val store = appContainer().companyStore
    val initial = remember { store.profile.value }
    var name by remember { mutableStateOf(initial.name) }
    var contact by remember { mutableStateOf(initial.contact) }
    var phone by remember { mutableStateOf(initial.phone) }
    var address by remember { mutableStateOf(initial.address) }
    var terms by remember { mutableStateOf(initial.terms) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    ScreenScaffold(
        title = "设置",
        onBack = { nav.popBackStack() },
        bottomBar = {
            Button(
                onClick = {
                    store.save(CompanyProfile(name, contact, phone, address, terms))
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("保存") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("本公司信息（显示在 PDF 抬头）")
            FormField("公司名称", name, { name = it })
            FormField("联系人", contact, { contact = it })
            FormField("联系电话", phone, { phone = it }, keyboardType = KeyboardType.Phone)
            FormField("地址", address, { address = it }, singleLine = false)
            SectionTitle("报价条款（显示在 PDF 底部）", Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { terms = CompanyProfileStore.DEFAULT_TERMS }) { Text("恢复默认") }
            }
            FormField("报价条款", terms, { terms = it }, singleLine = false, minLines = 4)

            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("原点报价助手 v$version", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("原点软件 出品 · 数据仅保存在本机", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
    }
}
