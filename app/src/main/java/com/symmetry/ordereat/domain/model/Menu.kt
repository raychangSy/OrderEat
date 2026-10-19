package com.symmetry.ordereat.domain.model

import java.net.URLEncoder

data class Restaurant(
    val id: String,
    val name: String,
    val categories: List<String>,
    val phone: String,
    val address: String,
    val note: String,
    /** 遠端網址或本機待上傳照片（file://）；沒有照片為 null */
    val menuPhotoUrl: String?,
    /** 照片還在本機等上傳 */
    val photoPendingUpload: Boolean = false,
    /** Sheet 填的 Google 地圖商家連結；可能空白 */
    val mapUrl: String = "",
    /** Sheet 填的線上菜單／點餐連結；可能空白 */
    val menuUrl: String = "",
) {
    /** 有填才有；不是網址就當沒填 */
    val menuLink: String?
        get() = menuUrl.trim().takeIf { it.isWebUrl() }

    /** 有填連結用連結，沒填就用店名＋地址搜尋 Google 地圖 */
    val mapLink: String
        get() = mapUrl.trim().takeIf { it.isWebUrl() }
            ?: ("https://www.google.com/maps/search/?api=1&query=" +
                URLEncoder.encode(listOf(name, address).filter { it.isNotBlank() }.joinToString(" "), "UTF-8"))
}

private fun String.isWebUrl() = startsWith("http://") || startsWith("https://")

data class MenuItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val price: Int,
    val optionGroupIds: List<String>,
)

enum class SelectionType { SINGLE, MULTI }

data class OptionChoice(
    val name: String,
    val price: Int,
)

data class OptionGroup(
    val id: String,
    /** 空字串 = 通用，所有餐廳可用 */
    val restaurantId: String,
    val name: String,
    val type: SelectionType,
    val required: Boolean,
    val choices: List<OptionChoice>,
    /** SINGLE 固定為 1；MULTI 為 0 表示不限 */
    val maxSelect: Int,
)

data class RestaurantMenu(
    val items: List<MenuItem>,
    val optionGroups: Map<String, OptionGroup>,
) {
    fun groupsOf(item: MenuItem): List<OptionGroup> = item.optionGroupIds.mapNotNull { optionGroups[it] }

    companion object {
        val Empty = RestaurantMenu(emptyList(), emptyMap())
    }
}
