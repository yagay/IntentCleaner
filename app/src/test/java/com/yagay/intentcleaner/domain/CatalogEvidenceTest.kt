package com.yagay.intentcleaner.domain

import com.yagay.intentcleaner.data.IntentCatalog
import org.junit.Assert.*
import org.junit.Test

class CatalogEvidenceTest {
    private val rule = ComponentRule(IntentKind.OPEN, "com.example", "com.example.Open")

    @Test fun ordinaryEvidencePromotesAnAdvancedOnlyCandidate() {
        val broad = ComponentCandidate(rule, "App", "Open", evidence = listOf("宽泛"), advanced = true)
        val ordinary = broad.copy(evidence = listOf("普通"), advanced = false)
        val merged = IntentCatalog.merge(listOf(broad, ordinary)).single()
        assertFalse(merged.advanced)
        assertEquals(listOf("宽泛", "普通"), merged.evidence)
    }

    @Test fun realFileEvidenceComesFirstAndDuplicatesAreRemoved() {
        val candidate = ComponentCandidate(rule, "App", "Open", evidence = listOf("普通", "实际文件检查 mime=application/pdf"))
        val merged = IntentCatalog.merge(listOf(candidate, candidate)).single()
        assertEquals(2, merged.evidence.size)
        assertTrue(merged.evidence.first().startsWith("实际文件检查"))
    }
}
