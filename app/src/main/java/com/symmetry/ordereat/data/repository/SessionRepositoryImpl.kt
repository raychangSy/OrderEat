package com.symmetry.ordereat.data.repository

import com.symmetry.ordereat.data.local.MemberDao
import com.symmetry.ordereat.data.local.SessionDao
import com.symmetry.ordereat.data.local.SessionEntity
import com.symmetry.ordereat.data.mapper.toDomain
import com.symmetry.ordereat.data.mapper.toEntity
import com.symmetry.ordereat.data.mapper.toExtensionEntity
import com.symmetry.ordereat.data.mapper.toSubmitPayload
import com.symmetry.ordereat.data.remote.ApiException
import com.symmetry.ordereat.data.remote.OrderEatApi
import com.symmetry.ordereat.data.remote.RemoteLineDto
import com.symmetry.ordereat.data.sync.UploadScheduler
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.SessionStatus
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val memberDao: MemberDao,
    private val api: OrderEatApi,
    private val clock: AppClock,
    private val scheduler: UploadScheduler,
) : SessionRepository {

    override fun observeSessions(): Flow<List<OrderSession>> =
        dao.observeAllWithLines().map { list -> list.map { it.toDomain() } }

    override fun observeSession(id: String): Flow<OrderSession?> =
        dao.observeWithLines(id).map { it?.toDomain() }

    override suspend fun createSession(date: LocalDate, mealType: String, restaurant: Restaurant): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            SessionEntity(
                id = id,
                date = date.toString(),
                mealType = mealType,
                restaurantId = restaurant.id,
                restaurantName = restaurant.name,
                note = "",
                status = SessionStatus.ORDERING.name,
                submittedAt = null,
                isRemoteOnly = false,
                remoteTotal = 0,
                updatedAt = clock.nowMillis(),
            ),
        )
        return id
    }

    override suspend fun upsertLine(line: OrderLine) {
        dao.upsertLine(line.toEntity())
        markEditing(line.sessionId)
    }

    override suspend fun removeLine(sessionId: String, lineId: String) {
        dao.deleteLine(lineId)
        markEditing(sessionId)
    }

    override suspend fun updateNote(sessionId: String, note: String) {
        val current = dao.get(sessionId) ?: return
        if (current.note == note) return
        dao.updateNote(sessionId, note, clock.nowMillis())
        markEditing(sessionId)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        val session = dao.get(sessionId) ?: return Result.success(Unit)
        if (session.submittedAt != null || session.isRemoteOnly) {
            try {
                api.deleteSession(sessionId)
            } catch (e: ApiException) {
                return Result.failure(e)
            }
        }
        dao.delete(sessionId)
        return Result.success(Unit)
    }

    override suspend fun submit(sessionId: String): UploadOutcome {
        dao.updateStatus(sessionId, SessionStatus.PENDING_UPLOAD.name, clock.nowMillis())
        return try {
            uploadSession(sessionId)
            UploadOutcome.Uploaded
        } catch (e: ApiException) {
            scheduler.schedule()
            UploadOutcome.Queued(e.message.orEmpty())
        }
    }

    override suspend fun openForJoin(sessionId: String, openedBy: String): UploadOutcome {
        dao.updateJoin(sessionId, open = true, openedBy = openedBy.trim(), now = clock.nowMillis())
        return submit(sessionId)
    }

    override suspend fun closeJoin(sessionId: String): UploadOutcome {
        val session = dao.get(sessionId) ?: return UploadOutcome.Failed("找不到這團")
        dao.updateJoin(sessionId, open = false, openedBy = session.openedBy, now = clock.nowMillis())
        return submit(sessionId)
    }

    /** 背景 Worker 用：回傳是否全部成功 */
    suspend fun uploadPendingSessions(): Boolean {
        var allOk = true
        dao.idsWithStatus(SessionStatus.PENDING_UPLOAD.name).forEach { id ->
            try {
                uploadSession(id)
            } catch (e: ApiException) {
                allOk = false
            }
        }
        return allOk
    }

    private suspend fun uploadSession(id: String) {
        val snapshot = dao.getWithLines(id) ?: return
        if (snapshot.session.status != SessionStatus.PENDING_UPLOAD.name) return
        val result = api.submitSession(snapshot.toSubmitPayload())
        result.lines?.let { saveExtensionLines(id, it) }
        dao.markSubmittedIfPending(id, clock.nowMillis())
    }

    /** 首頁同步用：開放中的團順便抓插件跟單；Sheet 上已截止就跟著關。單一團失敗不影響其他 */
    suspend fun refreshOpenSessions() {
        dao.joinOpenIds().forEach { id ->
            try {
                val remote = api.getSessionLines(id)
                saveExtensionLines(id, remote.lines)
                if (remote.session.status != "open") dao.markJoinClosed(id)
            } catch (e: ApiException) {
                // 這團不在 Sheet（例如開團還沒上傳成功），略過
            }
        }
    }

    /** 後端回傳的整團明細中，插件跟單的那些存回本機（名字對得上成員就掛到該成員） */
    private suspend fun saveExtensionLines(sessionId: String, remote: List<RemoteLineDto>) {
        val memberIdByName = memberDao.getAll().associate { it.name to it.id }
        val now = clock.nowMillis()
        val lines = remote
            .filter { it.source == "extension" && it.itemName.isNotBlank() }
            .mapIndexed { i, line -> line.toExtensionEntity(sessionId, memberIdByName[line.member], i, now) }
        dao.replaceExtensionLines(sessionId, lines)
    }

    /** 送出後又修改 → 回到「點餐中」，需要重送 */
    private suspend fun markEditing(sessionId: String) {
        dao.updateStatus(sessionId, SessionStatus.ORDERING.name, clock.nowMillis())
    }
}
