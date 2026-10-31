package com.yuandian.quote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuandian.quote.AppContainer
import com.yuandian.quote.YuandianApp
import com.yuandian.quote.ui.theme.Danger
import com.yuandian.quote.ui.theme.Divider
import com.yuandian.quote.ui.theme.GreenSoft
import com.yuandian.quote.ui.theme.PageGray
import com.yuandian.quote.ui.theme.TextPrimary
import com.yuandian.quote.ui.theme.TextSecondary
import com.yuandian.quote.ui.theme.TextTertiary

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as YuandianApp).container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column {
        CenterAlignedTopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
        )
        HorizontalDivider(color = Divider)
    }
}

/** 白色卡片 + 浅灰描边 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, Divider),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().height(52.dp),
        singleLine = true,
        placeholder = { Text(placeholder, color = TextTertiary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "清除", tint = TextTertiary)
            }
        },
        shape = RoundedCornerShape(26.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = PageGray,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    prefix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) "$label *" else label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = TextTertiary) }) else null,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        prefix = prefix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).background(GreenSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) Danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } },
        containerColor = Color.White,
    )
}

/** 圆形首字头像 */
@Composable
fun Avatar(text: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).background(GreenSoft, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            text.trim().take(1).ifEmpty { "?" },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun Tag(text: String, color: Color, background: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(text, color = Danger) }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}
