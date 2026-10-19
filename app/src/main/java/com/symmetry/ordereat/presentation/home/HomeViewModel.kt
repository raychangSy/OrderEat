package com.symmetry.ordereat.presentation.home

import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import com.symmetry.ordereat.domain.usecase.SyncMasterDataUseCase
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val today: LocalDate,
    val todaySessions: List<OrderSession> = emptyList(),
    val restaurantCount: Int = 0,
    val syncing: Boolean = false,
    val syncError: String? = null,
    val lastSyncAt: Long? = null,
    val pendingCount: Int = 0,
    val showMealTypeDialog: Boolean = false,
)

sealed interface HomeIntent {
    data object Sync : HomeIntent
    data object NewSession : HomeIntent
    data object DismissMealTypeDialog : HomeIntent
    data class SelectMealType(val mealType: String) : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToPicker(val mealType: String) : HomeEffect
    data class ShowMessage(val message: String) : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val syncMasterData: SyncMasterDataUseCase,
    sessionRepository: SessionRepository,
    menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    syncRepository: SyncRepository,
    clock: AppClock,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState(today = clock.today())) {

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeSessions(),
                menuRepository.observeRestaurants(),
                settingsRepository.settings,
                syncRepository.observePendingCount(),
            ) { sessions, restaurants, settings, pending ->
                val today = clock.today()
                setState {
                    copy(
                        today = today,
                        todaySessions = sessions.filter { it.date == today },
                        restaurantCount = restaurants.size,
                        lastSyncAt = settings.lastSyncAt,
                        pendingCount = pending,
                    )
                }
            }.collect {}
        }
        // 不自動同步：菜單只在使用者按「同步」時才去 Sheet 抓
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Sync -> sync()
            HomeIntent.NewSession -> setState { copy(showMealTypeDialog = true) }
            HomeIntent.DismissMealTypeDialog -> setState { copy(showMealTypeDialog = false) }
            is HomeIntent.SelectMealType -> {
                setState { copy(showMealTypeDialog = false) }
                sendEffect(HomeEffect.NavigateToPicker(intent.mealType))
            }
        }
    }

    private fun sync() {
        if (currentState.syncing) return
        setState { copy(syncing = true) }
        viewModelScope.launch {
            val result = syncMasterData()
            val error = result.exceptionOrNull()?.message
            setState { copy(syncing = false, syncError = error) }
            sendEffect(HomeEffect.ShowMessage(error?.let { "同步失敗：$it" } ?: "菜單已更新"))
        }
    }
}
