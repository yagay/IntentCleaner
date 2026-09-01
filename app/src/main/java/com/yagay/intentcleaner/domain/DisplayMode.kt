package com.yagay.intentcleaner.domain

import kotlinx.serialization.Serializable

internal fun selectedKinds(ids: Set<String>): Set<IntentKind> =
    ids.mapNotNull { ComponentRule.fromId(it)?.kind }.toSet()

@Serializable
enum class DisplayMode(val title: String) {
    HIDE_SELECTED("隐藏选中"),
    SHOW_SELECTED("只显示选中"),
    SHOW_ALL("全部显示");

    fun includes(selected: Boolean, hasSelection: Boolean): Boolean =
        !hasSelection || when (this) {
            HIDE_SELECTED -> !selected
            SHOW_SELECTED -> selected
            SHOW_ALL -> true
        }

    companion object {
        fun fromStored(value: String?, blacklist: Boolean): DisplayMode =
            entries.find { it.name == value } ?: if (blacklist) HIDE_SELECTED else SHOW_SELECTED
    }
}
