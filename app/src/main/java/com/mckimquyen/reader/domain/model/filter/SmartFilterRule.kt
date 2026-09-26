package com.mckimquyen.reader.domain.model.filter

import java.util.UUID

enum class FilterTargetField {
    TITLE,
    AUTHOR,
}

enum class FilterAction {
    MARK_READ,
    STAR,
}

data class SmartFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val targetField: FilterTargetField = FilterTargetField.TITLE,
    val keyword: String,
    val action: FilterAction = FilterAction.MARK_READ,
    val isEnabled: Boolean = true,
)
