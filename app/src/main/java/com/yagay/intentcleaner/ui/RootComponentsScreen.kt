package com.yagay.intentcleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.intentcleaner.data.CleanupKind

@Composable
fun RootComponentsScreen(state: MainState, vm: MainViewModel) {
    val scan by vm.componentScan.collectAsState()
    val busy by vm.componentBusy.collectAsState()
    val message by vm.componentMessage.collectAsState()
    var kind by remember { mutableStateOf(CleanupKind.TILE) }
    var disabledOnly by remember { mutableStateOf(false) }
    var expandedApps by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { vm.refreshComponents() }
    val visible = scan.items.filter {
        it.kind == kind && (!disabledOnly || it.enabled == false) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    // Package and user identify an app; display labels need not be unique.
    val groups = visible.groupBy { "${it.user}|${it.component.packageName}" }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("组件清理 · Root", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CleanupKind.entries.toList()) { entry ->
                FilterChip(selected = kind == entry, onClick = { kind = entry }, label = { Text(entry.title) })
            }
        }
        Text("勾选＝组件已禁用。直接读取系统状态，不保存名单，不区分由谁禁用。扫描无需 Root；更改时请求授权。清除本应用数据不改变系统状态。",
            style = MaterialTheme.typography.bodySmall)
        Text("勾选立即禁用，取消勾选立即明确启用，不再弹窗确认。禁用会影响正在使用此组件的功能；启用不是恢复原默认状态。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when (kind) {
            CleanupKind.TILE -> "仅列出标准 TileService。Wi-Fi、蓝牙等没有独立服务的系统内置磁贴不支持；核心系统组件仅展示。"
            CleanupKind.SHORTCUT -> "标准快捷方式创建 Activity；不含动态快捷方式或 ShortX 私有项目。禁用后可能影响其他使用该 Activity 的入口。"
            CleanupKind.WIDGET -> "带小部件声明的接收器。禁用可能使已放置的小部件失效；启用不保证恢复原布局。"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = disabledOnly, onClick = { disabledOnly = !disabledOnly }, label = { Text("只看已禁用") })
            Spacer(Modifier.weight(1f))
            Text("${groups.size} 个应用 · ${visible.size} 项", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = vm::refreshComponents, enabled = !busy) { Text(if (busy) "处理中…" else "刷新") }
        }
        if (scan.warning.isNotBlank()) Text(scan.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (appKey, components) ->
                val first = components.first()
                val expansionKey = "${kind.name}|$appKey"
                val expanded = expansionKey in expandedApps
                val onExpand = {
                    expandedApps = if (expansionKey in expandedApps) expandedApps - expansionKey
                        else expandedApps + expansionKey
                }
                item(key = "app|$expansionKey") {
                    Row(Modifier.fillMaxWidth()
                        .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                        .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(first.icon, first.owner)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(first.owner, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium)
                            Text("已禁用 ${components.count { it.enabled == false }}/${components.size} · ${kind.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onExpand) {
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                if (expanded) "折叠" else "展开")
                        }
                    }
                    HorizontalDivider()
                }
                if (expanded) items(components, key = { it.id }) { item ->
                    val editable = !busy && item.blocked == null && item.enabled != null
                    Row(Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .clickable(enabled = editable) { vm.changeComponent(item, item.enabled == false) }
                        .heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.enabled == false, enabled = editable, onCheckedChange = { checked -> vm.changeComponent(item, !checked) })
                        Column(Modifier.weight(1f)) {
                            Text(item.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.component.flattenToShortString(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            Text(item.blocked ?: when (item.overrideState) {
                                0 -> if (item.enabled == true) "默认启用" else "默认关闭"
                                1 -> "明确启用"
                                2, 3, 4 -> "已禁用（来源未知）"
                                else -> "状态未知"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (visible.isEmpty() && !busy) item { Text("没有匹配的组件", Modifier.padding(vertical = 16.dp)) }
        }
    }
}
