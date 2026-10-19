package com.symmetry.ordereat.presentation.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.symmetry.ordereat.domain.model.MenuItem
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.usecase.Orderer
import com.symmetry.ordereat.presentation.common.DeleteSessionDialog
import com.symmetry.ordereat.presentation.common.rememberOpenUrl
import com.symmetry.ordereat.presentation.common.CollectEffect
import com.symmetry.ordereat.presentation.common.EmptyState
import com.symmetry.ordereat.presentation.common.formatPrice
import com.symmetry.ordereat.presentation.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenMenuPhoto: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is OrderEffect.ShowMessage -> {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(effect.message)
            }

            OrderEffect.Close -> onBack()
        }
    }

    BackHandler(enabled = state.activeOrderer != null) { viewModel.onIntent(OrderIntent.ClearOrderer) }

    val session = state.session
    val openUrl = rememberOpenUrl()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.restaurantName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (session != null) {
                            Text(
                                "${session.mealType}・${if (session.joinOpen) "開放跟單中" else session.status.label(session.everSubmitted)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.activeOrderer != null) viewModel.onIntent(OrderIntent.ClearOrderer) else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    state.restaurant?.let { r ->
                        r.menuLink?.let { link ->
                            IconButton(onClick = { openUrl(link) }) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "線上菜單")
                            }
                        }
                        IconButton(onClick = { openUrl(r.mapLink) }) {
                            Icon(Icons.Default.Map, contentDescription = "Google 地圖")
                        }
                        IconButton(onClick = { onOpenMenuPhoto(r.id) }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "菜單照片")
                        }
                    }
                    if (state.canDelete) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("刪除這團") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.onIntent(OrderIntent.RequestDelete)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                val orderer = state.activeOrderer
                if (orderer != null) {
                    // 正在幫某人點：只給「換人」，避免有人以為點完自己的就要按送出
                    val lines = state.activeLines
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("${orderer.name} 已點 ${lines.sumOf { it.quantity }} 份", style = MaterialTheme.typography.labelMedium)
                        Text(formatPrice(lines.sumOf { it.subtotal }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.onIntent(OrderIntent.ClearOrderer) },
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp),
                    ) { Text("點好了，換人") }
                } else {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("全團總計", style = MaterialTheme.typography.labelMedium)
                        Text(formatPrice(state.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onOpenReview,
                        enabled = session?.lines?.isNotEmpty() == true || session?.joinOpen == true,
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp),
                    ) { Text("全部點好，確認送出") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val orderer = state.activeOrderer
            when {
                state.loading -> Unit
                session == null -> EmptyState("找不到這團", "可能已被刪除")
                orderer == null -> OrdererGrid(
                    cells = state.orderers,
                    onSelect = { viewModel.onIntent(OrderIntent.SelectOrderer(it)) },
                    onAddGuest = { viewModel.onIntent(OrderIntent.ShowGuestDialog) },
                    header = {
                        if (state.canJoin || session.joinOpen) {
                            JoinCard(
                                session = session,
                                extensionLines = state.extensionLines,
                                busy = state.joinBusy,
                                onOpen = { viewModel.onIntent(OrderIntent.RequestOpenJoin) },
                                onRefresh = { viewModel.onIntent(OrderIntent.RefreshJoin) },
                            )
                        }
                    },
                )

                else -> MemberMenu(
                    orderer = orderer,
                    lines = state.activeLines,
                    items = state.menu.items,
                    onClickItem = { viewModel.onIntent(OrderIntent.ClickItem(it)) },
                    onEditLine = { viewModel.onIntent(OrderIntent.EditLine(it)) },
                    onRemoveLine = { viewModel.onIntent(OrderIntent.RemoveLine(it)) },
                    onManualItem = { viewModel.onIntent(OrderIntent.ShowManualItemDialog) },
                )
            }
        }
    }

    state.optionSheet?.let { sheet ->
        OptionSheet(
            sheet = sheet,
            ordererName = state.activeOrderer?.name.orEmpty(),
            onToggle = { groupId, choice -> viewModel.onIntent(OrderIntent.ToggleChoice(groupId, choice)) },
            onQuantity = { viewModel.onIntent(OrderIntent.ChangeQuantity(it)) },
            onNote = { viewModel.onIntent(OrderIntent.ChangeNote(it)) },
            onConfirm = { viewModel.onIntent(OrderIntent.ConfirmOptions) },
            onDismiss = { viewModel.onIntent(OrderIntent.DismissOptions) },
        )
    }

    if (state.showGuestDialog) {
        TextInputDialog(
            title = "新增訪客",
            label = "名字",
            confirmText = "開始點餐",
            onConfirm = { viewModel.onIntent(OrderIntent.AddGuest(it)) },
            onDismiss = { viewModel.onIntent(OrderIntent.DismissGuestDialog) },
        )
    }

    if (state.showManualItemDialog) {
        ManualItemDialog(
            onConfirm = { name, price, qty, note -> viewModel.onIntent(OrderIntent.AddManualItem(name, price, qty, note)) },
            onDismiss = { viewModel.onIntent(OrderIntent.DismissManualItemDialog) },
        )
    }

    if (state.showOpenJoinDialog) {
        TextInputDialog(
            title = "開單",
            label = "開單人（插件上會顯示）",
            confirmText = "開單",
            initial = state.openerName,
            onConfirm = { viewModel.onIntent(OrderIntent.ConfirmOpenJoin(it)) },
            onDismiss = { viewModel.onIntent(OrderIntent.DismissOpenJoin) },
        )
    }

    if (state.showDeleteDialog) {
        DeleteSessionDialog(
            uploaded = session?.everSubmitted == true,
            onConfirm = { viewModel.onIntent(OrderIntent.ConfirmDelete) },
            onDismiss = { viewModel.onIntent(OrderIntent.DismissDelete) },
        )
    }
}

