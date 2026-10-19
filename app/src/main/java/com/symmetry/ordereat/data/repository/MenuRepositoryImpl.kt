package com.symmetry.ordereat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.symmetry.ordereat.data.local.MasterDataDao
import com.symmetry.ordereat.data.local.PendingPhotoDao
import com.symmetry.ordereat.data.local.PendingPhotoEntity
import com.symmetry.ordereat.data.mapper.toDomain
import com.symmetry.ordereat.data.photo.ImageCompressor
import com.symmetry.ordereat.data.remote.ApiException
import com.symmetry.ordereat.data.remote.OrderEatApi
import com.symmetry.ordereat.data.remote.UploadPhotoPayload
import com.symmetry.ordereat.data.sync.UploadScheduler
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.RestaurantMenu
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val masterDao: MasterDataDao,
    private val photoDao: PendingPhotoDao,
    private val api: OrderEatApi,
    private val compressor: ImageCompressor,
    private val clock: AppClock,
    private val scheduler: UploadScheduler,
) : MenuRepository {

    override fun observeRestaurants(): Flow<List<Restaurant>> =
        combine(masterDao.observeRestaurants(), photoDao.observeAll()) { restaurants, photos ->
            val pending = photos.associate { it.restaurantId to it.filePath }
            restaurants.map { it.toDomain(pending[it.id]) }
        }

    override fun observeRestaurant(id: String): Flow<Restaurant?> =
        combine(masterDao.observeRestaurant(id), photoDao.observeAll()) { restaurant, photos ->
            restaurant?.toDomain(photos.firstOrNull { it.restaurantId == id }?.filePath)
        }

    override fun observeMenu(restaurantId: String): Flow<RestaurantMenu> =
        combine(masterDao.observeMenuItems(restaurantId), masterDao.observeOptionGroups(restaurantId)) { items, groups ->
            RestaurantMenu(
                items = items.map { it.toDomain() },
                optionGroups = groups.map { it.toDomain() }.associateBy { it.id },
            )
        }

    override suspend fun uploadMenuPhoto(restaurantId: String, imageUri: String): UploadOutcome {
        val target = File(context.filesDir, "menu_photos/${restaurantId}_${clock.nowMillis()}.jpg")
        try {
            compressor.compress(Uri.parse(imageUri), target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            target.delete()
            return UploadOutcome.Failed("照片處理失敗：${e.message.orEmpty()}")
        }

        // 一店一張：取代尚未上傳的舊照片
        photoDao.getAll().firstOrNull { it.restaurantId == restaurantId }?.let { File(it.filePath).delete() }
        val pending = PendingPhotoEntity(restaurantId, target.absolutePath, clock.nowMillis())
        photoDao.upsert(pending)

        return try {
            upload(pending)
            UploadOutcome.Uploaded
        } catch (e: ApiException) {
            scheduler.schedule()
            UploadOutcome.Queued(e.message.orEmpty())
        }
    }

    /** 背景 Worker 用：回傳是否全部成功 */
    suspend fun uploadPendingPhotos(): Boolean {
        var allOk = true
        photoDao.getAll().forEach { pending ->
            try {
                upload(pending)
            } catch (e: ApiException) {
                allOk = false
            }
        }
        return allOk
    }

    private suspend fun upload(pending: PendingPhotoEntity) {
        val file = File(pending.filePath)
        if (!file.exists()) {
            photoDao.deleteIfSame(pending.restaurantId, pending.filePath)
            return
        }
        val base64 = withContext(Dispatchers.IO) { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }
        val result = api.uploadPhoto(UploadPhotoPayload(pending.restaurantId, base64))
        masterDao.updatePhotoUrl(pending.restaurantId, result.url)
        photoDao.deleteIfSame(pending.restaurantId, pending.filePath)
        file.delete()
    }
}
