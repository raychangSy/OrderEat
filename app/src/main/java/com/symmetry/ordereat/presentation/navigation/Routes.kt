package com.symmetry.ordereat.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class PickerRoute(val mealType: String) : NavKey

@Serializable
data class OrderRoute(val sessionId: String) : NavKey

@Serializable
data class ReviewRoute(val sessionId: String) : NavKey

@Serializable
data class MenuPhotoRoute(val restaurantId: String) : NavKey

@Serializable
data object HistoryRoute : NavKey

@Serializable
data object MembersRoute : NavKey

@Serializable
data object SettingsRoute : NavKey
