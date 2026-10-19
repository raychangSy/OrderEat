package com.symmetry.ordereat.data.repository

import com.symmetry.ordereat.data.local.AppPreferences
import com.symmetry.ordereat.data.local.MemberDao
import com.symmetry.ordereat.data.local.MemberEntity
import com.symmetry.ordereat.data.mapper.toDomain
import com.symmetry.ordereat.data.mapper.toDto
import com.symmetry.ordereat.data.remote.ApiException
import com.symmetry.ordereat.data.remote.OrderEatApi
import com.symmetry.ordereat.data.remote.SyncMembersPayload
import com.symmetry.ordereat.data.sync.UploadScheduler
import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val dao: MemberDao,
    private val api: OrderEatApi,
    private val prefs: AppPreferences,
    private val clock: AppClock,
    private val scheduler: UploadScheduler,
) : MemberRepository {

    override fun observeMembers(): Flow<List<Member>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addMember(name: String): Member {
        val entity = MemberEntity(
            id = newMemberId(),
            name = name.trim(),
            active = true,
            updatedAt = clock.nowMillis(),
            sortOrder = dao.maxSortOrder() + 1,
        )
        dao.upsert(entity)
        markDirty()
        return entity.toDomain()
    }

    override suspend fun updateMember(member: Member) {
        val existing = dao.getAll().firstOrNull { it.id == member.id } ?: return
        dao.upsert(existing.copy(name = member.name.trim(), active = member.active, updatedAt = clock.nowMillis()))
        markDirty()
    }

    /** 把整份名單備份到 Sheet；回傳是否成功（沒有異動也算成功） */
    suspend fun uploadIfDirty(): Boolean {
        if (!prefs.isMembersDirty()) return true
        // 先清旗標：上傳途中若又有異動會重新設回 true
        prefs.setMembersDirty(false)
        return try {
            val members = dao.getAll()
            if (members.isNotEmpty()) {
                api.syncMembers(SyncMembersPayload(members.map { it.toDto(formatTime(it.updatedAt)) }))
            }
            true
        } catch (e: ApiException) {
            prefs.setMembersDirty(true)
            false
        }
    }

    suspend fun markDirty() {
        prefs.setMembersDirty(true)
        scheduler.schedule()
    }

    private fun formatTime(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis))

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Taipei"))

        fun newMemberId(): String = "u_" + UUID.randomUUID().toString().replace("-", "").take(8)
    }
}
