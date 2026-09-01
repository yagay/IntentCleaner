package com.yagay.intentcleaner.domain

import kotlinx.serialization.Serializable

@Serializable
data class TileConfig(val enabled: Boolean = false, val hidden: Set<String> = emptySet()) {
    fun validated(): TileConfig {
        require(hidden.size <= 512 && hidden.all { TilePolicy.canonical(it) == it }) { "磁贴规则无效（最多512项）" }
        return this
    }
}
