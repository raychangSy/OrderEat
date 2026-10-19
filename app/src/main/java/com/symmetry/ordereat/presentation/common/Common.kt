package com.symmetry.ordereat.presentation.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.symmetry.ordereat.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 收集 MVI 一次性事件 */
@Composable
fun <E> CollectEffect(effect: Flow<E>, onEffect: suspend (E) -> Unit) {
    LaunchedEffect(effect) {
        effect.collect { onEffect(it) }
    }
}

fun formatPrice(amount: Int): String = "$$amount"

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei"))

fun formatTime(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis))

fun SessionStatus.label(everSubmitted: Boolean): String = when (this) {
    SessionStatus.ORDERING -> if (everSubmitted) "已修改，尚未重送" else "點餐中"
    SessionStatus.PENDING_UPLOAD -> "等待上傳"
    SessionStatus.SUBMITTED -> "已送出"
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 用外部 App 開網址（Google 地圖連結會交給地圖 App 或瀏覽器） */
@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "找不到可以開啟連結的 App", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 刪除整團確認；上傳過的會一併從 Google Sheet 刪除 */
@Composable
fun DeleteSessionDialog(uploaded: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刪除這團？") },
        text = {
            Text(
                if (uploaded) {
                    "會一併從 Google Sheet 刪除（每日記錄與點餐明細），插件也看不到這團。刪除後無法復原。"
                } else {
                    "這團還沒送出，刪除後所有點餐內容都會消失。"
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("刪除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
