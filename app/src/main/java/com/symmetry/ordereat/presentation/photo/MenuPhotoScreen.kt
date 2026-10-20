package com.symmetry.ordereat.presentation.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.symmetry.ordereat.presentation.common.CollectEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPhotoScreen(viewModel: MenuPhotoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MenuPhotoEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.restaurant?.name?.let { "$it・菜單" } ?: "菜單") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val url = state.restaurant?.menuPhotoUrl
                if (url != null) {
                    ZoomableImage(url)
                } else {
                    Text("還沒有菜單照片", style = MaterialTheme.typography.bodyLarge)
                }
                if (state.uploading) CircularProgressIndicator()
            }
            if (state.restaurant?.photoPendingUpload == true) {
                Text(
                    "照片還在手機上，有網路後會自動上傳",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            PhotoSourceButtons(
                enabled = !state.uploading,
                onPhotoSelected = { viewModel.onIntent(MenuPhotoIntent.PhotoSelected(it)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Text(
                "一間店只存一張，新照片會取代舊照片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

/** 雙指縮放、拖曳 */
@Composable
private fun ZoomableImage(url: String) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = url,
        contentDescription = "菜單照片",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
