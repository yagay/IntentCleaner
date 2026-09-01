package com.yagay.intentcleaner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.intentcleaner.domain.DisplayMode
import com.yagay.intentcleaner.domain.IntentKind
import com.yagay.intentcleaner.BuildConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel, onInspectFile: () -> Unit) {
    val fileCheckStatus by vm.fileCheckStatus.collectAsState()
    var showScopeDetails by remember { mutableStateOf(false) }
    if (showScopeDetails) {
        ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "module-indicator") {
            ModuleStatusRow(state) {
                showScopeDetails = true
                vm.refreshModuleStatus()
            }
        }
        stickyHeader(key = "list-controls") {
            Surface(tonalElevation = 2.dp) {
                ListControls(state, vm::setFilter, vm::setUiFilter)
            }
        }
        item(key = "list-summary") {
            SummaryRow(state)
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("显示高级候选", Modifier.weight(1f))
                Switch(state.showAdvanced, vm::setShowAdvanced)
            }
            Text("普通列表为样例匹配，不等于任意文件的真实菜单；高级候选含宽泛或权限受限入口。已配置规则始终保留管理入口（仍受视图过滤影响）。",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onInspectFile, enabled = !state.loading,
                modifier = Modifier.padding(horizontal = 16.dp)) { Text("用实际文件检查打开方式") }
            fileCheckStatus?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall) }
        }
        state.groups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(group, state.selected, expanded,
                    { vm.toggleExpandedApp(key) },
                    { selected -> vm.setGroupSelected(group, selected) })
            }
            if (expanded) {
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { component ->
                    ComponentRow(component, component.rule in state.selected) { vm.toggle(component.rule) }
                }
            }
        }
        if (!state.loading && state.groups.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("没有匹配的组件") } }
        }
    }
}

@Composable
private fun SummaryRow(state: MainState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("应用列表 · ${state.groups.size}", style = MaterialTheme.typography.labelLarge)
        Text("计数、标签和应用勾选均仅作用于当前可见匹配组件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            Text("过滤已暂停，勾选及排序保留", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else if (state.displayMode == DisplayMode.SHOW_SELECTED) {
            Text("未设置勾选的分类暂时显示全部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.filter == IntentKind.PROCESS_TEXT) {
            Text("过滤 PROCESS_TEXT 查询；不强制删除应用硬编码或缓存的菜单。该分类允许隐藏全部候选。", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PriorityTab(state: MainState, vm: MainViewModel) {
    PriorityDialogContent(state, vm)
}

@Composable
fun DashboardTabContent(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var showScopeDetails by remember { mutableStateOf(false) }
    if (showScopeDetails) ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("全局清理模式", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.displayMode.title, fontWeight = FontWeight.Bold)
                    Text("控制系统选择器如何处理已选组件。", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text("切换")
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.title) },
                                leadingIcon = { if (mode == state.displayMode) Icon(Icons.Rounded.Check, null) },
                                onClick = { menu = false; vm.setDisplayMode(mode) })
                        }
                    }
                }
            }
        }

        Text("同步状态", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(state.syncStatus, fontWeight = FontWeight.Bold)
                Text("规则变更会实时推送到选择器宿主。", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Text("数据备份", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("从 JSON 恢复") }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出为 JSON") }
        }

        Text("模块状态", style = MaterialTheme.typography.titleMedium)
        ModuleStatusRow(state) {
            showScopeDetails = true
            vm.refreshModuleStatus()
        }
        Text("加载状态：由 LSPosed 提供。Hook 安装与查询命中：当前界面未实时核验，请查看诊断包。更新源码后需要重启手机；规则修改无需重启。", style = MaterialTheme.typography.bodySmall)

        Text("诊断日志", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断模式", Modifier.weight(1f))
                    Switch(state.diagnosticMode, vm::setDiagnosticMode)
                }
                Text("开启后记录查询分类、规则与跳过原因；按分类和调用UID记录首个有效查询栈。管理扫描不占用调用栈记录。每进程每5秒关键记录最多200条，候选明细另限40条，不记录正文和完整URI。",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.diagnosticMode) Text("诊断已开启；请复现分享、打开或文本处理操作。", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onCollectDiagnostics,
                    enabled = !collectingDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (collectingDiagnostics) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (collectingDiagnostics) "正在收集" else "一键导出诊断包")
                }
                Text("先开启诊断，再复现问题，最后导出。包含最近24小时内最多12份 LSPosed 日志；大文件保留开头与最新结尾。系统原始日志可能包含隐私，分享前请检查。需要 Root 授权，读取失败会记录在包内。", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text("Intentcleaner v${BuildConfig.VERSION_NAME}", modifier = Modifier.align(Alignment.CenterHorizontally),
             style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
