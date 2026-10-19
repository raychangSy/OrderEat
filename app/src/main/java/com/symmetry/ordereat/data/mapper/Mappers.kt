package com.symmetry.ordereat.data.mapper

import com.symmetry.ordereat.data.local.MemberEntity
import com.symmetry.ordereat.data.local.MenuItemEntity
import com.symmetry.ordereat.data.local.OptionChoiceJson
import com.symmetry.ordereat.data.local.OptionGroupEntity
import com.symmetry.ordereat.data.local.OrderLineEntity
import com.symmetry.ordereat.data.local.RestaurantEntity
import com.symmetry.ordereat.data.local.SelectedOptionJson
import com.symmetry.ordereat.data.local.SessionEntity
import com.symmetry.ordereat.data.local.SessionWithLines
import com.symmetry.ordereat.data.remote.LinePayload
import com.symmetry.ordereat.data.remote.MemberDto
import com.symmetry.ordereat.data.remote.MenuItemDto
import com.symmetry.ordereat.data.remote.OptionGroupDto
import com.symmetry.ordereat.data.remote.RemoteLineDto
import com.symmetry.ordereat.data.remote.RestaurantDto
import com.symmetry.ordereat.data.remote.SessionDto
import com.symmetry.ordereat.data.remote.SessionPayload
import com.symmetry.ordereat.data.remote.SubmitSessionPayload
import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.model.MenuItem
import com.symmetry.ordereat.domain.model.OptionChoice
import com.symmetry.ordereat.domain.model.OptionGroup
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.model.SelectionType
import com.symmetry.ordereat.domain.model.SessionStatus
import java.time.LocalDate
import kotlin.math.roundToInt

// ── remote → local ──

fun RestaurantDto.toEntity(sortOrder: Int) = RestaurantEntity(
    id = id,
    name = name,
    categories = categories,
    phone = phone,
    address = address,
    note = note,
    menuPhotoUrl = menuPhotoUrl,
    mapUrl = mapUrl,
    menuUrl = menuUrl,
    sortOrder = sortOrder,
)

fun MenuItemDto.toEntity(sortOrder: Int) = MenuItemEntity(
    id = id,
    restaurantId = restaurantId,
    name = name,
    price = price.roundToInt(),
    optionGroupIds = optionGroupIds,
    sortOrder = sortOrder,
)

fun OptionGroupDto.toEntity(sortOrder: Int): OptionGroupEntity {
    val isMulti = type == "multi"
    return OptionGroupEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        type = if (isMulti) "multi" else "single",
        required = required,
        maxSelect = if (isMulti) (maxSelect ?: 0).coerceAtLeast(0) else 1,
        choices = choices.map { OptionChoiceJson(it.name, it.price.roundToInt()) },
        sortOrder = sortOrder,
    )
}

fun SessionDto.toRemoteOnlyEntity(now: Long) = SessionEntity(
    id = sessionId,
    date = date,
    mealType = mealType,
    restaurantId = restaurantId.ifBlank { null },
    restaurantName = restaurantName,
    note = note,
    status = SessionStatus.SUBMITTED.name,
    submittedAt = null,
    isRemoteOnly = true,
    remoteTotal = total.roundToInt(),
    updatedAt = now,
)

// ── local → domain ──

fun RestaurantEntity.toDomain(pendingPhotoPath: String?) = Restaurant(
    id = id,
    name = name,
    categories = categories,
    phone = phone,
    address = address,
    note = note,
    menuPhotoUrl = pendingPhotoPath?.let { "file://$it" } ?: menuPhotoUrl.ifBlank { null },
    photoPendingUpload = pendingPhotoPath != null,
    mapUrl = mapUrl,
    menuUrl = menuUrl,
)

fun MenuItemEntity.toDomain() = MenuItem(
    id = id,
    restaurantId = restaurantId,
    name = name,
    price = price,
    optionGroupIds = optionGroupIds,
)

