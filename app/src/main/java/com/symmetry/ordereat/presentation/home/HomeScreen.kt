package com.symmetry.ordereat.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symmetry.ordereat.domain.model.MealTypes
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.EmptyState
import com.symmetry.ordereat.presentation.common.formatPrice
import com.symmetry.ordereat.presentation.common.formatTime
import com.symmetry.ordereat.presentation.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPicker: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSummary: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToPicker -> onOpenPicker(effect.mealType)
            is HomeEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今天吃什麼") },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, contentDescription = "歷史記錄") }
                    IconButton(onClick = onOpenMembers) { Icon(Icons.Default.Group, contentDescription = "成員") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "設定") }
                },
            )
        },
        floatingActionButton = {
            if (state.restaurantCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.onIntent(HomeIntent.NewSession) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("開新團") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SyncBar(
                syncing = state.syncing,
                lastSyncAt = state.lastSyncAt,
                pendingCount = state.pendingCount,
                onSync = { viewModel.onIntent(HomeIntent.Sync) },
            )
            when {
                state.restaurantCount == 0 -> EmptyState(
                    title = if (state.syncing) "同步中…" else "還沒有餐廳資料",
                    message = state.syncError?.let { "同步失敗：$it" }
                        ?: "在 Google Sheet 的「餐廳」「品項」分頁新增資料後，按上方「同步」下載菜單。",
                )

                state.todaySessions.isEmpty() -> EmptyState(
                    title = "${state.today} 還沒開團",
                    message = "按右下「開新團」，選餐別後抽一間餐廳。",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("今天的團（${state.today}）", style = MaterialTheme.typography.titleSmall)
                    }
                    items(state.todaySessions, key = { it.id }) { session ->
                        SessionCard(session, onClick = {
                            when {
                                session.isRemoteOnly -> Unit
                                session.isClosed -> onOpenSummary(session.id)
                                else -> onOpenSession(session.id)
                            }
                        })
                    }
                }
            }
        }
    }

    if (state.showMealTypeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HomeIntent.DismissMealTypeDialog) },
            title = { Text("這團要吃什麼？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealTypes.defaults.forEach { type ->
                        FilledTonalButton(
                            onClick = { viewModel.onIntent(HomeIntent.SelectMealType(type)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(type) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HomeIntent.DismissMealTypeDialog) }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SyncBar(syncing: Boolean, lastSyncAt: Long?, pendingCount: Int, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lastSyncAt?.let { "菜單更新於 ${formatTime(it)}" } ?: "尚未同步過菜單",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (pendingCount > 0) {
            AssistChip(
                onClick = onSync,
                label = { Text("$pendingCount 筆待上傳") },
                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.size(4.dp))
        }
        FilledTonalButton(onClick = onSync, enabled = !syncing) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
            }
            Spacer(Modifier.size(6.dp))
            Text(if (syncing) "同步中" else "同步")
        }
    }
}

@Composable
private fun SessionCard(session: OrderSession, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.mealType, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        session.isRemoteOnly -> "雲端記錄"
                        session.joinOpen -> "開放跟單中"
                        else -> session.status.label(session.everSubmitted)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(session.restaurantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val people = session.lines.map { it.memberName }.distinct().size
            val count = session.lines.sumOf { it.quantity }
            Text(
                if (session.isRemoteOnly) "總計 ${formatPrice(session.total)}" else "$people 人・$count 份・${formatPrice(session.total)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
