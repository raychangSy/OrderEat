package com.symmetry.ordereat.domain.usecase

import com.symmetry.ordereat.domain.model.OptionGroup
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.model.SelectionType

/**
 * 客製選項規則：
 * - 單選：必選 → 剛好 1 個；非必選 → 0 或 1 個
 * - 多選：必選＋N → 剛好 N 個；必選＋不限 → 至少 1 個；非必選＋N → 最多 N 個
 */
object OptionRules {

    fun error(group: OptionGroup, selectedCount: Int): String? = when (group.type) {
        SelectionType.SINGLE ->
            if (group.required && selectedCount != 1) "請選擇${group.name}" else null

        SelectionType.MULTI -> {
            val max = group.maxSelect
            when {
                group.required && max > 0 && selectedCount != max -> "${group.name}請選 $max 項（目前 $selectedCount 項）"
                group.required && max == 0 && selectedCount == 0 -> "請選擇${group.name}"
                max in 1..<selectedCount -> "${group.name}最多選 $max 項"
                else -> null
            }
        }
    }

    fun canSelectMore(group: OptionGroup, selectedCount: Int): Boolean =
        group.type == SelectionType.MULTI && (group.maxSelect == 0 || selectedCount < group.maxSelect)

    /** 選擇提示，例如「必選・剛好 2 項」 */
    fun hint(group: OptionGroup): String = when (group.type) {
        SelectionType.SINGLE -> if (group.required) "必選・單選" else "可不選・單選"
        SelectionType.MULTI -> {
            val max = group.maxSelect
            when {
                group.required && max > 0 -> "必選・剛好 $max 項"
                group.required -> "必選・可多選"
                max > 0 -> "可不選・最多 $max 項"
                else -> "可不選・可多選"
            }
        }
    }

    fun unitPrice(basePrice: Int, selections: List<SelectedOption>): Int = basePrice + selections.sumOf { it.price }

    /** 單選點擊：已選的再點一次 → 取消（必選則保持）；多選點擊：切換，超過上限時回傳 null 代表不允許 */
    fun toggle(group: OptionGroup, current: List<String>, choiceName: String): List<String>? = when (group.type) {
        SelectionType.SINGLE ->
            if (choiceName in current) {
                if (group.required) current else emptyList()
            } else {
                listOf(choiceName)
            }

        SelectionType.MULTI ->
            when {
                choiceName in current -> current - choiceName
                canSelectMore(group, current.size) -> current + choiceName
                else -> null
            }
    }
}
