package com.symmetry.ordereat.domain.model

import java.time.LocalDate

data class Member(
    val id: String,
    val name: String,
    val active: Boolean,
)

data class SelectedOption(
    val groupId: String,
    val groupName: String,
    val choiceName: String,
    val price: Int,
)

data class OrderLine(
    val id: String,
    val sessionId: String,
    /** 訪客為 null */
    val memberId: String?,
    val memberName: String,
    /** 手動輸入的品項為 null */
    val menuItemId: String?,
    val itemName: String,
    val selections: List<SelectedOption>,
    val quantity: Int,
    val unitPrice: Int,
    val note: String,
    val createdAt: Long,
    /** 別人用 Chrome 插件跟單的明細，手機上唯讀 */
    val fromExtension: Boolean = false,
) {
    val subtotal: Int get() = unitPrice * quantity

    /** 「少飯/燒腩+叉燒」：同一群組用 + 串，不同群組用 / 分隔 */
    val optionsText: String
        get() = selections.groupBy { it.groupId }.values
            .joinToString("/") { group -> group.joinToString("+") { it.choiceName } }
}

enum class SessionStatus {
    /** 點餐中，或送出後又修改、尚未重送 */
    ORDERING,

    /** 已按送出，等待上傳（離線或上傳失敗時會自動重試） */
    PENDING_UPLOAD,

    /** 已寫入 Google Sheet */
    SUBMITTED,
}

data class OrderSession(
    val id: String,
    val date: LocalDate,
    val mealType: String,
    val restaurantId: String?,
    val restaurantName: String,
    val note: String,
    val status: SessionStatus,
    val submittedAt: Long?,
    /** 只存在雲端的歷史團（例如重裝 App 後從 Sheet 同步回來），本機沒有明細 */
    val isRemoteOnly: Boolean,
    val remoteTotal: Int,
    val lines: List<OrderLine>,
    /** 已開單：Chrome 插件看得到、可以跟單 */
    val joinOpen: Boolean = false,
    val openedBy: String = "",
) {
    val total: Int get() = if (isRemoteOnly) remoteTotal else lines.sumOf { it.subtotal }

    val everSubmitted: Boolean get() = submittedAt != null || isRemoteOnly

    /** 已結單：送出（或待上傳）且沒在開放跟單；點進去直接看誰點了什麼 */
    val isClosed: Boolean get() = !isRemoteOnly && !joinOpen && status != SessionStatus.ORDERING
}

object MealTypes {
    val defaults = listOf("午餐", "晚餐", "飲料", "點心")
}

data class AppSettings(
    val excludeRecentEnabled: Boolean,
    /** 排除「今天與前 N 天」吃過的店 */
    val excludeDays: Int,
    val lastSyncAt: Long?,
    /** 上次開單填的名字（插件上顯示「誰開團」） */
    val openerName: String = "",
) {
    companion object {
        const val MIN_EXCLUDE_DAYS = 1
        const val MAX_EXCLUDE_DAYS = 14
        const val DEFAULT_EXCLUDE_DAYS = 3
    }
}
