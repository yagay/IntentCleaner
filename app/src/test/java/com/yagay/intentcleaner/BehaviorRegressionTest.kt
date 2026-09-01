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
    @Test fun browserDiscoveryDoesNotUseMenuFlags() {
        val flags = com.yagay.intentcleaner.data.IntentCatalog.queryFlags(IntentKind.BROWSER, true)
        assertTrue(flags and android.content.pm.PackageManager.MATCH_ALL != 0)
        assertTrue(flags and android.content.pm.PackageManager.MATCH_DEFAULT_ONLY != 0)
        assertEquals(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            com.yagay.intentcleaner.data.IntentCatalog.queryFlags(IntentKind.OPEN, false))
        assertEquals(0, com.yagay.intentcleaner.data.IntentCatalog.queryFlags(IntentKind.PROCESS_TEXT, false))
    }

    @Test fun historyIsSeparateButSelectedRulesStayManageable() {
        val old = candidate(IntentKind.BROWSER).copy(unavailable = true)
        assertFalse(com.yagay.intentcleaner.ui.catalogVisible(old, emptySet(), false, false))
        assertTrue(com.yagay.intentcleaner.ui.catalogVisible(old, emptySet(), false, true))
        assertTrue(com.yagay.intentcleaner.ui.catalogVisible(old, setOf(old.rule), false, false))
    }

    @Test fun broadEvidenceDoesNotMeanRestricted() {
        val broad = candidate(IntentKind.OPEN).copy(broadMatch = true)
        assertTrue(com.yagay.intentcleaner.ui.catalogVisible(broad, emptySet(), false, false))
        assertFalse(com.yagay.intentcleaner.ui.catalogVisible(broad.copy(advanced = true), emptySet(), false, false))
    }

    @Test fun oldUnselectedHistoryExpiresButConfiguredHistoryDoesNot() {
        val old = candidate(IntentKind.OPEN).copy(lastSeenMillis = 1L)
        val now = 8 * 86_400_000L
        assertTrue(com.yagay.intentcleaner.data.IntentCatalog.mergeSnapshot(listOf(old), emptyList(), now = now).isEmpty())
        assertEquals(old.rule, com.yagay.intentcleaner.data.IntentCatalog.mergeSnapshot(listOf(old), emptyList(), setOf(old.rule), now).single().rule)
    }

    @Test fun cancellingARestrictedRuleHasTheSameVisibilityAfterRestart() {
        val item = candidate(IntentKind.OPEN).copy(advanced = true)
        assertTrue(com.yagay.intentcleaner.ui.catalogVisible(item, setOf(item.rule), false, false))
        assertFalse(com.yagay.intentcleaner.ui.catalogVisible(item, emptySet(), false, false))
        assertFalse(com.yagay.intentcleaner.ui.catalogVisible(item, emptySet(), false, false))
        assertTrue(com.yagay.intentcleaner.ui.catalogVisible(item, emptySet(), true, false))
    }

    @Test fun relativeStoredRuleUsesCanonicalIdentity() {
        assertEquals(ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open"),
            ComponentRule.fromId("OPEN|com.example|.Open"))
    }

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
