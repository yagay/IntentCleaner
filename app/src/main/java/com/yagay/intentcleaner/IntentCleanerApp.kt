package com.yagay.intentcleaner

import android.app.Application
import android.util.Log
import com.yagay.intentcleaner.data.IntentCatalog
import com.yagay.intentcleaner.data.RuleRepository
import com.yagay.intentcleaner.domain.ModuleConfig
import kotlinx.serialization.json.Json
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IntentCleanerApp : Application(), XposedServiceHelper.OnServiceListener {
    lateinit var rules: RuleRepository; private set
    lateinit var catalog: IntentCatalog; private set
    val service = MutableStateFlow<XposedService?>(null)
    val syncStatus = MutableStateFlow("已保存，等待连接")
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        rules = RuleRepository(this)
        catalog = IntentCatalog(this)
        XposedServiceHelper.registerListener(this)
        applicationScope.launch {
            combine(rules.revision, service) { _, _ -> Unit }.collect { sync() }
        }
    }

    override fun onServiceBind(service: XposedService) {
        this.service.value = service
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service.value === service) {
            this.service.value = null
            syncStatus.value = "已保存，连接已断开"
        }
    }

    @Synchronized
    private fun sync() {
        val value = rules.remoteSnapshot()
        val boundService = service.value ?: run {
            syncStatus.value = "已保存，等待连接"
            return
        }
        runCatching {
            boundService.getRemotePreferences(RuleRepository.REMOTE_PREFS).edit()
                .putString(RuleRepository.KEY_CONFIG, Json.encodeToString(ModuleConfig.serializer(), value))
                .apply()
        }.onSuccess {
            if (service.value === boundService) syncStatus.value = "配置已提交，效果待验证"
        }.onFailure {
            if (service.value === boundService) syncStatus.value = "已保存，同步失败"
            Log.e(TAG, "Failed to synchronize rules with the Xposed service", it)
        }
    }

    private companion object {
        const val TAG = "Intentcleaner"
    }

}
