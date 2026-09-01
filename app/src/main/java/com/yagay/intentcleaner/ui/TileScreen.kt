package com.yagay.intentcleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.intentcleaner.data.TileCandidate

@Composable
fun TileScreen(state: MainState, vm: MainViewModel) {
    val scan by vm.tileScan.collectAsState()
    val loading by vm.tilesLoading.collectAsState()
    var selectedOnly by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshTiles() }
    val known = scan.items.map { it.spec }.toSet()
    val all = scan.items + (state.tiles.hidden - known).sorted().map {
        TileCandidate(it, it, "已配置，本次未扫描到；仍可取消清理")
    }
    val visible = all.filter { tile ->
        (!selectedOnly || tile.spec in state.tiles.hidden) &&
            (state.query.isBlank() || listOf(tile.label, tile.owner, tile.spec).any { it.contains(state.query, true) })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清空磁贴清理规则？") },
        text = { Text("仅清空本模块的磁贴规则，不更改系统已固定磁贴。") },
        confirmButton = { TextButton(onClick = { vm.clearTileRules(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("磁贴清理", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Switch(state.tiles.enabled, vm::setTilesEnabled)
        }
        Text("勾选后只隐藏编辑界面的待添加磁贴。已固定磁贴保持不变；关闭开关或取消勾选后，重新打开系统编辑页面恢复。此开关独立于 Intent 规则的显示模式。",
            style = MaterialTheme.typography.bodySmall)
        if (!state.runtime.ready) Text(state.runtime.message, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        if (state.tiles.enabled && "com.android.systemui" !in state.module.grantedScope) {
            Text("需要新增 SystemUI 作用域。授权后，首次使用建议重启手机。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::requestScope, enabled = state.module.connected && !state.module.requesting) { Text("申请所需作用域") }
        }
        Text("已适配 AOSP 传统及新版编辑路径；一加等厂商实现尚需实机确认，授权/保存成功不等于过滤已生效。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("只看已选 ${state.tiles.hidden.size}") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::refreshTiles, enabled = !loading) { Text(if (loading) "扫描中…" else "刷新") }
            TextButton(onClick = { confirmClear = true }, enabled = state.tiles.hidden.isNotEmpty()) { Text("清空") }
        }
        Text(scan.warning, style = MaterialTheme.typography.labelSmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.spec }) { tile ->
                val checked = tile.spec in state.tiles.hidden
                Row(Modifier.fillMaxWidth().clickable { vm.setTileHidden(tile.spec, !checked) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = { vm.setTileHidden(tile.spec, it) })
                    AppIcon(tile.icon, tile.label)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tile.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(tile.owner, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (visible.isEmpty() && !loading) item { Text("没有匹配的磁贴", Modifier.padding(vertical = 16.dp)) }
        }
    }
}
