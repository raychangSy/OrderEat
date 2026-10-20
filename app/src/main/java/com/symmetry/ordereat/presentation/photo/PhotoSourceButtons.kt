package com.symmetry.ordereat.presentation.photo

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.symmetry.ordereat.domain.repository.UploadOutcome
import java.io.File

/** 「拍照」「從相簿選」兩顆按鈕；選好後回傳 content:// 或 file:// uri */
@Composable
fun PhotoSourceButtons(enabled: Boolean, onPhotoSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) onPhotoSelected(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoSelected(uri.toString())
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                val uri = newCameraUri(context)
                cameraUri = uri.toString()
                takePicture.launch(uri)
            },
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("拍照")
        }
        FilledTonalButton(
            onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("從相簿選")
        }
    }
}

/** 上傳結果轉成提示文字 */
fun UploadOutcome.photoMessage(): String = when (this) {
    UploadOutcome.Uploaded -> "菜單照片已更新"
    is UploadOutcome.Queued -> "已存在手機，有網路會自動上傳（$reason）"
    is UploadOutcome.Failed -> reason
}

private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "menu_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
