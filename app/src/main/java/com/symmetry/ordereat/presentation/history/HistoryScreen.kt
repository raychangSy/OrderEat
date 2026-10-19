package com.symmetry.ordereat.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.presentation.common.EmptyState
import com.symmetry.ordereat.presentation.common.formatPrice
import com.symmetry.ordereat.presentation.common.label
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryState(
    val loading: Boolean = true,
    val days: List<Pair<LocalDate, List<OrderSession>>> = emptyList(),
)

/** 歷史畫面只讀，沒有 Intent；導頁由 UI 直接處理 */
sealed interface HistoryIntent

@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : MviViewModel<HistoryState, HistoryIntent, Nothing>(HistoryState()) {

    init {
        viewModelScope.launch {
            sessionRepository.observeSessions().collect { sessions ->
                setState {
                    copy(
                        loading = false,
                        days = sessions.groupBy { it.date }.toSortedMap(compareByDescending { it }).toList(),
                    )
                }
            }
        }
    }

    override fun handleIntent(intent: HistoryIntent) = Unit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSummary: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("吃過哪些店") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        if (!state.loading && state.days.isEmpty()) {
            EmptyState("還沒有記錄", "送出第一團後就會出現在這裡", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            state.days.forEach { (date, sessions) ->
                item(key = "date:$date") {
                    Text(
                        "$date（${weekday(date)}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(sessions, key = { it.id }) { session ->
                    ListItem(
                        overlineContent = { Text(session.mealType) },
                        headlineContent = { Text(session.restaurantName) },
                        supportingContent = {
                            val status = if (session.isRemoteOnly) "雲端記錄（本機無明細）" else session.status.label(session.everSubmitted)
                            val note = session.note.takeIf { it.isNotBlank() }?.let { "・$it" }.orEmpty()
                            Text("$status$note")
                        },
                        trailingContent = { Text(formatPrice(session.total), style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.clickable(enabled = !session.isRemoteOnly) {
                            if (session.isClosed) onOpenSummary(session.id) else onOpenSession(session.id)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun weekday(date: LocalDate): String = "週" + "一二三四五六日"[date.dayOfWeek.value - 1]
