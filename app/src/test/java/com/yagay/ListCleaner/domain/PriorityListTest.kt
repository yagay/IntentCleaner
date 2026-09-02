package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class PriorityListTest {
    private fun item(pkg: String, label: String = pkg, cls: String = "Activity", kind: IntentKind = IntentKind.SHARE) =
        ComponentCandidate(ComponentRule(kind, pkg, cls), label, cls)

    private fun groups(items: List<ComponentCandidate>, saved: List<String>, filter: PriorityListFilter = PriorityListFilter.ALL,
                       query: String = "", selected: Set<ComponentRule> = emptySet()) =
        priorityAppGroups(items, selected, DisplayMode.HIDE_SELECTED, IntentKind.SHARE, saved, query, filter)

    @Test fun allViewKeepsAlphabeticPositionsWhenCheckedOrUnchecked() {
        val items = listOf(item("b"), item("a"), item("c"))
        val expected = listOf("a", "b", "c")
        for (saved in listOf(emptyList(), listOf("c"), listOf("c", "a"))) {
            assertEquals(expected, groups(items, saved).map { it.packageName })
        }
        assertEquals(listOf(2, null, 1), groups(items, listOf("c", "a")).map { it.rank })
    }

    @Test fun selectedFollowsSavedOrderAndUnselectedRemainsAlphabetic() {
        val items = listOf(item("b"), item("a"), item("c"), item("d"))
        val saved = listOf("c", "a")
        assertEquals(listOf("c", "a"), groups(items, saved, PriorityListFilter.SELECTED).map { it.packageName })
        assertEquals(listOf("b", "d"), groups(items, saved, PriorityListFilter.UNSELECTED).map { it.packageName })
    }

    @Test fun filteringKeepsOnlyMatchingComponentsAndRespectsRuleCleanup() {
        val hidden = item("a", cls = "Hidden")
        val match = item("a", cls = "Match")
        val other = item("a", cls = "Other")
        val results = groups(listOf(hidden, match, other, item("b", kind = IntentKind.OPEN)),
            listOf("a"), query = "match", selected = setOf(hidden.rule))
        assertEquals(listOf(match), results.single().components)
        assertTrue(groups(listOf(hidden), listOf("a"), selected = setOf(hidden.rule)).isEmpty())
    }

    @Test fun hiddenSavedEntriesStayInPlaceWhenMovingSearchResults() {
        val saved = listOf("a", "hidden", "b", "c")
        val matches = groups(listOf(item("a", "Match A"), item("b", "Other"), item("c", "Match C")),
            saved, PriorityListFilter.SELECTED, query = "match")
        assertEquals(listOf(1, 3), matches.map { it.rank })
        assertEquals(listOf("c", "hidden", "b", "a"),
            moveVisiblePriority(saved, matches.map { it.packageName }, "c", -1))
        assertEquals(listOf("a", "hidden", "b", "c"), saved)
    }

    @Test fun missingRestrictedAndOtherCategoriesDoNotBecomePriorityOptions() {
        val items = listOf(item("a").copy(unavailable = true), item("b").copy(restricted = true),
            item("c", kind = IntentKind.OPEN))
        assertTrue(groups(items, listOf("a", "b", "c")).isEmpty())
    }
}
