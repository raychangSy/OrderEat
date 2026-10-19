package com.symmetry.ordereat.data.repository

import com.symmetry.ordereat.data.local.AppPreferences
import com.symmetry.ordereat.data.local.MasterDataDao
import com.symmetry.ordereat.data.local.MemberDao
import com.symmetry.ordereat.data.local.MemberEntity
import com.symmetry.ordereat.data.local.PendingPhotoDao
import com.symmetry.ordereat.data.local.SessionDao
import com.symmetry.ordereat.data.mapper.toEntity
import com.symmetry.ordereat.data.mapper.toRemoteOnlyEntity
import com.symmetry.ordereat.data.remote.ApiConfig
import com.symmetry.ordereat.data.remote.ApiException
import com.symmetry.ordereat.data.remote.BootstrapDto
import com.symmetry.ordereat.data.remote.OrderEatApi
import com.symmetry.ordereat.data.sync.UploadScheduler
import com.symmetry.ordereat.domain.model.AppSettings
import com.symmetry.ordereat.domain.model.SessionStatus
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val api: OrderEatApi,
    private val masterDao: MasterDataDao,
    private val memberDao: MemberDao,
    private val sessionDao: SessionDao,
    private val photoDao: PendingPhotoDao,
    private val prefs: AppPreferences,
    private val clock: AppClock,
    private val scheduler: UploadScheduler,
    private val sessions: SessionRepositoryImpl,
    apiConfig: ApiConfig,
) : SyncRepository {

    override val isBackendConfigured: Boolean = apiConfig.isConfigured

    override suspend fun syncMasterData(): Result<Unit> {
        val dto = try {
            api.bootstrap()
        } catch (e: ApiException) {
            return Result.failure(e)
        }
        masterDao.replaceAll(
            restaurants = dto.restaurants.mapIndexed { i, r -> r.toEntity(i) },
            menuItems = dto.menuItems.mapIndexed { i, m -> m.toEntity(i) },
            optionGroups = dto.optionGroups.mapIndexed { i, g -> g.toEntity(i) },
        )
        importMembersIfEmpty(dto)
        removeSessionsDeletedRemotely(dto)
        importRemoteSessions(dto)
        sessions.refreshOpenSessions()
        prefs.setLastSyncAt(clock.nowMillis())
        // 順便補送離線時留下的資料
        scheduler.schedule()
        return Result.success(Unit)
    }

    override fun observePendingCount(): Flow<Int> = combine(
        sessionDao.observeCountWithStatus(SessionStatus.PENDING_UPLOAD.name),
        photoDao.observeAll(),
        prefs.membersDirty,
    ) { sessions, photos, membersDirty -> sessions + photos.size + if (membersDirty) 1 else 0 }

    override fun retryUploadsNow() = scheduler.schedule()

    /** App 是成員名單的主資料；只有本機完全沒有成員時（第一次安裝、重裝）才從 Sheet 匯入 */
    private suspend fun importMembersIfEmpty(dto: BootstrapDto) {
        if (memberDao.count() > 0 || dto.members.isEmpty()) return
        val now = clock.nowMillis()
        var generatedIds = false
        val members = dto.members
            .filter { it.name.isNotBlank() }
            .map { m ->
                val id = m.id.ifBlank {
                    generatedIds = true
                    MemberRepositoryImpl.newMemberId()
                }
                id to m
            }
            .distinctBy { it.first }
            .mapIndexed { index, (id, m) -> MemberEntity(id, m.name.trim(), m.active, now, index) }
        memberDao.insertAll(members)
        // Sheet 上手動填的成員沒有 id，補好後備份回去
        if (generatedIds) prefs.setMembersDirty(true)
    }

    /** Sheet 是已送出團的主資料：在「每日記錄」刪掉的團，本機也跟著刪（明細一起 cascade） */
    private suspend fun removeSessionsDeletedRemotely(dto: BootstrapDto) {
        val remoteIds = dto.sessionIds?.toSet() ?: return
        val gone = sessionDao.syncedIds().filter { it !in remoteIds }
        if (gone.isNotEmpty()) sessionDao.deleteAll(gone)
    }

    /** 雲端有、本機沒有的團（例如重裝 App），存成「僅雲端」記錄，給歷史與排除最近吃過用 */
    private suspend fun importRemoteSessions(dto: BootstrapDto) {
        val localIds = sessionDao.localIds().toSet()
        val now = clock.nowMillis()
        val remote = dto.recentSessions
            .filter { it.sessionId !in localIds && DATE_PATTERN.matches(it.date) }
            .map { it.toRemoteOnlyEntity(now) }
        if (remote.isNotEmpty()) sessionDao.upsertAll(remote)
    }

    private companion object {
        val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val prefs: AppPreferences,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = prefs.settings

    override suspend fun setExcludeRecentEnabled(enabled: Boolean) = prefs.setExcludeEnabled(enabled)

    override suspend fun setExcludeDays(days: Int) =
        prefs.setExcludeDays(days.coerceIn(AppSettings.MIN_EXCLUDE_DAYS, AppSettings.MAX_EXCLUDE_DAYS))

    override suspend fun setOpenerName(name: String) = prefs.setOpenerName(name.trim())
}
