package com.symmetry.ordereat.presentation.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symmetry.ordereat.domain.model.AppSettings
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.rememberOpenUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PickerScreen(
    viewModel: PickerViewModel,
    onBack: () -> Unit,
    onSessionCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openUrl = rememberOpenUrl()

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PickerEffect.OpenUrl -> openUrl(effect.url)
            is PickerEffect.NavigateToOrder -> onSessionCreated(effect.sessionId)
            is PickerEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.mealType}・選餐廳") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = { viewModel.onIntent(PickerIntent.ShowManualPicker) }) { Text("自己選") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.categories.isNotEmpty()) {
                Text("分類（不選 = 全部）", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = category in state.selectedCategories,
                            onClick = { viewModel.onIntent(PickerIntent.ToggleCategory(category)) },
                            label = { Text(category) },
                        )
                    }
                }
            }

            ExcludeRecentCard(
                enabled = state.excludeEnabled,
                days = state.excludeDays,
                excludedNames = state.excludedNames,
                onToggle = { viewModel.onIntent(PickerIntent.SetExcludeEnabled(it)) },
                onChangeDays = { viewModel.onIntent(PickerIntent.ChangeExcludeDays(it)) },
            )

            ResultCard(state)

            if (state.picked != null && !state.rolling) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(PickerIntent.Roll) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("重抽")
                    }
                    val hasLink = state.picked?.menuLink != null
                    Button(
                        onClick = { viewModel.onIntent(PickerIntent.Confirm) },
                        enabled = !state.creating,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Icon(if (hasLink) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (hasLink) "開啟點餐連結" else "確定，開始點餐")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(PickerIntent.ShowManualPicker) },
                        enabled = !state.rolling && state.restaurants.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("自己選", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { viewModel.onIntent(PickerIntent.Roll) },
                        enabled = !state.rolling && state.restaurants.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.rolling) "抽籤中…" else "抽一間", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (state.showAllExcludedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PickerIntent.DismissAllExcludedDialog) },
            title = { Text("沒有店可以抽") },
            text = { Text("符合分類的店，今天與前 ${state.excludeDays} 天內都吃過了。要忽略「排除最近吃過」再抽一次嗎？") },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(PickerIntent.RollIgnoringExclude) }) { Text("忽略並抽") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PickerIntent.DismissAllExcludedDialog) }) { Text("取消") }
            },
        )
    }

    if (state.showManualPicker) {
        ManualPickerDialog(
            restaurants = state.restaurants,
            recentNames = state.excludedNames.toSet(),
            onPick = { viewModel.onIntent(PickerIntent.PickManually(it)) },
            onDismiss = { viewModel.onIntent(PickerIntent.DismissManualPicker) },
        )
    }
}

@Composable
private fun ExcludeRecentCard(
    enabled: Boolean,
    days: Int,
    excludedNames: List<String>,
    onToggle: (Boolean) -> Unit,
    onChangeDays: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("排除最近吃過的店", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                if (enabled) {
                    "開啟中：今天與前 $days 天內吃過的店不會被抽到。"
                } else {
                    "關閉中：所有店都可能被抽到。開啟後會排除今天與前 $days 天內吃過的店。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("排除天數", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChangeDays(-1) }, enabled = days > AppSettings.MIN_EXCLUDE_DAYS) {
                    Icon(Icons.Default.Remove, contentDescription = "減少天數")
                }
                Text("$days 天", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onChangeDays(1) }, enabled = days < AppSettings.MAX_EXCLUDE_DAYS) {
                    Icon(Icons.Default.Add, contentDescription = "增加天數")
                }
            }
            if (enabled && excludedNames.isNotEmpty()) {
                Text(
                    "目前排除：${excludedNames.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(state: PickerState) {
    val picked = state.picked
    Card(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.rolling -> Text(
                    state.rollingName.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                picked != null -> {
                    Text("就吃這間！", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(picked.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (picked.categories.isNotEmpty()) Text(picked.categories.joinToString("・"), style = MaterialTheme.typography.bodyMedium)
                    if (picked.phone.isNotBlank()) Text("☎ ${picked.phone}", style = MaterialTheme.typography.bodyMedium)
                    if (picked.note.isNotBlank()) {
                        Text(picked.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (picked.menuLink != null) {
                        Text(
                            "這間用連結點餐，按下方「開啟點餐連結」各自下單",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    val openUrl = rememberOpenUrl()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openUrl(picked.mapLink) }) {
                            Icon(Icons.Default.Map, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Google 地圖")
                        }
                    }
                }

                else -> Text(
                    "按下方「抽一間」\n不喜歡可以重抽，按「確定」才算數",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ManualPickerDialog(
    restaurants: List<Restaurant>,
    recentNames: Set<String>,
    onPick: (Restaurant) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自己選餐廳") },
        text = {
            LazyColumn {
                items(restaurants, key = { it.id }) { restaurant ->
                    ListItem(
                        headlineContent = { Text(restaurant.name) },
                        supportingContent = {
                            val info = listOfNotNull(
                                restaurant.categories.joinToString("・").ifBlank { null },
                                "最近吃過".takeIf { restaurant.name in recentNames },
                            )
                            if (info.isNotEmpty()) Text(info.joinToString("　"))
                        },
                        modifier = Modifier.clickable { onPick(restaurant) },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
