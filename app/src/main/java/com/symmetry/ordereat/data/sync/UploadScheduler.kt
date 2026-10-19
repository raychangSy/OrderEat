package com.symmetry.ordereat.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.symmetry.ordereat.data.repository.MemberRepositoryImpl
import com.symmetry.ordereat.data.repository.MenuRepositoryImpl
import com.symmetry.ordereat.data.repository.SessionRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 有網路時在背景補送：待上傳的訂單、成員名單、菜單照片 */
@Singleton
class UploadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "order_eat_upload"
    }
}

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessions: SessionRepositoryImpl,
    private val members: MemberRepositoryImpl,
    private val menu: MenuRepositoryImpl,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 三種都要跑，不因前一個失敗而跳過
        val results = listOf(
            sessions.uploadPendingSessions(),
            members.uploadIfDirty(),
            menu.uploadPendingPhotos(),
        )
        return if (results.all { it }) Result.success() else Result.retry()
    }
}
