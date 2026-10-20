package com.symmetry.ordereat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.symmetry.ordereat.presentation.history.HistoryScreen
import com.symmetry.ordereat.presentation.home.HomeScreen
import com.symmetry.ordereat.presentation.members.MembersScreen
import com.symmetry.ordereat.presentation.order.OrderScreen
import com.symmetry.ordereat.presentation.order.OrderViewModel
import com.symmetry.ordereat.presentation.photo.MenuPhotoScreen
import com.symmetry.ordereat.presentation.photo.MenuPhotoViewModel
import com.symmetry.ordereat.presentation.picker.PickerScreen
import com.symmetry.ordereat.presentation.picker.PickerViewModel
import com.symmetry.ordereat.presentation.restaurants.RestaurantDetailScreen
import com.symmetry.ordereat.presentation.restaurants.RestaurantDetailViewModel
import com.symmetry.ordereat.presentation.restaurants.RestaurantsScreen
import com.symmetry.ordereat.presentation.review.ReviewScreen
import com.symmetry.ordereat.presentation.review.ReviewViewModel
import com.symmetry.ordereat.presentation.settings.SettingsScreen

@Composable
fun OrderEatNavDisplay() {
    val backStack = rememberNavBackStack(HomeRoute)
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    viewModel = hiltViewModel(),
                    onOpenPicker = { backStack.add(PickerRoute(it)) },
                    onOpenSession = { backStack.add(OrderRoute(it)) },
                    onOpenSummary = { backStack.add(ReviewRoute(it)) },
                    onOpenHistory = { backStack.add(HistoryRoute) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                )
            }
            entry<PickerRoute> { key ->
                PickerScreen(
                    viewModel = hiltViewModel<PickerViewModel, PickerViewModel.Factory> { it.create(key.mealType) },
                    onBack = back,
                    // 選好店後用點餐頁取代抽籤頁，返回時直接回首頁
                    onSessionCreated = { sessionId -> backStack.replaceTop(OrderRoute(sessionId)) },
                    onOpenMenuPhoto = { backStack.add(MenuPhotoRoute(it)) },
                )
            }
            entry<OrderRoute> { key ->
                OrderScreen(
                    viewModel = hiltViewModel<OrderViewModel, OrderViewModel.Factory> { it.create(key.sessionId) },
                    onBack = back,
                    onOpenReview = { backStack.add(ReviewRoute(key.sessionId)) },
                    onOpenMenuPhoto = { backStack.add(MenuPhotoRoute(it)) },
                )
            }
            entry<ReviewRoute> { key ->
                ReviewScreen(
                    viewModel = hiltViewModel<ReviewViewModel, ReviewViewModel.Factory> { it.create(key.sessionId) },
                    onBack = back,
                    onHome = { backStack.popToHome() },
                )
            }
            entry<MenuPhotoRoute> { key ->
                MenuPhotoScreen(
                    viewModel = hiltViewModel<MenuPhotoViewModel, MenuPhotoViewModel.Factory> { it.create(key.restaurantId) },
                    onBack = back,
                )
            }
            entry<HistoryRoute> {
                HistoryScreen(
                    viewModel = hiltViewModel(),
                    onBack = back,
                    onOpenSession = { backStack.add(OrderRoute(it)) },
                    onOpenSummary = { backStack.add(ReviewRoute(it)) },
                )
            }
            entry<MembersRoute> { MembersScreen(viewModel = hiltViewModel(), onBack = back) }
            entry<SettingsRoute> {
                SettingsScreen(
                    viewModel = hiltViewModel(),
                    onBack = back,
                    onOpenMembers = { backStack.add(MembersRoute) },
                    onOpenRestaurants = { backStack.add(RestaurantsRoute) },
                )
            }
            entry<RestaurantsRoute> {
                RestaurantsScreen(
                    viewModel = hiltViewModel(),
                    onBack = back,
                    onOpenRestaurant = { backStack.add(RestaurantDetailRoute(it)) },
                )
            }
            entry<RestaurantDetailRoute> { key ->
                RestaurantDetailScreen(
                    viewModel = hiltViewModel<RestaurantDetailViewModel, RestaurantDetailViewModel.Factory> { it.create(key.restaurantId) },
                    onBack = back,
                    onOpenMenuPhoto = { backStack.add(MenuPhotoRoute(it)) },
                )
            }
        },
    )
}

private fun NavBackStack<NavKey>.replaceTop(key: NavKey) {
    removeAt(lastIndex)
    add(key)
}

private fun NavBackStack<NavKey>.popToHome() {
    while (size > 1) removeAt(lastIndex)
}