@Composable
private fun OrdererGrid(
    cells: List<OrdererCell>,
    onSelect: (Orderer) -> Unit,
    onAddGuest: () -> Unit,
    header: @Composable () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val done = cells.count { it.lineCount > 0 }
            Text("誰要點餐？（已點 $done／${cells.size} 人）", style = MaterialTheme.typography.titleSmall)
        }
        items(cells, key = { (it.orderer.memberId ?: "guest:") + it.orderer.name }) { cell ->
            val ordered = cell.lineCount > 0
            Card(
                onClick = { onSelect(cell.orderer) },
                colors = if (ordered) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
                modifier = Modifier.heightIn(min = 76.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        cell.orderer.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            ordered -> "${cell.lineCount} 份・${formatPrice(cell.total)}"
                            cell.isGuest -> "訪客"
                            else -> "未點"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ordered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedCard(onClick = onAddGuest, modifier = Modifier.heightIn(min = 76.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Text("訪客", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MemberMenu(
    orderer: Orderer,
    lines: List<OrderLine>,
    items: List<MenuItem>,
    onClickItem: (MenuItem) -> Unit,
    onEditLine: (OrderLine) -> Unit,
    onRemoveLine: (OrderLine) -> Unit,
    onManualItem: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(orderer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (lines.isEmpty()) "點選下方品項" else "已點 ${lines.sumOf { it.quantity }} 份・${formatPrice(lines.sumOf { it.subtotal })}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (lines.isNotEmpty()) {
            items(lines, key = { "line:" + it.id }) { line ->
                ListItem(
                    headlineContent = { Text("${line.itemName} ×${line.quantity}") },
                    supportingContent = {
                        val detail = listOf(line.optionsText, line.note).filter { it.isNotBlank() }.joinToString("・")
                        if (detail.isNotBlank()) Text(detail)
                    },
                    overlineContent = if (line.fromExtension) {
                        { Text("插件跟單（要改請本人在插件改）") }
                    } else {
                        null
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatPrice(line.subtotal))
                            if (!line.fromExtension) {
                                IconButton(onClick = { onEditLine(line) }) { Icon(Icons.Default.Edit, contentDescription = "修改") }
                                IconButton(onClick = { onRemoveLine(line) }) { Icon(Icons.Default.Delete, contentDescription = "刪除") }
                            }
                        }
                    },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
        item {
            Text("菜單", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "這間店在 Sheet 還沒有品項，可以看菜單照片後用「手動輸入品項」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(items, key = { "item:" + it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                trailingContent = { Text(formatPrice(item.price), style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.clickable { onClickItem(item) },
            )
            HorizontalDivider()
        }
        item {
            OutlinedButton(
                onClick = onManualItem,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) { Text("手動輸入品項") }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ManualItemDialog(onConfirm: (String, Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    val priceValue = price.toIntOrNull()
    val quantityValue = quantity.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手動輸入品項") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("品名") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = { Text("單價") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit) },
                        label = { Text("數量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註（選填）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, priceValue ?: 0, quantityValue ?: 1, note) },
                enabled = name.isNotBlank() && priceValue != null && (quantityValue ?: 0) > 0,
            ) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 開單（讓 Chrome 插件跟單）／開單中同步跟單 */
@Composable
private fun JoinCard(
    session: OrderSession,
    extensionLines: List<OrderLine>,
    busy: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    val open = session.joinOpen
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (open) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (open) {
                    Text("開放跟單中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val people = extensionLines.map { it.memberName }.distinct().size
                    Text(
                        if (people > 0) {
                            "插件已有 $people 人點 ${extensionLines.sumOf { it.quantity }} 份（${formatPrice(extensionLines.sumOf { it.subtotal })}）"
                        } else {
                            "插件還沒有人跟單，按「同步跟單」看最新"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("手機點完換人會自動上傳；都點好後按下方「確認送出」結單", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("讓其他人用 Chrome 插件跟單", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "開單後插件就看得到這團，大家可以自己點；手機也能繼續輪流點。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                open -> OutlinedButton(onClick = onRefresh) { Text("同步跟單") }
                else -> Button(onClick = onOpen) { Text("開單") }
            }
        }
    }
}
