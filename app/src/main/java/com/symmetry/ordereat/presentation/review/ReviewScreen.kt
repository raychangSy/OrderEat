package com.symmetry.ordereat.presentation.review

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symmetry.ordereat.domain.model.SessionStatus
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.DeleteSessionDialog
import com.symmetry.ordereat.presentation.common.formatPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is ReviewEffect.CopyToClipboard -> {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("訂單", effect.text)))
                snackbar.showSnackbar("已複製，可以貼到 LINE")
            }

            is ReviewEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            ReviewEffect.ClearFocus -> focusManager.clearFocus()
            ReviewEffect.Deleted -> onHome()
        }
    }

    val session = state.session
    val summary = state.summary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.session?.isClosed == true) "訂單明細" else "確認送出") },
                navigationIcon = {
                    // 結單後點餐頁也不能再改，返回直接回首頁
                    IconButton(onClick = if (state.session?.isClosed == true) onHome else onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(ReviewIntent.RequestDelete) }, enabled = !state.submitting) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除這團")
                    }
                    IconButton(onClick = { viewModel.onIntent(ReviewIntent.CopyText) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "複製文字")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (session == null || summary == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                // 點備註以外的地方收鍵盤
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("【${session.mealType}】${session.restaurantName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${session.date}・共 ${summary.itemCount} 份・總計 ${formatPrice(summary.total)}", style = MaterialTheme.typography.bodyLarge)
            }

            if (state.missingMembers.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("尚未點餐（${state.missingMembers.size} 人）", style = MaterialTheme.typography.titleSmall)
                            Text(state.missingMembers.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item { SectionTitle("依品項（打電話給店家用）") }
            items(summary.byItem) { item ->
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(item.itemName, style = MaterialTheme.typography.titleMedium)
                            if (item.optionsText.isNotBlank()) {
                                Text(item.optionsText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item.notes.forEach { Text("└ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        }
                        Text("×${item.quantity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }

            item { SectionTitle("每人應付") }
            items(summary.byMember) { member ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(member.memberName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            member.lines.joinToString("、") { line ->
                                val options = if (line.optionsText.isBlank()) "" else "(${line.optionsText})"
                                "${line.itemName}$options×${line.quantity}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatPrice(member.total), style = MaterialTheme.typography.titleMedium)
                }
            }

            if (session.isClosed) {
                // 已結單：唯讀
                if (session.note.isNotBlank()) {
                    item {
                        SectionTitle("備註")
                        Text(session.note, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else item {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onIntent(ReviewIntent.ChangeNote(it)) },
                    label = { Text("這團備註（例如：12:00 取餐、外送到 3F）") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.resultMessage?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                val submitted = session.status == SessionStatus.SUBMITTED
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!session.isClosed) Button(
                        onClick = { viewModel.onIntent(ReviewIntent.Submit) },
                        enabled = !state.submitting && (session.lines.isNotEmpty() || session.joinOpen),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        if (state.submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            when {
                                state.submitting -> "送出中…"
                                session.joinOpen -> "結單並送出（插件不能再加）"
                                session.everSubmitted -> "重新送出（覆蓋 Sheet 上的這團）"
                                else -> "送出訂單"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (session.joinOpen) {
                        OutlinedButton(
                            onClick = { viewModel.onIntent(ReviewIntent.RefreshJoin) },
                            enabled = !state.submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("同步跟單（先看插件點了什麼）") }
                    }
                    if (submitted || session.status == SessionStatus.PENDING_UPLOAD) {
                        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Home, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("回首頁")
                        }
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteSessionDialog(
            uploaded = state.session?.everSubmitted == true,
            onConfirm = { viewModel.onIntent(ReviewIntent.ConfirmDelete) },
            onDismiss = { viewModel.onIntent(ReviewIntent.DismissDelete) },
        )
    }

    if (state.showMissingConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ReviewIntent.DismissMissingConfirm) },
            title = { Text("還有 ${state.missingMembers.size} 人沒點") },
            text = { Text("${state.missingMembers.joinToString("、")}\n\n確定要送出嗎？") },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(ReviewIntent.ConfirmSubmitWithMissing) }) { Text("直接送出") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(ReviewIntent.DismissMissingConfirm) }) { Text("再等等") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
