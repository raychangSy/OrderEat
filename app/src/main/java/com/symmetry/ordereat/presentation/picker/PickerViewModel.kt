package com.symmetry.ordereat.presentation.picker

import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.AppSettings
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.usecase.CreateSessionUseCase
import com.symmetry.ordereat.domain.usecase.PickRandomRestaurantUseCase
import com.symmetry.ordereat.domain.usecase.PickResult
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PickerState(
    val mealType: String,
    val restaurants: List<Restaurant> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    val excludeEnabled: Boolean = true,
    val excludeDays: Int = AppSettings.DEFAULT_EXCLUDE_DAYS,
    /** 目前會被排除的店名（顯示在說明裡） */
    val excludedNames: List<String> = emptyList(),
    val rolling: Boolean = false,
    /** 抽籤動畫中閃過的店名 */
    val rollingName: String? = null,
    val picked: Restaurant? = null,
    val showAllExcludedDialog: Boolean = false,
    val showManualPicker: Boolean = false,
    val creating: Boolean = false,
)

sealed interface PickerIntent {
    data class ToggleCategory(val category: String) : PickerIntent
    data class SetExcludeEnabled(val enabled: Boolean) : PickerIntent
    data class ChangeExcludeDays(val delta: Int) : PickerIntent
    data object Roll : PickerIntent
    data object RollIgnoringExclude : PickerIntent
    data object DismissAllExcludedDialog : PickerIntent
    data object Confirm : PickerIntent
    data object ShowManualPicker : PickerIntent
    data object DismissManualPicker : PickerIntent
    data class PickManually(val restaurant: Restaurant) : PickerIntent
}

sealed interface PickerEffect {
    data class OpenUrl(val url: String) : PickerEffect
    data class NavigateToOrder(val sessionId: String) : PickerEffect
    data class ShowMessage(val message: String) : PickerEffect
}

@HiltViewModel(assistedFactory = PickerViewModel.Factory::class)
class PickerViewModel @AssistedInject constructor(
    @Assisted mealType: String,
    private val pickRandomRestaurant: PickRandomRestaurantUseCase,
    private val createSession: CreateSessionUseCase,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
    menuRepository: MenuRepository,
    sessionRepository: SessionRepository,
) : MviViewModel<PickerState, PickerIntent, PickerEffect>(PickerState(mealType = mealType)) {

    @AssistedFactory
    interface Factory {
        fun create(mealType: String): PickerViewModel
    }

    private var sessions: List<OrderSession> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                menuRepository.observeRestaurants(),
                sessionRepository.observeSessions(),
                settingsRepository.settings,
            ) { restaurants, sessions, settings ->
                this@PickerViewModel.sessions = sessions
                val recentIds = PickRandomRestaurantUseCase.recentRestaurantIds(sessions, clock.today(), settings.excludeDays)
                setState {
                    copy(
                        restaurants = restaurants,
                        picked = picked?.let { p -> restaurants.find { it.id == p.id } ?: p },
                        categories = restaurants.flatMap { it.categories }.distinct(),
                        selectedCategories = selectedCategories.filterTo(mutableSetOf()) { c -> restaurants.any { c in it.categories } },
                        excludeEnabled = settings.excludeRecentEnabled,
                        excludeDays = settings.excludeDays,
                        excludedNames = restaurants.filter { it.id in recentIds }.map { it.name },
                    )
                }
            }.collect {}
        }
    }

    override fun handleIntent(intent: PickerIntent) {
        when (intent) {
            is PickerIntent.ToggleCategory -> setState {
                val next = if (intent.category in selectedCategories) selectedCategories - intent.category else selectedCategories + intent.category
                copy(selectedCategories = next)
            }

            is PickerIntent.SetExcludeEnabled -> viewModelScope.launch { settingsRepository.setExcludeRecentEnabled(intent.enabled) }
            is PickerIntent.ChangeExcludeDays -> viewModelScope.launch {
                settingsRepository.setExcludeDays(currentState.excludeDays + intent.delta)
            }

            PickerIntent.Roll -> roll(ignoreExclude = false)
            PickerIntent.RollIgnoringExclude -> {
                setState { copy(showAllExcludedDialog = false) }
                roll(ignoreExclude = true)
            }

            PickerIntent.DismissAllExcludedDialog -> setState { copy(showAllExcludedDialog = false) }
            PickerIntent.Confirm -> currentState.picked?.let(::confirm)
            PickerIntent.ShowManualPicker -> setState { copy(showManualPicker = true) }
            PickerIntent.DismissManualPicker -> setState { copy(showManualPicker = false) }
            is PickerIntent.PickManually -> {
                setState { copy(showManualPicker = false, picked = intent.restaurant) }
                // 有連結的店不開團：留在結果卡顯示連結，讓大家自己點
                if (intent.restaurant.menuLink == null) startSession(intent.restaurant)
            }
        }
    }

    /** 有「連結選單」的店直接開連結各自點，不走 POS 點餐流程 */
    private fun confirm(restaurant: Restaurant) {
        val link = restaurant.menuLink
        if (link != null) sendEffect(PickerEffect.OpenUrl(link)) else startSession(restaurant)
    }

    private fun roll(ignoreExclude: Boolean) {
        val s = currentState
        if (s.rolling || s.creating) return
        val result = pickRandomRestaurant(
            restaurants = s.restaurants,
            sessions = sessions,
            categories = s.selectedCategories,
            excludeDays = if (s.excludeEnabled && !ignoreExclude) s.excludeDays else null,
            today = clock.today(),
            previousId = s.picked?.id,
        )
        when (result) {
            PickResult.NoMatch -> sendEffect(PickerEffect.ShowMessage("這個分類沒有餐廳"))
            PickResult.AllExcluded -> setState { copy(showAllExcludedDialog = true) }
            is PickResult.Picked -> animateTo(result.restaurant)
        }
    }

    /** 店名快速輪播後停在抽中的店 */
    private fun animateTo(target: Restaurant) {
        val pool = currentState.restaurants.filter { r ->
            currentState.selectedCategories.isEmpty() || r.categories.any { it in currentState.selectedCategories }
        }
        setState { copy(rolling = true, picked = null) }
        viewModelScope.launch {
            repeat(ROLL_STEPS) { step ->
                setState { copy(rollingName = pool.random().name) }
                delay(40L + step * 12L)
            }
            setState { copy(rolling = false, rollingName = null, picked = target) }
        }
    }

    private fun startSession(restaurant: Restaurant) {
        if (currentState.creating) return
        setState { copy(creating = true) }
        viewModelScope.launch {
            val id = createSession(currentState.mealType, restaurant)
            setState { copy(creating = false) }
            sendEffect(PickerEffect.NavigateToOrder(id))
        }
    }

    private companion object {
        const val ROLL_STEPS = 14
    }
}
