package com.yagay.intentcleaner

import com.yagay.intentcleaner.data.readBackupText
import com.yagay.intentcleaner.domain.ComponentCandidate
import com.yagay.intentcleaner.domain.ComponentRule
import com.yagay.intentcleaner.domain.IntentKind
import com.yagay.intentcleaner.domain.RuleBackup
import com.yagay.intentcleaner.ui.groupCandidates
import com.yagay.intentcleaner.ui.UiFilter
import com.yagay.intentcleaner.ui.retainConfiguredCandidates
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class BehaviorRegressionTest {
    @Test fun selectingInAllViewDoesNotRemoveAnApp() {
        val item = candidate(IntentKind.BROWSER)
        assertEquals(groupCandidates(listOf(item), emptySet(), IntentKind.BROWSER, "", UiFilter.ALL),
            groupCandidates(listOf(item), setOf(item.rule), IntentKind.BROWSER, "", UiFilter.ALL))
    }

    @Test fun missingFreshMatchRetainsLabelAndRule() {
        val item = candidate(IntentKind.BROWSER, label = "Browser name")
        val merged = com.yagay.intentcleaner.data.IntentCatalog.mergeSnapshot(listOf(item), emptyList())
        assertEquals(item.rule, merged.single().rule)
        assertEquals("Browser name", merged.single().appLabel)
        assertTrue(merged.single().unavailable)
        assertEquals(1, groupCandidates(merged, emptySet(), IntentKind.BROWSER, "Browser name", UiFilter.ALL).size)
    }

    @Test fun freshMatchReplacesHistoricalMetadata() {
        val old = candidate(IntentKind.OPEN).copy(unavailable = true)
        val fresh = old.copy(appLabel = "Updated", unavailable = false)
        val merged = com.yagay.intentcleaner.data.IntentCatalog.mergeSnapshot(listOf(old), listOf(fresh))
        assertEquals(listOf(fresh), merged)
    }

    private fun candidate(kind: IntentKind, pkg: String = "com.example", label: String = "Example") =
        ComponentCandidate(ComponentRule(kind, pkg, "$pkg.${kind.name}Activity"), label, "Target")

    @Test fun categorySelectionDoesNotIncludeOtherKindsFromTheSameApp() {
        val share = candidate(IntentKind.SHARE)
        val browser = candidate(IntentKind.BROWSER)
        val groups = groupCandidates(listOf(browser, share), emptySet(), IntentKind.SHARE, "", UiFilter.ALL)
        assertEquals(listOf(share), groups.single().components)
    }

    @Test fun appAndCategoryOrderStayStable() {
        val share = candidate(IntentKind.SHARE)
        val browser = candidate(IntentKind.BROWSER)
        val other = candidate(IntentKind.OPEN, "com.other", "Alpha")
        val groups = groupCandidates(listOf(browser, other, share), emptySet(), null, "", UiFilter.ALL)
        assertEquals(listOf("com.other", "com.example"), groups.map { it.packageName })
        assertEquals(listOf(share, browser), groups.last().components)
    }

    @Test fun classNameSearchIsCaseInsensitiveAndStillRespectsCategory() {
        val share = candidate(IntentKind.SHARE)
        assertEquals(listOf(share), groupCandidates(listOf(share), emptySet(), null, "shareactivity", UiFilter.ALL).single().components)
        assertTrue(groupCandidates(listOf(share), emptySet(), IntentKind.OPEN, "shareactivity", UiFilter.ALL).isEmpty())
    }

    @Test fun missingSelectedRuleRemainsManageable() {
        val item = candidate(IntentKind.OPEN)
        val retained = retainConfiguredCandidates(emptyList(), setOf(item.rule))
        assertEquals(item.rule, retained.single().rule)
        assertTrue(retained.single().unavailable)
        assertEquals(1, retainConfiguredCandidates(listOf(item), setOf(item.rule)).size)
    }

    @Test fun backupReaderHandlesShortReadsWithoutLosingUnicodeOrNewlines() {
        val expected = "中文\n备份😀"
        val reader = object : Reader() {
            var position = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (position == expected.length) return -1
                buffer[offset] = expected[position++]
                return 1
            }
            override fun close() {}
        }
        assertEquals(expected, reader.use { it.readBackupText(expected.length) })
    }

    @Test fun backupReaderAllowsExactLimitAndEmptyInput() {
        assertEquals("abcd", StringReader("abcd").use { it.readBackupText(4) })
        assertEquals("", StringReader("").use { it.readBackupText(4) })
    }

    @Test fun oversizedBackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StringReader("abcde").use { it.readBackupText(4) }
        }
    }

    @Test fun existingBackupWithoutExplicitVersionRetainsWhitelist() {
        val original = """{"blacklist":false,"rules":[{"kind":"SHARE","packageName":"com.example","className":"com.example.Share"}]}"""
        val backup = Json.decodeFromString(RuleBackup.serializer(), original)
        assertEquals(1, backup.version)
        assertFalse(backup.blacklist)
        assertEquals(setOf(ComponentRule(IntentKind.SHARE, "com.example", "com.example.Share")), backup.rules)
        assertEquals(backup, Json.decodeFromString(RuleBackup.serializer(), Json.encodeToString(RuleBackup.serializer(), backup)))
    }
}
