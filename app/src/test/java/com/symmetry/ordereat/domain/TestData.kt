package com.symmetry.ordereat.domain

import com.symmetry.ordereat.domain.model.OptionChoice
import com.symmetry.ordereat.domain.model.OptionGroup
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.model.SelectionType
import com.symmetry.ordereat.domain.model.SessionStatus
import java.time.LocalDate

fun restaurant(id: String, vararg categories: String) = Restaurant(
    id = id,
    name = "店$id",
    categories = categories.toList(),
    phone = "",
    address = "",
    note = "",
    menuPhotoUrl = null,
)

fun session(restaurantId: String, date: LocalDate, lines: List<OrderLine> = emptyList()) = OrderSession(
    id = "s-$restaurantId-$date",
    date = date,
    mealType = "午餐",
    restaurantId = restaurantId,
    restaurantName = "店$restaurantId",
    note = "",
    status = SessionStatus.SUBMITTED,
    submittedAt = 1L,
    isRemoteOnly = false,
    remoteTotal = 0,
    lines = lines,
)

fun group(
    type: SelectionType,
    required: Boolean,
    maxSelect: Int = if (type == SelectionType.SINGLE) 1 else 0,
    choices: List<String> = listOf("燒腩", "烤鴨", "叉燒", "油雞", "雞腿"),
    id: String = "g",
) = OptionGroup(
    id = id,
    restaurantId = "",
    name = "配菜",
    type = type,
    required = required,
    choices = choices.map { OptionChoice(it, 0) },
    maxSelect = maxSelect,
)

fun line(
    member: String,
    item: String,
    unitPrice: Int,
    quantity: Int = 1,
    selections: List<SelectedOption> = emptyList(),
    note: String = "",
    createdAt: Long = 0,
    memberId: String? = "id-$member",
) = OrderLine(
    id = "$member-$item-$createdAt",
    sessionId = "s",
    memberId = memberId,
    memberName = member,
    menuItemId = item,
    itemName = item,
    selections = selections,
    quantity = quantity,
    unitPrice = unitPrice,
    note = note,
    createdAt = createdAt,
)
