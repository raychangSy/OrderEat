package com.symmetry.ordereat.presentation.photo

import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

data class MenuPhotoState(
    val restaurant: Restaurant? = null,
    val uploading: Boolean = false,
)

sealed interface MenuPhotoIntent {
    /** 拍照或相簿挑選後的 uri */
    data class PhotoSelected(val uri: String) : MenuPhotoIntent
}

sealed interface MenuPhotoEffect {
    data class ShowMessage(val message: String) : MenuPhotoEffect
}

@HiltViewModel(assistedFactory = MenuPhotoViewModel.Factory::class)
class MenuPhotoViewModel @AssistedInject constructor(
    @Assisted private val restaurantId: String,
    private val menuRepository: MenuRepository,
) : MviViewModel<MenuPhotoState, MenuPhotoIntent, MenuPhotoEffect>(MenuPhotoState()) {

    @AssistedFactory
    interface Factory {
        fun create(restaurantId: String): MenuPhotoViewModel
    }

    init {
        viewModelScope.launch {
            menuRepository.observeRestaurant(restaurantId).collect { setState { copy(restaurant = it) } }
        }
    }

    override fun handleIntent(intent: MenuPhotoIntent) {
        when (intent) {
            is MenuPhotoIntent.PhotoSelected -> upload(intent.uri)
        }
    }

    private fun upload(uri: String) {
        if (currentState.uploading) return
        setState { copy(uploading = true) }
        viewModelScope.launch {
            val message = when (val outcome = menuRepository.uploadMenuPhoto(restaurantId, uri)) {
                UploadOutcome.Uploaded -> "菜單照片已更新"
                is UploadOutcome.Queued -> "已存在手機，有網路會自動上傳（${outcome.reason}）"
                is UploadOutcome.Failed -> outcome.reason
            }
            setState { copy(uploading = false) }
            sendEffect(MenuPhotoEffect.ShowMessage(message))
        }
    }
}
