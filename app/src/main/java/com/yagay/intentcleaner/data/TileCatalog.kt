package com.yagay.intentcleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.service.quicksettings.TileService
import androidx.core.graphics.drawable.toBitmap
import com.yagay.intentcleaner.domain.TilePolicy

data class TileCandidate(val spec: String, val label: String, val owner: String,
    val icon: Bitmap? = null, val system: Boolean = false)
data class TileScan(val items: List<TileCandidate>, val warning: String, val observedAtMillis: Long = 0)

/** Management discovery only; it never queries or modifies the active QS layout. */
class TileCatalog(private val context: Context) {
    @Suppress("DEPRECATION")
    fun scan(): TileScan {
        val pm = context.packageManager
        val items = linkedMapOf<String, TileCandidate>()
        val warnings = mutableListOf<String>()
        try {
            pm.queryIntentServices(Intent(TileService.ACTION_QS_TILE), 0).forEach { result ->
                val info = result.serviceInfo ?: return@forEach
                if (!info.exported || info.permission != "android.permission.BIND_QUICK_SETTINGS_TILE") return@forEach
                val spec = TilePolicy.canonical("custom(${ComponentName(info.packageName, info.name).flattenToString()})")
                    ?: return@forEach
                runCatching {
                    items[spec] = TileCandidate(spec, info.loadLabel(pm).toString(),
                        "${info.applicationInfo.loadLabel(pm)} · ${info.packageName}",
                        runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull())
                }.onFailure { warnings += "${info.packageName} 信息读取失败" }
            }
        } catch (failure: Exception) { warnings += "应用磁贴查询失败：${failure.javaClass.simpleName}" }
        try {
            val res = pm.getResourcesForApplication("com.android.systemui")
            val id = res.getIdentifier("quick_settings_tiles_stock", "string", "com.android.systemui")
            if (id == 0) warnings += "系统未公开内置磁贴目录" else {
                res.getString(id).split(',').forEach { raw ->
                    val spec = TilePolicy.canonical(raw) ?: return@forEach
                    if (spec == "default" || spec.startsWith("custom(") || spec in items) return@forEach
                    // Resource names are not standardized. Keep the actual spec as fallback.
                    val labelId = res.getIdentifier("quick_settings_${spec}_label", "string", "com.android.systemui")
                    val label = if (labelId == 0) spec else runCatching { res.getString(labelId) }.getOrDefault(spec)
                    items[spec] = TileCandidate(spec, label, "系统内置 · $spec", system = true)
                }
            }
        } catch (failure: Exception) { warnings += "内置目录读取失败：${failure.javaClass.simpleName}" }
        return TileScan(items.values.sortedWith(compareBy({ !it.system }, { it.owner }, { it.label })),
            (listOf("目录来自已安装磁贴服务与系统资源，不保证包含所有厂商磁贴，也不代表当前已固定状态。") + warnings).joinToString("\n"), System.currentTimeMillis())
    }
}
