package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.priorityCandidates

@Composable
fun PriorityDialogContent(state: MainState, vm: MainViewModel) {
    var kind by remember { mutableStateOf(state.filter ?: IntentKind.SHARE) }
    var showInfo by remember { mutableStateOf(false) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    
    val rankedRaw = state.priorities.apps[kind].orEmpty()
    val apps = remember(state.candidates, state.selected, state.displayMode, kind) {
        priorityCandidates(state.candidates, state.selected, state.displayMode, kind)
            .groupBy { it.rule.packageName }
    }
    val visibleRanked = rankedRaw.filter { it in apps }

    // 同时对已优先和可添加列表进行全局搜索过滤
    val ranked = visibleRanked.mapIndexed { index, pkg -> index to pkg }.filter { (_, pkg) ->
        state.query.isBlank() || pkg.contains(state.query, true) ||
            apps[pkg]?.any { it.matchesQuery(state.query) } == true
    }
    
    val available = apps.keys.filter { packageName ->
        packageName !in rankedRaw && apps.getValue(packageName).any {
            it.isCatalogCandidate && it.matchesQuery(state.query)
        }
    }.sortedBy { apps.getValue(it).first().appLabel.lowercase() }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("优先排序", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showInfo = !showInfo }) { Text("说明") }
        }
        LazyRow(contentPadding = PaddingValues(bottom = 8.dp)) {
            items(IntentKind.entries.toList()) { entry ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { kind = entry; expandedKey = null }) {
                        Text(entry.shortTitle,
                            fontWeight = if (kind == entry) FontWeight.Bold else FontWeight.Normal,
                            color = if (kind == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.height(2.dp).width(24.dp).background(
                        if (kind == entry) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        if (showInfo) Text("按分类保存。规则要求清理的组件不显示；应用仍有可见组件时保留。暂时隐藏的排序配置不删除，取消清理后恢复。分享适配推荐区及全部应用区，文本处理在查询出口排序；未知厂商菜单可能另行重排。联系人和调用方专属入口不调整。", style = MaterialTheme.typography.bodySmall)
        val compatibility = when {
            !state.module.connected -> "LSPosed 未连接：可以保存，但尚未生效。"
            state.module.detection.hosts.isEmpty() -> "未确认选择器宿主，当前设备排序能力未知。"
            state.module.detection.hosts.any { it.packageName != "system" && !it.className.startsWith("com.android.internal.app.") && !it.className.startsWith("com.android.intentresolver.") } ->
                "厂商独立排序路径尚未适配；仅走 AOSP 路径时可能有效，不能保证置顶。"
            else -> "已实现 AOSP 路径适配，未确认本机命中。保存后重新打开选择器核对；更新模块后需重启选择器。"
        }
        Text(if (!state.runtime.ready) state.runtime.message else if (kind == IntentKind.PROCESS_TEXT) "文本候选按查询结果排序；来源应用若自行重排，仍可能不同。保存后重新打开文本菜单。" else compatibility,
            Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        LazyColumn(Modifier.weight(1f)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("优先应用（${visibleRanked.size}）", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.resetPriority(kind) }, enabled = rankedRaw.isNotEmpty()) { Text("恢复系统顺序") }
                }
                if (visibleRanked.isEmpty()) Text("当前没有可显示的优先应用，从下方添加。", style = MaterialTheme.typography.bodySmall)
                if (rankedRaw.size != visibleRanked.size) Text("已保存 ${rankedRaw.size}/200 项；已清理或本次未匹配项暂不显示。", style = MaterialTheme.typography.bodySmall)
            }
            
            items(ranked, key = { "priority|${it.second}" }) { (originalIndex, packageName) ->
                val components = apps[packageName].orEmpty()
                val key = "ranked|$packageName"
                val expanded = expandedKey == key
                
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expandedKey = if (expanded) null else key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(components.firstOrNull()?.appIcon, components.firstOrNull()?.appLabel ?: packageName)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${originalIndex + 1}. ${components.firstOrNull()?.appLabel ?: packageName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.movePriority(kind, packageName, -1, visibleRanked) }, enabled = originalIndex > 0) { Icon(Icons.Rounded.ArrowUpward, "上移") }
                        IconButton(onClick = { vm.movePriority(kind, packageName, 1, visibleRanked) }, enabled = originalIndex < visibleRanked.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "下移") }
                        IconButton(onClick = { vm.removePriority(kind, packageName) }) { Icon(Icons.Rounded.Close, "取消优先") }
                    }
                    if (expanded) {
                        components.forEach { comp ->
                            ComponentInfoRow(comp)
                        }
                    }
                }
            }
            
            item { 
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("可添加应用", fontWeight = FontWeight.Bold) 
            }
            
            items(available, key = { "available|$it" }) { packageName ->
                val components = apps.getValue(packageName)
                val key = "available|$packageName"
                val expanded = expandedKey == key
                
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expandedKey = if (expanded) null else key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(components.first().appIcon, components.first().appLabel)
                        Spacer(Modifier.width(8.dp))
                        Text(components.first().appLabel, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.pinApp(kind, packageName) }, enabled = rankedRaw.size < 200) { Text("加入优先") }
                    }
                    if (expanded) {
                        components.forEach { comp ->
                            ComponentInfoRow(comp)
                        }
                    }
                }
            }
            if (available.isEmpty() && state.query.isNotBlank()) item { Text("没有匹配的待添加应用", Modifier.padding(vertical = 12.dp)) }
        }
    }
}

@Composable
private fun ComponentInfoRow(item: ComponentCandidate) {
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
