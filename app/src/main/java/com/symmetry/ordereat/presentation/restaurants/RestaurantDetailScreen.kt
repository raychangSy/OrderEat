package com.symmetry.ordereat.presentation.restaurants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.RestaurantMenu
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.formatPrice
import com.symmetry.ordereat.presentation.common.rememberOpenUrl
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import com.symmetry.ordereat.presentation.photo.MenuPhotoPreview
import com.symmetry.ordereat.presentation.photo.PhotoSourceButtons
import com.symmetry.ordereat.presentation.photo.photoMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

data class RestaurantDetailState(
    val restaurant: Restaurant? = null,
    val menu: RestaurantMenu = RestaurantMenu.Empty,
    val uploading: Boolean = false,
)

sealed interface RestaurantDetailIntent {
    /** 拍照或相簿挑選後的 uri */
    data class PhotoSelected(val uri: String) : RestaurantDetailIntent
}

sealed interface RestaurantDetailEffect {
    data class ShowMessage(val message: String) : RestaurantDetailEffect
}

/** 餐廳資料只讀（以 Sheet 為準），只能換菜單照片 */
@HiltViewModel(assistedFactory = RestaurantDetailViewModel.Factory::class)
class RestaurantDetailViewModel @AssistedInject constructor(
    @Assisted private val restaurantId: String,
    private val menuRepository: MenuRepository,
) : MviViewModel<RestaurantDetailState, RestaurantDetailIntent, RestaurantDetailEffect>(RestaurantDetailState()) {

    @AssistedFactory
    interface Factory {
        fun create(restaurantId: String): RestaurantDetailViewModel
    }

    init {
        viewModelScope.launch {
            menuRepository.observeRestaurant(restaurantId).collect { setState { copy(restaurant = it) } }
        }
        viewModelScope.launch {
            menuRepository.observeMenu(restaurantId).collect { setState { copy(menu = it) } }
        }
    }

    override fun handleIntent(intent: RestaurantDetailIntent) {
        when (intent) {
            is RestaurantDetailIntent.PhotoSelected -> upload(intent.uri)
        }
    }

    private fun upload(uri: String) {
        if (currentState.uploading) return
        setState { copy(uploading = true) }
        viewModelScope.launch {
            val message = menuRepository.uploadMenuPhoto(restaurantId, uri).photoMessage()
            setState { copy(uploading = false) }
            sendEffect(RestaurantDetailEffect.ShowMessage(message))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantDetailScreen(
    viewModel: RestaurantDetailViewModel,
    onBack: () -> Unit,
    onOpenMenuPhoto: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openUrl = rememberOpenUrl()

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is RestaurantDetailEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.restaurant?.name ?: "餐廳") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val restaurant = state.restaurant ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuPhotoPreview(
                        url = restaurant.menuPhotoUrl,
                        emptyText = "目前沒有菜單照片\n請用下方按鈕上傳",
                        onClick = { onOpenMenuPhoto(restaurant.id) },
                        modifier = Modifier.height(220.dp),
                    ) {
                        if (state.uploading) CircularProgressIndicator()
                    }
                    if (restaurant.photoPendingUpload) {
                        Text(
                            "照片還在手機上，有網路後會自動上傳",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    PhotoSourceButtons(
                        enabled = !state.uploading,
                        onPhotoSelected = { viewModel.onIntent(RestaurantDetailIntent.PhotoSelected(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "一間店只存一張，新照片會取代舊照片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            item { RestaurantInfo(restaurant, openUrl) }
            item {
                HorizontalDivider()
                Text(
                    "品項（${state.menu.items.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                if (state.menu.items.isEmpty()) {
                    Text(
                        "Sheet 還沒有這間店的品項，點餐時可以看照片手輸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.menu.items, key = { it.id }) { item ->
                val groups = state.menu.groupsOf(item)
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = if (groups.isNotEmpty()) {
                        { Text(groups.joinToString("、") { it.name }) }
                    } else {
                        null
                    },
                    trailingContent = { Text(formatPrice(item.price), style = MaterialTheme.typography.bodyLarge) },
                )
            }
            item {
                Text(
                    "店家資料與品項請到 Google Sheet 修改，回首頁按「同步」後更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RestaurantInfo(restaurant: Restaurant, openUrl: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("分類", restaurant.categories.joinToString("・"))
        InfoRow("電話", restaurant.phone)
        InfoRow("地址", restaurant.address)
        InfoRow("備註", restaurant.note)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openUrl(restaurant.mapLink) }) {
                Icon(Icons.Default.Map, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Google 地圖")
            }
            restaurant.menuLink?.let { link ->
                OutlinedButton(onClick = { openUrl(link) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("線上菜單")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
    }
}
