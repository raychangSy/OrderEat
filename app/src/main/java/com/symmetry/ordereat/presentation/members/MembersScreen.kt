package com.symmetry.ordereat.presentation.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.repository.MemberRepository
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.EmptyState
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import com.symmetry.ordereat.presentation.order.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MembersState(
    val members: List<Member> = emptyList(),
    val showAddDialog: Boolean = false,
    val editing: Member? = null,
)

sealed interface MembersIntent {
    data object ShowAdd : MembersIntent
    data class Edit(val member: Member) : MembersIntent
    data object DismissDialog : MembersIntent
    data class Add(val name: String) : MembersIntent
    data class Rename(val member: Member, val name: String) : MembersIntent
    data class SetActive(val member: Member, val active: Boolean) : MembersIntent
}

sealed interface MembersEffect {
    data class ShowMessage(val message: String) : MembersEffect
}

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
) : MviViewModel<MembersState, MembersIntent, MembersEffect>(MembersState()) {

    init {
        viewModelScope.launch {
            memberRepository.observeMembers().collect { setState { copy(members = it) } }
        }
    }

    override fun handleIntent(intent: MembersIntent) {
        when (intent) {
            MembersIntent.ShowAdd -> setState { copy(showAddDialog = true) }
            is MembersIntent.Edit -> setState { copy(editing = intent.member) }
            MembersIntent.DismissDialog -> setState { copy(showAddDialog = false, editing = null) }
            is MembersIntent.Add -> {
                val name = intent.name.trim()
                if (isDuplicate(name, exceptId = null)) return
                setState { copy(showAddDialog = false) }
                viewModelScope.launch {
                    memberRepository.addMember(name)
                    sendEffect(MembersEffect.ShowMessage("已新增 $name"))
                }
            }

            is MembersIntent.Rename -> {
                val name = intent.name.trim()
                if (isDuplicate(name, exceptId = intent.member.id)) return
                setState { copy(editing = null) }
                viewModelScope.launch { memberRepository.updateMember(intent.member.copy(name = name)) }
            }

            is MembersIntent.SetActive -> viewModelScope.launch {
                memberRepository.updateMember(intent.member.copy(active = intent.active))
            }
        }
    }

    private fun isDuplicate(name: String, exceptId: String?): Boolean {
        val duplicate = currentState.members.any { it.name == name && it.id != exceptId }
        if (duplicate) sendEffect(MembersEffect.ShowMessage("已經有「$name」了"))
        return duplicate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(viewModel: MembersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MembersEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成員（${state.members.count { it.active }} 人）") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onIntent(MembersIntent.ShowAdd) },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("新增成員") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.members.isEmpty()) {
            EmptyState("還沒有成員", "按右下「新增成員」，或在 Sheet「成員」分頁填好後重新同步", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "點名字可以改名；關掉開關 = 停用（點餐時不會出現，記錄保留）。名單會自動備份到 Sheet。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.members, key = { it.id }) { member ->
                ListItem(
                    headlineContent = {
                        Text(member.name, color = if (member.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                    },
                    supportingContent = { if (!member.active) Text("已停用") },
                    trailingContent = {
                        Switch(checked = member.active, onCheckedChange = { viewModel.onIntent(MembersIntent.SetActive(member, it)) })
                    },
                    modifier = Modifier.clickable { viewModel.onIntent(MembersIntent.Edit(member)) },
                )
                HorizontalDivider()
            }
        }
    }

    if (state.showAddDialog) {
        TextInputDialog(
            title = "新增成員",
            label = "名字",
            confirmText = "新增",
            onConfirm = { viewModel.onIntent(MembersIntent.Add(it)) },
            onDismiss = { viewModel.onIntent(MembersIntent.DismissDialog) },
        )
    }
    state.editing?.let { member ->
        TextInputDialog(
            title = "修改名字",
            label = "名字",
            confirmText = "儲存",
            initial = member.name,
            onConfirm = { viewModel.onIntent(MembersIntent.Rename(member, it)) },
            onDismiss = { viewModel.onIntent(MembersIntent.DismissDialog) },
        )
    }
}
