package com.yagay.intentcleaner.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Sort
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.intentcleaner.IntentCleanerApp
import com.yagay.intentcleaner.data.RuleRepository
import com.yagay.intentcleaner.data.IntentCatalog
import com.yagay.intentcleaner.data.ResolverScopeDetector
import com.yagay.intentcleaner.data.ScopeDetection
import com.yagay.intentcleaner.domain.ComponentCandidate
import com.yagay.intentcleaner.domain.ComponentRule
import com.yagay.intentcleaner.domain.IntentKind
import com.yagay.intentcleaner.domain.DisplayMode
import com.yagay.intentcleaner.domain.PriorityConfig
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RunningTargetStatus(val processName: String, val state: String, val version: Long)

data class ModuleStatus(
    val connected: Boolean = false,
    val apiVersion: Int? = null,
    val grantedScope: Set<String> = emptySet(),
    val runningTargets: List<RunningTargetStatus> = emptyList(),
    val detection: ScopeDetection = ScopeDetection(),
    val scopeKnown: Boolean = false,
    val requesting: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val missingScope: Set<String> get() = detection.recommended - grantedScope
    val extraScope: Set<String> get() = grantedScope - detection.hosts.map { it.packageName }.toSet()
    val resolverLoaded: Boolean get() = runningTargets.any { target ->
        target.state == "UP_TO_DATE" && detection.hosts.any { it.processName == target.processName }
    }
}

enum class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RULES("规则", Icons.Rounded.List),
    PRIORITY("排序", Icons.Rounded.Sort),
    DASHBOARD("状态", Icons.Rounded.Dashboard)
}

enum class UiFilter(val title: String) {
    ALL("显示全部"),
    HIDE_SELECTED("隐藏已选"),
    SHOW_SELECTED("只看已选")
}

data class MainState(
    val module: ModuleStatus = ModuleStatus(),
    val syncStatus: String = "已保存，等待连接",
    val loading: Boolean = true,
    val error: String? = null,
    val candidates: List<ComponentCandidate> = emptyList(),
    val selected: Set<ComponentRule> = emptySet(),
    val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val filter: IntentKind? = null,
    val query: String = "",
    val uiFilter: UiFilter = UiFilter.ALL,
    val diagnosticMode: Boolean = false,
    val priorities: PriorityConfig = PriorityConfig(),
    val groups: List<AppGroup> = emptyList(),
    val destination: Destination = Destination.RULES,
    val expandedAppKey: String? = null,
    val showAdvanced: Boolean = false
)

data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val components: List<ComponentCandidate>
)

fun groupCandidates(candidates: List<ComponentCandidate>, selected: Set<ComponentRule>, filter: IntentKind?, query: String, uiFilter: UiFilter): List<AppGroup> =
    candidates.groupBy { it.rule.packageName }.mapNotNull { (pkg, all) ->
        val matching = all.filter {
            val isSelected = it.rule in selected
            val matchesUiFilter = when (uiFilter) {
                UiFilter.ALL -> true
                UiFilter.HIDE_SELECTED -> !isSelected
                UiFilter.SHOW_SELECTED -> isSelected
            }
            matchesUiFilter && (filter == null || it.rule.kind == filter) &&
                (query.isBlank() || it.appLabel.contains(query, true) ||
                    it.activityLabel.contains(query, true) || it.rule.packageName.contains(query, true) ||
                    it.rule.className.contains(query, true))
        }.sortedBy { it.rule.kind.ordinal }
        if (matching.isEmpty()) null else AppGroup(pkg, all.first().appLabel, all.first().appIcon,
            matching)
    }.sortedBy { it.appLabel.lowercase() }

