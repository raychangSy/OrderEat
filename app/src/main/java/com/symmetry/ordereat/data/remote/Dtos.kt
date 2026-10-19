package com.symmetry.ordereat.data.remote

import kotlinx.serialization.Serializable

// ── bootstrap 回應（欄位對應 apps-script/Code.gs 的 bootstrap_） ──

@Serializable
data class BootstrapDto(
    val restaurants: List<RestaurantDto> = emptyList(),
    val menuItems: List<MenuItemDto> = emptyList(),
    val optionGroups: List<OptionGroupDto> = emptyList(),
    val members: List<MemberDto> = emptyList(),
    val recentSessions: List<SessionDto> = emptyList(),
    /** 每日記錄上全部的團ID；舊版後端沒有這欄（null）時不做刪除 */
    val sessionIds: List<String>? = null,
    val serverTime: String = "",
)

@Serializable
data class RestaurantDto(
    val id: String,
    val name: String,
    val categories: List<String> = emptyList(),
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val menuPhotoUrl: String = "",
    val mapUrl: String = "",
    val menuUrl: String = "",
)

@Serializable
data class MenuItemDto(
    val id: String,
    val restaurantId: String,
    val name: String,
    val price: Double = 0.0,
    val optionGroupIds: List<String> = emptyList(),
)

@Serializable
data class OptionGroupDto(
    val id: String,
    val restaurantId: String = "",
    val name: String,
    /** single | multi */
    val type: String = "single",
    val required: Boolean = false,
    val choices: List<ChoiceDto> = emptyList(),
    /** 舊版後端沒有此欄位 */
    val maxSelect: Int? = null,
)

@Serializable
data class ChoiceDto(
    val name: String,
    val price: Double = 0.0,
)

@Serializable
data class MemberDto(
    val id: String = "",
    val name: String,
    val active: Boolean = true,
    val updatedAt: String = "",
)

@Serializable
data class SessionDto(
    val sessionId: String,
    val date: String,
    val mealType: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val total: Double = 0.0,
    val note: String = "",
    val submittedAt: String = "",
)

// ── 請求 payload ──

@Serializable
data class SubmitSessionPayload(
    val session: SessionPayload,
    val lines: List<LinePayload>,
)

@Serializable
data class SessionPayload(
    val sessionId: String,
    val date: String,
    val mealType: String,
    val restaurantId: String,
    val restaurantName: String,
    val note: String,
    /** open = 開放（插件可跟單）；closed = 截止；空白 = 舊團，維持原狀態 */
    val status: String = "",
    val openedBy: String = "",
)

@Serializable
data class LinePayload(
    val member: String,
    val itemName: String,
    val options: String,
    val qty: Int,
    val unitPrice: Int,
    val subtotal: Int,
    val note: String,
)

@Serializable
data class SubmitResultDto(
    val sessionId: String,
    val lineCount: Int,
    val total: Double,
    val submittedAt: String,
    /** 整團最新明細（含插件跟單）；舊版後端沒有（null） */
    val lines: List<RemoteLineDto>? = null,
)

@Serializable
data class SessionLinesDto(
    val session: OpenSessionDto,
    val lines: List<RemoteLineDto> = emptyList(),
)

@Serializable
data class OpenSessionDto(
    val sessionId: String,
    /** open | closed | submitted（舊團） */
    val status: String = "",
)

@Serializable
data class RemoteLineDto(
    val member: String,
    val itemName: String,
    val options: String = "",
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
    val subtotal: Double = 0.0,
    val note: String = "",
    /** app | extension */
    val source: String = "app",
)

@Serializable
data class SyncMembersPayload(
    val members: List<MemberDto>,
    val allowEmpty: Boolean = false,
)

@Serializable
data class SyncMembersResultDto(val count: Int)

@Serializable
data class UploadPhotoPayload(
    val restaurantId: String,
    val base64: String,
    val mimeType: String = "image/jpeg",
)

@Serializable
data class UploadPhotoResultDto(
    val url: String,
    val fileId: String,
)

@Serializable
data class DeleteSessionPayload(val sessionId: String)

@Serializable
data class DeleteSessionResultDto(val sessionId: String, val deletedLines: Int = 0, val deletedSessions: Int = 0)
