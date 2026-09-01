package com.yagay.intentcleaner

import android.app.Application
import android.content.Intent
import com.yagay.intentcleaner.data.IntentCatalog
import com.yagay.intentcleaner.data.RuleRepository
import com.yagay.intentcleaner.domain.ModuleConfig
import com.yagay.intentcleaner.domain.DisplayMode
import com.yagay.intentcleaner.domain.PriorityConfig
import com.yagay.intentcleaner.domain.RuntimeProtocol
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

data class RuntimeStatus(
    val ready: Boolean = false,
    val needsDecision: Boolean = false,
    val message: String = "等待核实运行模块与配置",
    val digest: String = ""
)

class IntentCleanerApp : Application(), XposedServiceHelper.OnServiceListener {
    lateinit var rules: RuleRepository; private set
    lateinit var catalog: IntentCatalog; private set
    val service = MutableStateFlow<XposedService?>(null)
    val syncStatus = MutableStateFlow("等待连接")
    val runtime = MutableStateFlow(RuntimeStatus())
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var pendingRecovery: ModuleConfig? = null

    override fun onCreate() {
        super.onCreate()
        rules = RuleRepository(this)
        catalog = IntentCatalog(this)
        XposedServiceHelper.registerListener(this)
        applicationScope.launch {
            combine(rules.revision, service) { _, _ -> Unit }.collect { synchronize() }
        }
    }

    override fun onServiceBind(service: XposedService) { this.service.value = service }
    override fun onServiceDied(service: XposedService) {
        if (this.service.value === service) {
            this.service.value = null
            publish(RuntimeStatus(message = "连接已断开；暂停扫描，保留当前列表"))
        }
    }

    private fun publish(status: RuntimeStatus): Boolean {
        runtime.value = status
        syncStatus.value = status.message
        return status.ready
    }

    /** Serialized bootstrap/sync/probe, also used before every catalog query batch. */
    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val bound = service.value ?: return@withLock publish(RuntimeStatus(message = "未连接 LSPosed；暂停扫描，保留当前列表"))
                val prefs = bound.getRemotePreferences(RuleRepository.REMOTE_PREFS)
                if (!rules.hasLocalConfiguration()) {
                    val encoded = prefs.getString(RuleRepository.KEY_CONFIG, null)
                    val remote = if (encoded != null) json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
                    else if (prefs.contains(RuleRepository.KEY_RULES)) {
                        ModuleConfig(prefs.getStringSet(RuleRepository.KEY_RULES, emptySet()).orEmpty()
                            .map { requireNotNull(com.yagay.intentcleaner.domain.ComponentRule.fromId(it)) { "远程旧规则损坏，请先从 JSON 备份恢复" } }.toSet(),
                            DisplayMode.fromStored(prefs.getString(RuleRepository.KEY_DISPLAY_MODE, null), prefs.getBoolean(RuleRepository.KEY_BLACKLIST, true)),
                            json.decodeFromString(PriorityConfig.serializer(), prefs.getString(RuleRepository.KEY_PRIORITIES, null) ?: "{}"),
                            prefs.getBoolean(RuleRepository.KEY_DIAGNOSTIC, false)).validated()
                    } else null
                    if (remote != null) {
                        pendingRecovery = remote
                        return@withLock publish(RuntimeStatus(needsDecision = true,
                            message = "本地配置缺失，远程配置仍存在（${remote.rules.size} 条规则）。请先选择恢复或重置；尚未覆盖远程配置。"))
                    }
                    rules.markInitialized()
                }
                pendingRecovery = null
                // Never send a new schema to old code or accept a stale scan as a fresh catalog.
                check(bound.apiVersion >= 102) { "框架需要 API 102；暂停同步和扫描" }
                val targets = bound.runningTargets
                val incompatible = targets.filter { !RuntimeProtocol.current(it.state.name, it.loadedVersionCode, BuildConfig.VERSION_CODE.toLong()) }
                check(incompatible.isEmpty()) {
                    "旧模块或异常运行状态：" + incompatible.joinToString { "${it.processName} ${it.state.name}/v${it.loadedVersionCode}" } +
                        "；请尝试热更新，旧版本不支持时完整重启手机。暂停同步和扫描。"
                }
                check(targets.any { it.processName == "system" }) { "未检测到 system 中的模块；请检查作用域并重启，暂不扫描" }
                val config = rules.remoteSnapshot()
                val encoded = json.encodeToString(ModuleConfig.serializer(), config)
                val digest = RuntimeProtocol.digest(encoded)
                if (runtime.value.digest != digest) publish(RuntimeStatus(message = "本地配置已保存，等待系统 Hook 确认新配置"))
                if (prefs.getString(RuleRepository.KEY_CONFIG, null) != encoded) {
                    check(prefs.edit().putString(RuleRepository.KEY_CONFIG, encoded).commit()) { "远程配置写入失败" }
                }
                // Read-back isn't hook acknowledgement. The private action is UID-authenticated
                // in system_server and returns the digest of the actual applied snapshot.
                var acknowledged = false
                repeat(4) {
                    if (!acknowledged) {
                        @Suppress("DEPRECATION")
                        val results = packageManager.queryIntentActivities(Intent(RuntimeProtocol.ACTION).setPackage(packageName), 0)
                        acknowledged = results.any { info ->
                            info.activityInfo?.packageName == packageName && info.activityInfo?.name == RuntimeProtocol.COMPONENT &&
                                info.nonLocalizedLabel?.toString() == "${BuildConfig.VERSION_CODE}:$digest"
                        }
                        if (!acknowledged) delay(150)
                    }
                }
                check(service.value === bound) { "连接已变化，请重试" }
                check(acknowledged) { "配置已写入，但系统 Hook 未确认接收；暂停扫描，请重试或重启" }
                check(rules.remoteSnapshot() == config) { "配置在确认期间发生变化，正在重新同步" }
                publish(RuntimeStatus(ready = true, message = "系统 Hook 已确认配置和管理查询豁免；选择器效果仍需实际验证", digest = digest))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                publish(RuntimeStatus(message = failure.message ?: "运行状态验证失败，保留当前列表"))
            }
        }
    }

    suspend fun resolveRecovery(restore: Boolean) {
        syncMutex.withLock {
            if (rules.hasLocalConfiguration()) return@withLock
            val remote = pendingRecovery ?: return@withLock
            rules.restoreRemote(if (restore) remote else ModuleConfig(emptySet(), DisplayMode.SHOW_ALL, PriorityConfig(), false))
            pendingRecovery = null
            publish(RuntimeStatus(message = if (restore) "已恢复本地配置，等待系统确认" else "已重置本地规则并暂停过滤，等待系统确认"))
        }
        synchronize()
    }
}
