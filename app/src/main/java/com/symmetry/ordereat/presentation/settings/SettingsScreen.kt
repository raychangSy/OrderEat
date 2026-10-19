package com.symmetry.ordereat.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.AppSettings
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import com.symmetry.ordereat.domain.usecase.SyncMasterDataUseCase
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.formatTime
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SettingsState(
    val settings: AppSettings = AppSettings(true, AppSettings.DEFAULT_EXCLUDE_DAYS, null),
    val pendingCount: Int = 0,
    val syncing: Boolean = false,
    val backendConfigured: Boolean = false,
)

sealed interface SettingsIntent {
    data object Sync : SettingsIntent
    data object RetryUploads : SettingsIntent
    data class SetExcludeEnabled(val enabled: Boolean) : SettingsIntent
    data class SetExcludeDays(val days: Int) : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val syncMasterData: SyncMasterDataUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(
    SettingsState(backendConfigured = syncRepository.isBackendConfigured),
) {

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, syncRepository.observePendingCount()) { s, p -> s to p }
                .collect { (s, p) -> setState { copy(settings = s, pendingCount = p) } }
        }
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Sync -> {
                if (currentState.syncing) return
                setState { copy(syncing = true) }
                viewModelScope.launch {
                    val error = syncMasterData().exceptionOrNull()?.message
                    setState { copy(syncing = false) }
                    sendEffect(SettingsEffect.ShowMessage(error?.let { "同步失敗：$it" } ?: "菜單已更新"))
                }
            }

            SettingsIntent.RetryUploads -> {
                syncRepository.retryUploadsNow()
                sendEffect(SettingsEffect.ShowMessage("已開始補送，有網路時會在背景完成"))
            }

            is SettingsIntent.SetExcludeEnabled -> viewModelScope.launch { settingsRepository.setExcludeRecentEnabled(intent.enabled) }
            is SettingsIntent.SetExcludeDays -> viewModelScope.launch { settingsRepository.setExcludeDays(intent.days) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is SettingsEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("菜單同步") {
                Text(
                    state.settings.lastSyncAt?.let { "上次同步：${formatTime(it)}" } ?: "尚未同步",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "在 Google Sheet 改了餐廳、品項或客製選項後，按這裡更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { viewModel.onIntent(SettingsIntent.Sync) }, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.syncing) "同步中…" else "立即同步")
                }
            }

            SettingsCard("待上傳") {
                Text(
                    if (state.pendingCount == 0) "全部都已上傳" else "${state.pendingCount} 筆等待上傳（訂單、照片或成員名單）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { viewModel.onIntent(SettingsIntent.RetryUploads) },
                    enabled = state.pendingCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("立即重送") }
            }

            SettingsCard("排除最近吃過的店") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.settings.excludeRecentEnabled) "開啟" else "關閉",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.settings.excludeRecentEnabled,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetExcludeEnabled(it)) },
                    )
                }
                Text(
                    "抽餐廳時不會抽到「今天與前 ${state.settings.excludeDays} 天」內吃過的店。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.settings.excludeDays.toFloat(),
                    onValueChange = { viewModel.onIntent(SettingsIntent.SetExcludeDays(it.roundToInt())) },
                    valueRange = AppSettings.MIN_EXCLUDE_DAYS.toFloat()..AppSettings.MAX_EXCLUDE_DAYS.toFloat(),
                    steps = AppSettings.MAX_EXCLUDE_DAYS - AppSettings.MIN_EXCLUDE_DAYS - 1,
                )
            }

            SettingsCard("後端") {
                Text(
                    if (state.backendConfigured) "已設定 Google Apps Script" else "尚未設定：請在 local.properties 填 ORDEREAT_SCRIPT_URL 與 ORDEREAT_TOKEN 後重新建置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.backendConfigured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
