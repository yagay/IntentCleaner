package com.yagay.intentcleaner.domain

import kotlinx.serialization.Serializable

/** One remote preference value prevents mixed old/new fields during backup restore. */
@Serializable
data class ModuleConfig(
    val rules: Set<ComponentRule>,
    val mode: DisplayMode,
    val priorities: PriorityConfig,
    val diagnostic: Boolean,
    // Supplied by our own app via framework-owned remote preferences, never by an Intent extra.
    val managerAppId: Int = -1,
    val tiles: TileConfig = TileConfig()
) {
    fun validated(): ModuleConfig {
        require(rules.size <= 20_000 && rules.all(ComponentRule::isValid))
        priorities.validated()
        tiles.validated()
        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))
        return this
    }
}
