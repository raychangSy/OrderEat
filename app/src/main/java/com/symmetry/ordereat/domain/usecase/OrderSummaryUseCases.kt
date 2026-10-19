package com.symmetry.ordereat.domain.usecase

import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import javax.inject.Inject

data class ItemSummary(
    val itemName: String,
    val optionsText: String,
    val quantity: Int,
    val amount: Int,
    /** 「Ann：不要蔥」 */
    val notes: List<String>,
)

data class MemberSummary(
    val memberName: String,
    val lines: List<OrderLine>,
    val total: Int,
)

data class OrderSummary(
    val byItem: List<ItemSummary>,
    val byMember: List<MemberSummary>,
    val total: Int,
    val itemCount: Int,
)

class BuildOrderSummaryUseCase @Inject constructor() {
    operator fun invoke(lines: List<OrderLine>): OrderSummary {
        val sorted = lines.sortedBy { it.createdAt }
        val byItem = sorted
            .groupBy { it.itemName to it.optionsText }
            .map { (key, group) ->
                ItemSummary(
                    itemName = key.first,
                    optionsText = key.second,
                    quantity = group.sumOf { it.quantity },
                    amount = group.sumOf { it.subtotal },
                    notes = group.filter { it.note.isNotBlank() }.map { "${it.memberName}：${it.note}" },
                )
            }
        val byMember = sorted
            .groupBy { it.memberName }
            .map { (name, group) -> MemberSummary(name, group, group.sumOf { it.subtotal }) }
        return OrderSummary(
            byItem = byItem,
            byMember = byMember,
            total = sorted.sumOf { it.subtotal },
            itemCount = sorted.sumOf { it.quantity },
        )
    }
}

class FindMissingMembersUseCase @Inject constructor() {
    /** 啟用中、但這團還沒點任何東西的成員 */
    operator fun invoke(members: List<Member>, lines: List<OrderLine>): List<Member> {
        val orderedIds = lines.mapNotNull { it.memberId }.toSet()
        return members.filter { it.active && it.id !in orderedIds }
    }
}

class FormatOrderTextUseCase @Inject constructor() {
    /** 給 LINE／打電話用的純文字 */
    operator fun invoke(session: OrderSession, summary: OrderSummary): String = buildString {
        appendLine("【${session.mealType}】${session.restaurantName}　${session.date}")
        appendLine()
        summary.byItem.forEach { item ->
            val options = if (item.optionsText.isBlank()) "" else "（${item.optionsText}）"
            appendLine("${item.itemName}$options ×${item.quantity}")
            item.notes.forEach { appendLine("　└ $it") }
        }
        appendLine()
        appendLine("共 ${summary.itemCount} 份，總計 $${summary.total}")
        appendLine()
        appendLine("── 每人應付 ──")
        summary.byMember.forEach { appendLine("${it.memberName}　$${it.total}") }
        if (session.note.isNotBlank()) {
            appendLine()
            appendLine("備註：${session.note}")
        }
    }.trimEnd()
}
