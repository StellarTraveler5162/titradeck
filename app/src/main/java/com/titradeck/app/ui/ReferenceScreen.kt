package com.titradeck.app.ui

import android.content.ClipData
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titradeck.app.chemistry.*
import java.util.Locale
import kotlinx.coroutines.launch

private fun compact(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toString()

@Composable
fun ReferenceScreen(onUse: (ReferenceEntry) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("元素周期表") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Element?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = Modifier.testTag("reference_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("化学参考", "可靠数据，随手查阅。无需网络连接。") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("元素周期表", "常数与参考值").forEach { FilterChip(selected = mode == it, onClick = { mode = it; query = "" }, label = { Text(it) }) }
        } }
        item { SearchField(query, { query = it }, if (mode == "元素周期表") "搜索符号、名称或原子序数" else "搜索常数、电势、Ksp 或 pKa") }
        if (mode == "元素周期表") {
            if (query.isBlank()) {
                item { Text("左右滑动查看完整周期表 · 点击元素查看性质", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { PeriodicTable { selected = it } }
                item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PeriodicColorGroup.entries.forEach { group ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(9.dp).background(elementTint(group), RoundedCornerShape(3.dp)))
                            Text(group.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } }
                item { Text("* 表示没有稳定同位素 · 类金属按非金属颜色显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("原子量：CIAAW 简化标准原子量；其他性质：PubChem。无标准原子量的元素使用注明的代表同位素质量。缺失性质不会用估算值填补。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val matching = Elements.all.filter { it.symbol.contains(query, true) || it.name.contains(query) || it.englishName.contains(query, true) || it.atomicNumber.toString() == query }
                if (matching.isEmpty()) item { EmptyState("未找到元素", "可输入 Fe、铁、Iron 或 26。") }
                items(matching, key = { it.atomicNumber }) { element ->
                    Card(onClick = { selected = element }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ElementSymbol(element, 28.sp, MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) { Text("${element.name} · ${element.englishName}", style = MaterialTheme.typography.titleMedium); Text("原子序数 ${element.atomicNumber}  ·  ${element.category}", style = MaterialTheme.typography.bodySmall) }
                            Text(compact(element.atomicMass), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            val matching = ReferenceData.entries.filter { "${it.name} ${it.description} ${it.unit}".contains(query, true) }
            if (matching.isEmpty()) item { EmptyState("未找到参考值", "试试“醋酸”“银”或“气体”。") }
            items(matching, key = { it.id }) { constant ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LatexText(constant.name, style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { LatexText(listOf(compact(constant.value), constant.unit.takeUnless { it == "无量纲" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge) }
                        LatexText(constant.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text("来源：${constant.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (constant.toolId != null) FilledTonalButton(onClick = { onUse(constant) }) { Text("用于计算") }
                            TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(constant.name, constant.value.toString()))) } }) { Text("复制数值") }
                        }
                    }
                }
            }
        }
    }
    selected?.let { element -> ElementDialog(element) { selected = null } }
}

@Composable
private fun PeriodicTable(onSelect: (Element) -> Unit) {
    val map = remember { Elements.all.associateBy { it.displayRow to it.displayColumn } }
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (column in 1..18) Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) { Text("$column", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        for (row in 1..9) {
            if (row == 8) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 1..18) {
                    val element = map[row to column]
                    if (element != null) Surface(onClick = { onSelect(element) }, shape = RoundedCornerShape(9.dp),
                        color = elementTint(element.periodicColorGroup).copy(alpha = .13f), modifier = Modifier.width(58.dp).height(76.dp)) {
                        Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Text("${element.atomicNumber}", fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ElementSymbol(element, 21.sp, MaterialTheme.colorScheme.onSurface)
                            Text(element.name, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
                        }
                    } else Box(Modifier.width(58.dp).height(76.dp), contentAlignment = Alignment.Center) {
                        if (column == 3 && row in 6..7) Text(if (row == 6) "57–71\n镧系" else "89–103\n锕系", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun elementTint(group: PeriodicColorGroup): Color = when (group) {
    PeriodicColorGroup.NONMETAL -> Color(0xFF299569)
    PeriodicColorGroup.METAL -> Color(0xFF427DB8)
    PeriodicColorGroup.NOBLE_GAS -> Color(0xFF8B67CA)
}

@Composable
private fun ElementSymbol(element: Element, size: TextUnit, color: Color) {
    val description = if (element.hasNoStableIsotopes) "${element.name} ${element.symbol}，没有稳定同位素" else "${element.name} ${element.symbol}"
    Row(Modifier.testTag("element_symbol_${element.symbol}").semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top) {
        Text(element.symbol, fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.Bold, color = color)
        if (element.hasNoStableIsotopes) Text("*", modifier = Modifier.testTag("radioactive_marker_${element.symbol}"),
            fontSize = size * .55f, lineHeight = size * .65f, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ElementDialog(element: Element, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ElementSymbol(element, 38.sp, MaterialTheme.colorScheme.primary)
            Column { Text(element.name); Text(element.englishName, style = MaterialTheme.typography.bodyMedium) }
        } },
        text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val rows = listOf(
                "原子序数" to element.atomicNumber.toString(), "原子量" to compact(element.atomicMass),
                "周期 / 族" to "${element.period} / ${element.group?.toString() ?: "f 区"}", "分类" to element.category,
                "电子排布" to (element.electronConfiguration ?: "暂无可靠数据"),
                "电负性（Pauling）" to (element.electronegativity?.let(::compact) ?: "暂无可靠数据"),
                "常见氧化态" to (element.oxidationStates ?: "暂无可靠数据"),
                "原子半径（范德华）" to (element.atomicRadius?.let { "${compact(it)} pm" } ?: "暂无可靠数据"),
                "熔点" to (element.meltingPoint?.let { "${compact(it)} K / ${compact(it - 273.15)} °C" } ?: "暂无可靠数据"),
                "沸点" to (element.boilingPoint?.let { "${compact(it)} K / ${compact(it - 273.15)} °C" } ?: "暂无可靠数据"))
            rows.forEach { (label, value) -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LatexText(value, style = MaterialTheme.typography.bodyMedium)
            } }
            if (element.massIsRepresentative) Text("该元素没有标准原子量；此处使用 PubChem 的代表同位素质量，并非天然同位素平均值。", style = MaterialTheme.typography.bodySmall)
            Text("物性与压力、同素异形体及测定条件有关；氦熔点需加压，部分元素常压下升华。电子排布按来源的基态表示。", style = MaterialTheme.typography.bodySmall)
            Text("原子量来源：${element.massSource}\n性质来源：${element.source}", style = MaterialTheme.typography.labelSmall)
        } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
