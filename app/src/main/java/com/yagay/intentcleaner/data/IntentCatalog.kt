package com.yagay.intentcleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.yagay.intentcleaner.domain.ComponentCandidate
import com.yagay.intentcleaner.domain.ComponentRule
import com.yagay.intentcleaner.domain.IntentKind
import com.yagay.intentcleaner.domain.intentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Evidence-based discovery, not a claim to enumerate every installed intent filter. */
class IntentCatalog(private val context: Context) {
    @Serializable private data class KnownCandidate(val rule: ComponentRule, val label: String, val activity: String,
        val advanced: Boolean, val evidence: List<String>, val broadMatch: Boolean = false,
        val lastSeenMillis: Long = 0L)
    private val cachePrefs = context.getSharedPreferences("catalog_cache", Context.MODE_PRIVATE)
    private val cacheJson = Json { ignoreUnknownKeys = true }

    suspend fun loadKnown(): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        try {
            val encoded = cachePrefs.getString("entries", null) ?: return@withContext emptyList()
            check(encoded.length <= 2_000_000) { "缓存超出大小限制" }
            cacheJson.decodeFromString(ListSerializer(KnownCandidate.serializer()), encoded).take(5_000)
                .filter { it.rule.isValid() }.map {
                    ComponentCandidate(it.rule, it.label, it.activity,
                        loadAppIcon(it.rule.packageName) { context.packageManager.getApplicationIcon(it.rule.packageName) },
                        it.evidence, it.advanced, unavailable = true, broadMatch = it.broadMatch,
                        lastSeenMillis = it.lastSeenMillis)
                }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            cacheStatus = "缓存读取失败：${failure.javaClass.simpleName}；规则不受影响"
            emptyList()
        }
    }

    suspend fun saveKnown(items: List<ComponentCandidate>, selected: Set<ComponentRule> = emptySet()) = withContext(Dispatchers.IO) {
        val selectedIds = selected.map { it.id }.toSet()
        var records = items.sortedWith(compareBy<ComponentCandidate> { it.rule.id !in selectedIds }
            .thenBy { it.unavailable }.thenByDescending { it.lastSeenMillis }).take(5_000)
            .map { KnownCandidate(it.rule, it.appLabel.take(256), it.activityLabel.take(256), it.advanced,
                it.evidence.take(3).map { evidence -> evidence.take(512) }, it.broadMatch, it.lastSeenMillis) }
        var encoded = cacheJson.encodeToString(ListSerializer(KnownCandidate.serializer()), records)
        while (encoded.length > 2_000_000 && records.isNotEmpty()) {
            records = records.take(records.size * 3 / 4)
            encoded = cacheJson.encodeToString(ListSerializer(KnownCandidate.serializer()), records)
        }
        cacheStatus = if (cachePrefs.edit().putString("entries", encoded).commit())
            "缓存保存 ${records.size}/${items.size}；裁剪 ${items.size - records.size}；规则独立保存"
        else "缓存保存失败；规则不受影响"
    }

    suspend fun completeConfigured(items: List<ComponentCandidate>, selected: Set<ComponentRule>): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        val known = items.map { it.rule.id }.toSet()
        items + selected.filter { it.id !in known }.map { rule ->
            val label = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(rule.packageName, 0).loadLabel(context.packageManager).toString()
            }.getOrDefault(rule.packageName)
            ComponentCandidate(rule, label, rule.className.substringAfterLast('.'),
                loadAppIcon(rule.packageName) { context.packageManager.getApplicationIcon(rule.packageName) },
                evidence = listOf("已配置；等待重新确认匹配"), unavailable = true)
        }
    }
    private val appIconCache = LruCache<String, Bitmap>(128)
    @Volatile var lastReport: String = "Not scanned"
        private set
    @Volatile var lastFileReport: String = "No real-file probe"
        private set
    @Volatile var cacheStatus: String = "Not loaded"
        private set
    @Volatile var scanWarning: String? = null
        private set

    private data class Probe(val intent: Intent, val advanced: Boolean, val label: String)
    private data class QueryResult(val candidates: List<ComponentCandidate>, val raw: Int, val flags: Int = 0)

    suspend fun scan(): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        appIconCache.evictAll()
        val found = mutableListOf<ComponentCandidate>()
        val known = mutableSetOf<String>()
        val report = StringBuilder("startedAt=${Instant.now()}\nmanagerUid=${android.os.Process.myUid()}\n")
        // Compare discovery against menu-style queries without launching any activity.
        for (scheme in listOf("http", "https")) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching {
                @Suppress("DEPRECATION")
                val menu = context.packageManager.queryIntentActivities(web, PackageManager.MATCH_DEFAULT_ONLY)
                @Suppress("DEPRECATION")
                val resolved = context.packageManager.resolveActivity(web, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                report.appendLine("browserBaseline scheme=$scheme flags=0x${PackageManager.MATCH_DEFAULT_ONLY.toString(16)} count=${menu.size} resolved=${resolved?.packageName}/${resolved?.name}")
            }.onFailure { report.appendLine("browserBaseline scheme=$scheme error=${it.javaClass.simpleName}") }
        }
        var failures = 0
        scanWarning = null
        for (probe in probes()) {
            currentCoroutineContext().ensureActive()
            try {
                val result = query(probe)
                val added = result.candidates.count { known.add(it.rule.id) }
                found += result.candidates
                report.appendLine("${probe.label} broad=${probe.advanced} flags=0x${result.flags.toString(16)} raw=${result.raw} kept=${result.candidates.size} new=$added")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures++
                report.appendLine("${probe.label} ERROR=${failure.javaClass.name}")
            }
        }
        val result = merge(found)
        report.appendLine("finishedAt=${Instant.now()} unique=${result.size} failures=$failures")
        lastReport = report.toString()
        if (failures > 0) scanWarning = "部分扫描失败（$failures 项）；成功结果已更新，缺失项仅作历史记录"
        result
    }

    /** Query only; does not launch a target or read file contents, and never logs its URI. */
    suspend fun inspectFile(uri: Uri): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            require(intent.intentKind() == IntentKind.OPEN) { "无法确认文件类型，未归入打开方式" }
            val label = "实际文件检查 scheme=${uri.scheme} mime=$mime"
            val result = query(Probe(intent, false, label), discovery = false)
            lastFileReport = "at=${Instant.now()} $label flags=0x${result.flags.toString(16)} raw=${result.raw} kept=${result.candidates.size}"
            result.candidates
        } catch (failure: Exception) {
            lastFileReport = "at=${Instant.now()} scheme=${uri.scheme} error=${failure.javaClass.name}"
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun query(probe: Probe, discovery: Boolean = true): QueryResult {
        val kind = probe.intent.intentKind() ?: return QueryResult(emptyList(), 0)
        val flags = queryFlags(kind, discovery)
        val raw = context.packageManager.queryIntentActivities(probe.intent, flags)
        val candidates = raw.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val rule = ComponentRule(kind, activity.packageName, activity.name)
            if (!rule.isValid()) return@mapNotNull null
            val reasons = buildList {
                if (!activity.exported) add("未导出")
                if (!activity.enabled || !activity.applicationInfo.enabled) add("未启用")
                activity.permission?.let {
                    if (context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) add("调用权限受限")
                }
            }
            // Keep restricted matches as advanced evidence, never advertise them as ordinary targets.
            val advanced = reasons.isNotEmpty()
            ComponentCandidate(
                rule,
                runCatching { activity.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault(activity.packageName),
                runCatching { info.loadLabel(context.packageManager).toString() }.getOrDefault(activity.name.substringAfterLast('.')),
                loadAppIcon(activity.packageName) {
                    runCatching { activity.applicationInfo.loadIcon(context.packageManager) }.getOrNull()
                        ?: context.packageManager.defaultActivityIcon
                },
                evidence = listOf(probe.label + if (reasons.isEmpty()) "" else " [" + reasons.joinToString() + "]"),
                advanced = advanced, broadMatch = probe.advanced,
                lastSeenMillis = System.currentTimeMillis()
            )
        }
        return QueryResult(candidates, raw.size, flags)
    }

    private fun loadAppIcon(packageName: String, loader: () -> Drawable): Bitmap? {
        appIconCache.get(packageName)?.let { return it }
        return runCatching { loader().toBitmap(width = 96, height = 96) }.getOrNull()?.also {
            appIconCache.put(packageName, it)
        }
    }

    private fun probes(): List<Probe> = buildList {
        for ((mime, file) in FILE_TYPES) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                add(Probe(Intent(action).setType(mime), false, "${action.substringAfterLast('.')} mime=$mime"))
            }
            for (scheme in listOf("content", "file", "https")) {
                val uri = when (scheme) {
                    "content" -> "content://com.yagay.intentcleaner.placeholder/$file"
                    "file" -> "file:///storage/emulated/0/Download/$file"
                    else -> "https://example.com/$file"
                }
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
                add(Probe(intent, false, "VIEW scheme=$scheme mime=$mime sample=$file"))
            }
        }
        for (scheme in listOf("http", "https")) {
            add(Probe(Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE), false, "网页 scheme=$scheme"))
        }
        add(Probe(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), false, "PROCESS_TEXT mime=text/plain"))
        for (mime in listOf("*/*", "image/*", "video/*", "audio/*")) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                add(Probe(Intent(action).setType(mime), true, "宽泛 ${action.substringAfterLast('.')} mime=$mime"))
            }
            for (scheme in listOf("content", "file")) {
                val uri = if (scheme == "content") "content://com.yagay.intentcleaner.placeholder/item" else "file:///item"
                add(Probe(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime), true,
                    "宽泛 VIEW scheme=$scheme mime=$mime"))
            }
        }
    }

    companion object {
        fun queryFlags(kind: IntentKind, discovery: Boolean): Int =
            (if (discovery) PackageManager.MATCH_ALL else 0) or
                (if (kind == IntentKind.PROCESS_TEXT) 0 else PackageManager.MATCH_DEFAULT_ONLY)

        fun mergeSnapshot(previous: List<ComponentCandidate>, fresh: List<ComponentCandidate>,
            selected: Set<ComponentRule> = emptySet(), now: Long = System.currentTimeMillis()): List<ComponentCandidate> {
            val freshIds = fresh.map { it.rule.id }.toSet()
            val selectedIds = selected.map { it.id }.toSet()
            return merge(fresh + previous.filter { it.rule.id !in freshIds &&
                (it.rule.id in selectedIds || it.lastSeenMillis == 0L || now - it.lastSeenMillis < 7 * 86_400_000L) }
                .map { it.copy(unavailable = true, lastSeenMillis = if (it.lastSeenMillis == 0L) now else it.lastSeenMillis) })
        }

        fun merge(items: List<ComponentCandidate>): List<ComponentCandidate> =
            items.groupBy { it.rule.id }.values.map { matches ->
                val first = matches.firstOrNull { !it.advanced && !it.unavailable } ?: matches.first()
                first.copy(evidence = matches.flatMap { it.evidence }.distinct()
                    .sortedBy { !it.startsWith("实际文件检查") }.take(32),
                    advanced = matches.all { it.advanced }, unavailable = matches.all { it.unavailable },
                    broadMatch = matches.all { it.broadMatch }, lastSeenMillis = matches.maxOf { it.lastSeenMillis })
            }.sortedWith(compareBy({ it.rule.kind.ordinal }, { it.appLabel.lowercase() }, { it.rule.id }))

        private val FILE_TYPES = listOf(
            "text/plain" to "sample.txt", "text/html" to "sample.html",
            "image/jpeg" to "sample.jpg", "image/png" to "sample.png",
            "video/mp4" to "sample.mp4", "audio/mpeg" to "sample.mp3",
            "application/pdf" to "sample.pdf", "application/zip" to "sample.zip",
            "application/json" to "sample.json", "application/epub+zip" to "sample.epub",
            "application/vnd.android.package-archive" to "sample.apk",
            "application/msword" to "sample.doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "sample.docx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "sample.xlsx"
        )
    }
}
