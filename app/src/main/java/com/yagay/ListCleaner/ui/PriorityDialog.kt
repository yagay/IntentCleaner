package com.yagay.ListCleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.priorityCandidates
import com.yagay.ListCleaner.domain.priorityAppGroups
import com.yagay.ListCleaner.domain.PriorityListFilter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriorityDialogContent(state: MainState, vm: MainViewModel) {
    var kind by rememberSaveable { mutableStateOf(state.filter ?: IntentKind.SHARE) }
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val rankedRaw = state.priorities.apps[kind].orEmpty()
    val groups = remember(state.candidates, state.selected, state.displayMode, kind, rankedRaw, state.query, viewFilter) {
        priorityAppGroups(state.candidates, state.selected, state.displayMode, kind, rankedRaw, state.query,
            when (viewFilter) {
                UiFilter.ALL -> PriorityListFilter.ALL
                UiFilter.HIDE_SELECTED -> PriorityListFilter.UNSELECTED
                UiFilter.SHOW_SELECTED -> PriorityListFilter.SELECTED
            })
    }
    // Search narrows the move targets too; hidden saved slots remain untouched.
    val moveTargets = groups.filter { it.rank != null }.sortedBy { it.rank }.map { it.packageName }
    val visibleSaved = priorityCandidates(state.candidates, state.selected, state.displayMode, kind)
        .map { it.rule.packageName }.toSet()
    val hiddenSavedCount = rankedRaw.count { it !in visibleSaved }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "status") {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("优先排序", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showInfo = !showInfo }) { Text("说明") }
            }
        }
        stickyHeader(key = "controls") {
            Surface(tonalElevation = 2.dp) {
                ListControls(state.copy(filter = kind, uiFilter = viewFilter),
                    onFilter = { entry -> if (entry != null) { kind = entry; expandedKey = null } },
                    onUiFilter = { viewFilter = it }, includeAllKinds = false,
                    viewTitle = { when (it) {
                        UiFilter.ALL -> "全部"
                        UiFilter.HIDE_SELECTED -> "未优先"
                        UiFilter.SHOW_SELECTED -> "已优先"
                    } })
            }
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("应用列表 · ${groups.size}", style = MaterialTheme.typography.labelLarge)
                Text("勾选加入当前分类的优先顺序，取消勾选移除；全部视图不会因勾选隐藏或移动应用。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已优先视图按设定顺序排列；展开应用可上移、下移。排序以应用为单位。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("已保存 ${rankedRaw.size}/200 项", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { vm.resetPriority(kind) }, enabled = rankedRaw.isNotEmpty()) { Text("恢复系统顺序") }
                }
                if (hiddenSavedCount > 0) Text("$hiddenSavedCount 项因已清理或本次未匹配而暂不显示，排序配置保留。",
                    style = MaterialTheme.typography.bodySmall)
                if (rankedRaw.size >= 200) Text("当前分类已达 200 项上限，取消部分优先后可继续添加。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (showInfo) Text("按分类保存。规则要求清理的组件不显示；应用仍有可见组件时保留。暂时隐藏的排序配置不删除，取消清理后恢复。分享适配推荐区及全部应用区，文本处理在查询出口排序；未知厂商菜单可能另行重排。联系人和调用方专属入口不调整。",
                    style = MaterialTheme.typography.bodySmall)
                val compatibility = when {
                    !state.module.connected -> "LSPosed 未连接：可以保存，但尚未生效。"
                    state.module.detection.hosts.isEmpty() -> "未确认选择器宿主，当前设备排序能力未知。"
                    state.module.detection.hosts.any { it.packageName != "system" && !it.className.startsWith("com.android.internal.app.") && !it.className.startsWith("com.android.intentresolver.") } ->
                        "厂商独立排序路径尚未适配；仅走 AOSP 路径时可能有效，不能保证置顶。"
                    else -> "已实现 AOSP 路径适配，未确认本机命中。保存后重新打开选择器核对。"
                }
                Text(if (!state.runtime.ready) state.runtime.message else if (kind == IntentKind.PROCESS_TEXT)
                    "文本候选按查询结果排序；来源应用若自行重排，仍可能不同。保存后重新打开文本菜单。" else compatibility,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.error != null) Text("本次刷新未完整完成，详情见状态页", color = MaterialTheme.colorScheme.error)
            }
        }
        groups.forEach { group ->
            val packageName = group.packageName
            val key = "${kind.name}|$packageName"
            val expanded = expandedKey == key
            val onExpand = { expandedKey = if (expanded) null else key }
            val first = group.components.first()
            item(key = "app|$key", contentType = "app") {
                Row(Modifier.fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                    .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = group.rank != null, enabled = group.rank != null || rankedRaw.size < 200,
                        onCheckedChange = { checked ->
                            if (checked) vm.pinApp(kind, packageName) else vm.removePriority(kind, packageName)
                        })
                    AppIcon(first.appIcon, first.appLabel)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text((group.rank?.let { "优先第 $it 位" } ?: "未优先") + " · ${group.components.size} 个组件",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onExpand) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) "折叠" else "展开")
                    }
                }
                HorizontalDivider()
            }
            if (expanded) {
                if (group.rank != null) item(key = "order|$key") {
                    val index = moveTargets.indexOf(packageName)
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("优先第 ${group.rank} 位", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { vm.movePriority(kind, packageName, -1, moveTargets) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)); Text("上移")
                        }
                        TextButton(onClick = { vm.movePriority(kind, packageName, 1, moveTargets) },
                            enabled = index >= 0 && index < moveTargets.lastIndex) {
                            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)); Text("下移")
                        }
                    }
                }
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { ComponentInfoRow(it) }
            }
        }
        if (!state.loading && groups.isEmpty()) item(key = "empty") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(when {
                    state.query.isNotBlank() -> "没有匹配的应用"
                    viewFilter == UiFilter.SHOW_SELECTED -> "当前没有可显示的优先应用"
                    viewFilter == UiFilter.HIDE_SELECTED -> "当前没有可显示的未优先应用"
                    else -> "当前分类没有可排序的应用"
                })
            }
        }
    }
}

@Composable
private fun ComponentInfoRow(item: ComponentCandidate) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .heightIn(min = 48.dp).padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(item.activityLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.rule.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