/** A rule must remain manageable even when a probe, permission or package update hides its target. */
fun retainConfiguredCandidates(items: List<ComponentCandidate>, selected: Set<ComponentRule>): List<ComponentCandidate> {
    val ids = items.map { it.rule.id }.toSet()
    return items + selected.filter { it.id !in ids }.map { rule ->
        ComponentCandidate(rule, rule.packageName, rule.className.substringAfterLast('.'),
            evidence = listOf("已配置，但本次未扫描到；不代表已卸载"), unavailable = true)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IntentCleanerApp
    private val candidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    private val showAdvanced = MutableStateFlow(false)
    private val mutableFileCheckStatus = MutableStateFlow<String?>(null)
    val fileCheckStatus: StateFlow<String?> = mutableFileCheckStatus
    fun setShowAdvanced(value: Boolean) { showAdvanced.value = value }

    fun inspectFile(uri: Uri) {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            mutableFileCheckStatus.value = "正在检查实际文件的候选，不会打开文件…"
            error.value = null
            try {
                val found = app.catalog.inspectFile(uri)
                if (generation == refreshGeneration) {
                    candidates.value = IntentCatalog.merge(candidates.value + found)
                    mutableFileCheckStatus.value = "本次实际文件查询返回 ${found.size} 个组件，已合并并标注匹配依据"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == refreshGeneration) mutableFileCheckStatus.value = failure.message ?: "文件检查失败"
            } finally {
                if (generation == refreshGeneration) loading.value = false
            }
        }
    }
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow<IntentKind?>(null)
    private val query = MutableStateFlow("")
    private val uiFilter = MutableStateFlow(UiFilter.ALL)
    private val moduleStatus = MutableStateFlow(ModuleStatus())
    private val destination = MutableStateFlow(Destination.RULES)
    private val expandedAppKey = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var statusGeneration = 0L
    private var scopeRequestInFlight = false
    private val scopeDetector = ResolverScopeDetector(application)
    private val mutableCollectingDiagnostics = MutableStateFlow(false)
    val collectingDiagnostics: StateFlow<Boolean> = mutableCollectingDiagnostics
    private val mutableExportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = mutableExportMessage

    fun clearExportMessage() { mutableExportMessage.value = null }

    fun exportDiagnostics(uri: Uri) {
        if (mutableCollectingDiagnostics.value) return
        mutableCollectingDiagnostics.value = true
        viewModelScope.launch {
            var report: File? = null
            try {
                val freshStatus = readModuleStatus(app.service.value)
                val config = app.rules.remoteSnapshot()
                report = DiagnosticCollector.collect(app, state.value.copy(
                    module = freshStatus, selected = config.rules, displayMode = config.mode,
                    priorities = config.priorities, diagnosticMode = config.diagnostic,
                    syncStatus = app.syncStatus.value
                ))
                val ready = requireNotNull(report)
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("无法创建诊断包")
                    output.use { destination -> ready.inputStream().use { it.copyTo(destination) } }
                }
                mutableExportMessage.value = "诊断包已导出"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableExportMessage.value = "导出失败：${failure.message}；目标文件可能不完整，请重新导出"
            } finally {
                report?.delete()
                mutableCollectingDiagnostics.value = false
            }
        }
    }

    private data class ListContent(val candidates: List<ComponentCandidate>, val filter: IntentKind?, val query: String, val groups: List<AppGroup>, val advanced: Boolean = false)

    private val catalogView = combine(candidates, showAdvanced) { items, advanced -> items to advanced }
    private val grouped = combine(catalogView, app.rules.rules, filter, query, uiFilter) { view, selected, kind, text, ui ->
        val items = retainConfiguredCandidates(view.first, selected)
        val visible = items.filter { view.second || !it.advanced || it.rule in selected }
        ListContent(items, kind, text, groupCandidates(visible, selected, kind, text, ui), view.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListContent(emptyList(), null, "", emptyList()))
    
    val state: StateFlow<MainState> = combine(
        moduleStatus, loading, error, grouped, app.rules.rules, app.rules.displayMode, app.rules.priorities, app.rules.diagnosticMode, app.syncStatus, destination, expandedAppKey, uiFilter
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MainState(
            module = values[0] as ModuleStatus,
            loading = values[1] as Boolean,
            error = values[2] as String?,
            candidates = (values[3] as ListContent).candidates,
            selected = values[4] as Set<ComponentRule>,
            displayMode = values[5] as DisplayMode,
            filter = (values[3] as ListContent).filter,
            query = (values[3] as ListContent).query,
            showAdvanced = (values[3] as ListContent).advanced,
            priorities = values[6] as PriorityConfig,
            groups = (values[3] as ListContent).groups,
            diagnosticMode = values[7] as Boolean,
            syncStatus = values[8] as String,
            destination = values[9] as Destination,
            expandedAppKey = values[10] as String?,
            uiFilter = values[11] as UiFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainState())

    init {
        refresh()
        viewModelScope.launch {
            app.service.collectLatest { readModuleStatus(it) }
        }
    }

    fun setDiagnosticMode(enabled: Boolean) {
        app.rules.setDiagnosticMode(enabled)
        refreshModuleStatus()
    }

    fun refresh() {
        mutableFileCheckStatus.value = null
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val result = app.catalog.scan()
                if (generation == refreshGeneration) candidates.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == refreshGeneration) error.value = failure.message ?: "扫描失败"
            } finally {
                if (generation == refreshGeneration) loading.value = false
            }
        }
        refreshModuleStatus()
    }

    fun refreshModuleStatus() {
        viewModelScope.launch { readModuleStatus(app.service.value) }
    }

    private suspend fun readModuleStatus(service: XposedService?): ModuleStatus {
        val generation = ++statusGeneration
        val status = withContext(Dispatchers.IO) {
            val detection = scopeDetector.detect()
            var result = ModuleStatus(connected = service != null, detection = detection)
            if (service != null) {
                try {
                    result = result.copy(apiVersion = service.apiVersion)
                    result = result.copy(grantedScope = service.scope.toSet(), scopeKnown = true)
                    result = if ((result.apiVersion ?: 0) >= 102) {
                        result.copy(runningTargets = service.runningTargets.map {
                            RunningTargetStatus(it.processName, it.state.name, it.loadedVersionCode)
                        })
                    } else result.copy(error = "当前框架不支持运行目标检测")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    result = result.copy(error = failure.message ?: "无法读取 LSPosed 模块状态")
                }
            }
            result
        }
        if (generation == statusGeneration && app.service.value === service) {
            moduleStatus.value = status.copy(requesting = scopeRequestInFlight)
        }
        return status
    }

    fun toggle(rule: ComponentRule) = app.rules.toggle(rule)
    fun setGroupSelected(group: AppGroup, selected: Boolean) = app.rules.setSelected(group.components.map { it.rule }, selected)
    fun setDisplayMode(value: DisplayMode) = app.rules.setDisplayMode(value)
    fun setFilter(value: IntentKind?) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setUiFilter(value: UiFilter) { uiFilter.value = value }
    fun setDestination(value: Destination) { destination.value = value }
    fun toggleExpandedApp(key: String) { expandedAppKey.value = if (expandedAppKey.value == key) null else key }
    fun exportJson(): String = app.rules.exportJson()
    fun importJson(content: String) = app.rules.importJson(content)

    fun pinApp(kind: IntentKind, packageName: String) {
        val current = app.rules.priorities.value.apps[kind].orEmpty()
        if (packageName !in current && current.size < 200) app.rules.setPriority(kind, current + packageName)
    }

    fun removePriority(kind: IntentKind, packageName: String) {
        app.rules.setPriority(kind, app.rules.priorities.value.apps[kind].orEmpty() - packageName)
    }

    fun movePriority(kind: IntentKind, packageName: String, offset: Int) {
        val current = app.rules.priorities.value.apps[kind].orEmpty().toMutableList()
        val index = current.indexOf(packageName)
        val destination = index + offset
        if (index < 0 || destination !in current.indices) return
        current.removeAt(index)
        current.add(destination, packageName)
        app.rules.setPriority(kind, current)
    }

    fun resetPriority(kind: IntentKind) = app.rules.setPriority(kind, emptyList())

    fun requestScope() {
        if (scopeRequestInFlight) return
        scopeRequestInFlight = true
        moduleStatus.value = moduleStatus.value.copy(requesting = true, message = null)
        viewModelScope.launch {
            try {
                val service = app.service.value ?: error("未连接 LSPosed，请先启用模块")
                val current = readModuleStatus(service)
                check(app.service.value === service) { "服务连接已变化，请重新检查" }
                check(current.scopeKnown) { current.error ?: "无法读取已授权作用域，未提交申请" }
                check(current.detection.recommended.isNotEmpty()) { "未确认可自动申请的选择器宿主，请查看检测详情并在 LSPosed 中手动核查" }
                val missing = current.missingScope.toList()
                if (missing.isEmpty()) {
                    moduleStatus.value = current.copy(requesting = true, message = "检测到的推荐作用域均已授权，无需重复申请")
                    return@launch
                }
                val approved = withTimeoutOrNull(120_000) {
                    suspendCancellableCoroutine<List<String>> { continuation ->
                        service.requestScope(missing, object : XposedService.OnScopeEventListener {
                            override fun onScopeRequestApproved(approved: List<String>) {
                                if (continuation.isActive) continuation.resume(approved)
                            }
                            override fun onScopeRequestFailed(message: String) {
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                            }
                        })
                    }
                }
                check(app.service.value === service) { "服务连接已变化，请重新检查授权结果" }
                val refreshed = readModuleStatus(service)
                check(app.service.value === service) { "服务连接已变化，请重新检查授权结果" }
                val message = when {
                    approved == null -> "等待授权超时，框架申请可能仍在处理；请先在 LSPosed 查看结果"
                    !refreshed.scopeKnown -> "框架已返回，暂时无法核实授权结果，请重新检查"
                    refreshed.missingScope.isNotEmpty() -> "仍缺少作用域：${refreshed.missingScope.joinToString()}"
                    else -> "推荐作用域已确认授权；若目标尚未加载，请重新启动相关选择器，必要时重启设备"
                }
                moduleStatus.value = refreshed.copy(message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                moduleStatus.value = moduleStatus.value.copy(error = failure.message ?: "申请作用域失败")
            } finally {
                scopeRequestInFlight = false
                moduleStatus.value = moduleStatus.value.copy(requesting = false)
            }
        }
    }

    companion object {
        const val MAX_BACKUP_CHARS = RuleRepository.MAX_BACKUP_CHARS
    }
}
