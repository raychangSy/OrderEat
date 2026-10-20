package com.symmetry.ordereat.presentation.restaurants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.presentation.common.EmptyState
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestaurantsState(
    val restaurants: List<Restaurant> = emptyList(),
)

/** 列表只讀，沒有 Intent／Effect */
sealed interface RestaurantsIntent

sealed interface RestaurantsEffect

@HiltViewModel
class RestaurantsViewModel @Inject constructor(
    menuRepository: MenuRepository,
) : MviViewModel<RestaurantsState, RestaurantsIntent, RestaurantsEffect>(RestaurantsState()) {

    init {
        viewModelScope.launch {
            menuRepository.observeRestaurants().collect { setState { copy(restaurants = it) } }
        }
    }

    override fun handleIntent(intent: RestaurantsIntent) = Unit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantsScreen(viewModel: RestaurantsViewModel, onBack: () -> Unit, onOpenRestaurant: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("餐廳詳細（${state.restaurants.size} 間）") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        if (state.restaurants.isEmpty()) {
            EmptyState("還沒有餐廳", "在 Google Sheet「餐廳」分頁新增後，回首頁按「同步」", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "點餐廳看詳細、上傳菜單照片。店名、品項等資料請到 Google Sheet 修改，再回首頁同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.restaurants, key = { it.id }) { restaurant ->
                ListItem(
                    leadingContent = { PhotoThumbnail(restaurant.menuPhotoUrl) },
                    headlineContent = { Text(restaurant.name) },
                    supportingContent = {
                        val info = listOfNotNull(
                            restaurant.categories.joinToString("・").ifBlank { null },
                            when {
                                restaurant.photoPendingUpload -> "照片待上傳"
                                restaurant.menuPhotoUrl == null -> "尚無菜單照片"
                                else -> null
                            },
                        )
                        if (info.isNotEmpty()) Text(info.joinToString("　"))
                    },
                    modifier = Modifier.clickable { onOpenRestaurant(restaurant.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(url: String?) {
    val modifier = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.NoPhotography, contentDescription = "尚無菜單照片", tint = MaterialTheme.colorScheme.outline)
        }
    }
}
