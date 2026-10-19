package com.symmetry.ordereat.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** 菜單照片縮小：長邊 1280px、JPEG 75%（約 150–300KB）。ImageDecoder 會自動套用 EXIF 方向。 */
class ImageCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun compress(uri: Uri, target: File, maxEdge: Int = 1280, quality: Int = 75) = withContext(Dispatchers.IO) {
        val source = if (uri.scheme == "file") {
            ImageDecoder.createSource(File(requireNotNull(uri.path)))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > maxEdge) {
                val scale = maxEdge.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
    }
}
