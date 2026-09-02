package com.yagay.intentcleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.intentcleaner.data.CleanupKind
import com.yagay.intentcleaner.data.RootComponent

@Composable
fun RootComponentsScreen(state: MainState, vm: MainViewModel) {
    val scan by vm.componentScan.collectAsState()
    val busy by vm.componentBusy.collectAsState()
    val message by vm.componentMessage.collectAsState()
    var kind by remember { mutableStateOf(CleanupKind.TILE) }
    var disabledOnly by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RootComponent?>(null) }
    LaunchedEffect(Unit) { vm.refreshComponents() }
    val visible = scan.items.filter {
        it.kind == kind && (!disabledOnly || it.enabled == false) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    pending?.let { target ->
        val enable = target.enabled == false
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(if (enable) "启用这个组件？" else "禁用这个组件？") },
            text = { Text("${target.owner} · ${target.label}\n${target.component.flattenToString()}\n\n" +
                if (enable) "这是明确启用，不是恢复原状态。无法判断原先由谁禁用；默认关闭的组件也会被启用。不会启用整个应用。"
                else "将通过 Root 禁用此组件，而不只是隐藏列表。已固定磁贴、已放置小部件或其他使用此组件的功能也可能失效；应用进程可能被系统结束。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { pending = null; vm.changeComponent(target, enable) }) {
                Text(if (enable) "确认启用" else "确认禁用")
            } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("组件清理 · Root", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CleanupKind.entries.toList()) { entry ->
                FilterChip(selected = kind == entry, onClick = { kind = entry }, label = { Text(entry.title) })
            }
        }
        Text("勾选＝组件已禁用。直接读取系统状态，不保存名单，不区分由谁禁用。扫描无需 Root；更改时请求授权。清除本应用数据不改变系统状态。",
            style = MaterialTheme.typography.bodySmall)
        Text(when (kind) {
            CleanupKind.TILE -> "仅列出标准 TileService。Wi-Fi、蓝牙等没有独立服务的系统内置磁贴不支持；核心系统组件仅展示。"
            CleanupKind.SHORTCUT -> "标准快捷方式创建 Activity；不含动态快捷方式或 ShortX 私有项目。禁用后可能影响其他使用该 Activity 的入口。"
            CleanupKind.WIDGET -> "带小部件声明的接收器。禁用可能使已放置的小部件失效；启用不保证恢复原布局。"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = disabledOnly, onClick = { disabledOnly = !disabledOnly }, label = { Text("只看已禁用") })
            Spacer(Modifier.weight(1f))
            Text("${visible.size} 项", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = vm::refreshComponents, enabled = !busy) { Text(if (busy) "处理中…" else "刷新") }
        }
        if (scan.warning.isNotBlank()) Text(scan.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.id }) { item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                Row(Modifier.fillMaxWidth().clickable(enabled = editable) { pending = item }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.enabled == false, enabled = editable, onCheckedChange = { pending = item })
                    AppIcon(item.icon, item.owner)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${item.owner} · ${item.label}", maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            if (visible.isEmpty() && !busy) item { Text("没有匹配的组件", Modifier.padding(vertical = 16.dp)) }
        }
    }
}
