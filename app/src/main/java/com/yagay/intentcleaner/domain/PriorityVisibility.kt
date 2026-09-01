package com.yagay.intentcleaner.domain

/** Same category and rule mode as filtering. A partially hidden app keeps surviving entries. */
fun priorityCandidates(
    candidates: List<ComponentCandidate>, selected: Set<ComponentRule>, mode: DisplayMode, kind: IntentKind
): List<ComponentCandidate> {
    val hasSelection = selected.any { it.kind == kind }
    return candidates.filter {
        it.rule.kind == kind && it.isCatalogCandidate && mode.includes(it.rule in selected, hasSelection)
    }
}

/** Swap visible neighbors while preserving hidden saved entries and their slots. */
fun moveVisiblePriority(current: List<String>, visible: List<String>, pkg: String, offset: Int): List<String> {
    if (offset != -1 && offset != 1) return current
    val orderedVisible = current.filter { it in visible }
    val from = orderedVisible.indexOf(pkg)
    val to = from + offset
    if (from < 0 || to !in orderedVisible.indices) return current
    val a = current.indexOf(pkg)
    val b = current.indexOf(orderedVisible[to])
    return current.toMutableList().apply { this[a] = current[b]; this[b] = current[a] }
}
