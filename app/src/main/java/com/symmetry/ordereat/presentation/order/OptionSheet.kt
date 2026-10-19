package com.symmetry.ordereat.presentation.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.symmetry.ordereat.domain.model.OptionChoice
import com.symmetry.ordereat.domain.usecase.OptionRules
import com.symmetry.ordereat.presentation.common.formatPrice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OptionSheet(
    sheet: OptionSheetState,
    ordererName: String,
    onToggle: (groupId: String, choiceName: String) -> Unit,
    onQuantity: (Int) -> Unit,
    onNote: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(ordererName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(sheet.item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(formatPrice(sheet.item.price), style = MaterialTheme.typography.bodyLarge)
            }

            sheet.groups.forEach { group ->
                val selected = sheet.selected[group.id].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(OptionRules.hint(group), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.choices.forEach { choice ->
                            FilterChip(
                                selected = choice.name in selected,
                                onClick = { onToggle(group.id, choice.name) },
                                label = { Text(choiceLabel(choice)) },
                                modifier = Modifier.heightIn(min = 40.dp),
                            )
                        }
                    }
                    sheet.errors[group.id]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("數量", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { onQuantity(-1) }, enabled = sheet.quantity > 1) {
                    Icon(Icons.Default.Remove, contentDescription = "減少")
                }
                Text("${sheet.quantity}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                FilledTonalIconButton(onClick = { onQuantity(1) }) {
                    Icon(Icons.Default.Add, contentDescription = "增加")
                }
            }

            OutlinedTextField(
                value = sheet.note,
                onValueChange = onNote,
                label = { Text("備註（例如：不要蔥、分開裝）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                val verb = if (sheet.editingLine != null) "更新" else "加入"
                Text(
                    "$verb ${formatPrice(sheet.unitPrice)} × ${sheet.quantity} = ${formatPrice(sheet.unitPrice * sheet.quantity)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun choiceLabel(choice: OptionChoice): String = when {
    choice.price > 0 -> "${choice.name} +${choice.price}"
    choice.price < 0 -> "${choice.name} ${choice.price}"
    else -> choice.name
}