fun OptionGroupEntity.toDomain() = OptionGroup(
    id = id,
    restaurantId = restaurantId,
    name = name,
    type = if (type == "multi") SelectionType.MULTI else SelectionType.SINGLE,
    required = required,
    choices = choices.map { OptionChoice(it.name, it.price) },
    maxSelect = maxSelect,
)

fun MemberEntity.toDomain() = Member(id = id, name = name, active = active)

fun SessionWithLines.toDomain() = OrderSession(
    id = session.id,
    date = LocalDate.parse(session.date),
    mealType = session.mealType,
    restaurantId = session.restaurantId,
    restaurantName = session.restaurantName,
    note = session.note,
    status = runCatching { SessionStatus.valueOf(session.status) }.getOrDefault(SessionStatus.ORDERING),
    submittedAt = session.submittedAt,
    isRemoteOnly = session.isRemoteOnly,
    remoteTotal = session.remoteTotal,
    lines = lines.sortedBy { it.createdAt }.map { it.toDomain() },
    joinOpen = session.joinOpen,
    openedBy = session.openedBy,
)

fun OrderLineEntity.toDomain() = OrderLine(
    id = id,
    sessionId = sessionId,
    memberId = memberId,
    memberName = memberName,
    menuItemId = menuItemId,
    itemName = itemName,
    selections = selections.map { SelectedOption(it.groupId, it.groupName, it.choiceName, it.price) },
    quantity = quantity,
    unitPrice = unitPrice,
    note = note,
    createdAt = createdAt,
    fromExtension = fromExtension,
)

// ── domain → local ──

fun OrderLine.toEntity() = OrderLineEntity(
    id = id,
    sessionId = sessionId,
    memberId = memberId,
    memberName = memberName,
    menuItemId = menuItemId,
    itemName = itemName,
    selections = selections.map { SelectedOptionJson(it.groupId, it.groupName, it.choiceName, it.price) },
    quantity = quantity,
    unitPrice = unitPrice,
    note = note,
    createdAt = createdAt,
    fromExtension = fromExtension,
)

// ── local → remote ──

fun SessionWithLines.toSubmitPayload(): SubmitSessionPayload = SubmitSessionPayload(
    session = SessionPayload(
        sessionId = session.id,
        date = session.date,
        mealType = session.mealType,
        restaurantId = session.restaurantId.orEmpty(),
        restaurantName = session.restaurantName,
        note = session.note,
        status = when {
            session.joinOpen -> "open"
            session.openedBy.isNotEmpty() -> "closed"
            else -> "" // 從沒開團：維持舊團（Sheet 狀態留空）
        },
        openedBy = session.openedBy,
    ),
    // 插件跟單的明細由後端保留，App 只送自己點的
    lines = lines.filterNot { it.fromExtension }.sortedBy { it.createdAt }.map { it.toDomain() }.map { line ->
        LinePayload(
            member = line.memberName,
            itemName = line.itemName,
            options = line.optionsText,
            qty = line.quantity,
            unitPrice = line.unitPrice,
            subtotal = line.subtotal,
            note = line.note,
        )
    },
)

fun MemberEntity.toDto(updatedAtText: String) = MemberDto(
    id = id,
    name = name,
    active = active,
    updatedAt = updatedAtText,
)

/** 插件跟單的明細 → 本機唯讀列；客製選項只有文字，存成單一選項以沿用 optionsText */
fun RemoteLineDto.toExtensionEntity(sessionId: String, memberId: String?, index: Int, now: Long) = OrderLineEntity(
    id = "ext_${sessionId}_$index",
    sessionId = sessionId,
    memberId = memberId,
    memberName = member,
    menuItemId = null,
    itemName = itemName,
    selections = if (options.isBlank()) emptyList() else listOf(SelectedOptionJson("ext", "", options, 0)),
    quantity = qty.roundToInt().coerceAtLeast(1),
    unitPrice = unitPrice.roundToInt(),
    note = note,
    createdAt = now + index,
    fromExtension = true,
)
