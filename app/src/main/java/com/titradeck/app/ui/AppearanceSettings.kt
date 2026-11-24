package com.titradeck.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.titradeck.app.BuildConfig
import com.titradeck.app.R

@Composable
internal fun SettingsScreen(backgroundHex: String?, appearance: String, themeHex: String?,
    onBackground: (String?) -> Unit, onAppearance: (String) -> Unit, onThemeColor: (String?) -> Unit) {
    var customMode by rememberSaveable { mutableStateOf(backgroundHex != null) }
    var draft by rememberSaveable { mutableStateOf(backgroundHex ?: "#F4F6FA") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var customTheme by rememberSaveable { mutableStateOf(themeHex != null) }
    var themeDraft by rememberSaveable { mutableStateOf(themeHex ?: "#166B58") }
    var invalidTheme by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(backgroundHex) {
        if (backgroundHex != null) { draft = backgroundHex; customMode = true }
    }
    val presets = listOf("云白" to "#F4F6FA", "暖白" to "#FFF8EE", "浅蓝" to "#EDF3FF",
        "薄荷" to "#E9F6EF", "浅紫" to "#F2EEFA", "石墨" to "#181C22")

    LazyColumn(modifier = Modifier.testTag("settings_list"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge) }
        item { Text("主题色", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(selected = !customTheme, onClick = {
                customTheme = false
                invalidTheme = false
                onThemeColor(null)
            }, label = { Text("跟随系统主题色") })
            FilterChip(selected = customTheme, onClick = { customTheme = true }, label = { Text("自定义主题色") })
        } }
        if (customTheme) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("青绿" to "#166B58", "蓝色" to "#2865A6", "紫色" to "#7451A7").forEach { (name, hex) ->
                        FilterChip(
                            selected = themeHex == hex,
                            modifier = Modifier.weight(1f).testTag("theme_preset_${hex.drop(1)}"),
                            onClick = { themeDraft = hex; invalidTheme = false; onThemeColor(hex) },
                            leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(
                                BackgroundColors.parseColor(hex) ?: Color.Transparent)) },
                            label = { Text(name) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = themeDraft,
                    onValueChange = { themeDraft = it.take(32); invalidTheme = false },
                    modifier = Modifier.fillMaxWidth().testTag("theme_hex"),
                    label = { Text("主题色值（HEX）") },
                    singleLine = true,
                    isError = invalidTheme,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    supportingText = if (invalidTheme) { { Text("请输入 6 位十六进制颜色，例如 #166B58") } } else null,
                )
            }
            item {
                Button(onClick = {
                    val normalized = BackgroundColors.normalize(themeDraft)
                    if (normalized == null) {
                        invalidTheme = true
                    } else {
                        themeDraft = normalized
                        invalidTheme = false
                        focus.clearFocus()
                        keyboard?.hide()
                        onThemeColor(normalized)
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply_theme")) {
                    Text("应用主题色")
                }
            }
        }
        item { Text("外观模式", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                FilterChip(selected = appearance == value, onClick = { onAppearance(value) }, label = { Text(label) })
            }
        } }
        item { Text("背景颜色", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = !customMode, onClick = {
                    customMode = false
                    invalid = false
                    onBackground(null)
                }, label = { Text("跟随系统背景") })
                FilterChip(selected = customMode, onClick = {
                    customMode = true
                    invalid = false
                }, label = { Text("自定义背景") })
            }
        }
        if (customMode) {
            item { Text("选择预设颜色可直接应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(presets.chunked(3).size) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.chunked(3)[row].forEach { (name, hex) ->
                        val chosen = backgroundHex == hex
                        Surface(onClick = { draft = hex; invalid = false; onBackground(hex) },
                            modifier = Modifier.weight(1f).testTag("background_preset_${hex.drop(1)}"),
                            color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(color = BackgroundColors.parseColor(hex) ?: Color.Transparent, shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.size(28.dp)) {}
                                Text(name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item { OutlinedTextField(value = draft, onValueChange = { draft = it.take(32); invalid = false },
                modifier = Modifier.fillMaxWidth().testTag("background_hex"), label = { Text("背景色值（HEX）") },
                singleLine = true, isError = invalid, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                supportingText = { Text(if (invalid) "请输入 6 位十六进制颜色，例如 #F4F6FA" else "例如 #F4F6FA，也可以不输入 #。") }) }
            item { Button(onClick = {
                val normalized = BackgroundColors.normalize(draft)
                if (normalized == null) invalid = true else {
                    draft = normalized; invalid = false; focus.clearFocus(); keyboard?.hide(); onBackground(normalized)
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply_background")) { Text("应用背景") } }
        }
        item { Text("当前背景：${backgroundHex ?: "系统背景"}\n自定义背景会自动调整文字与卡片明暗，保证清晰。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppBrandIcon(size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("关于 TitraDeck", style = MaterialTheme.typography.titleMedium)
                        Text("化学工作台 · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("核心计算、118 种元素及常数库均可离线使用，无需账号。历史保存在本机，卸载应用会删除。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("数值结果受参考原子量、有效数字和模型假设影响；VSEPR 是注明范围的教学模型，化合物查询数据来自 PubChem。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("开发者：東站", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialButton("个人网站", R.drawable.ic_social_website, "https://blog.dongzhan.ink")
                    SocialButton("Bilibili", R.drawable.ic_social_bilibili, "https://space.bilibili.com/521110373")
                    SocialButton("X", R.drawable.ic_social_x, "https://x.com/dongzhan4137")
                    SocialButton("GitHub", R.drawable.ic_social_github, "https://github.com/dongzhan4137")
                }
            }
        } }
    }
}

@Composable
private fun SocialButton(name: String, icon: Int, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    FilledTonalIconButton(onClick = {
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, "无法打开 $name 主页", Toast.LENGTH_SHORT).show()
        }
    }) {
        Icon(painterResource(icon), contentDescription = name, modifier = Modifier.size(22.dp))
    }
}
