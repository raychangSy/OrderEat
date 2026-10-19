package com.symmetry.ordereat.domain.repository

import com.symmetry.ordereat.domain.model.AppSettings
import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.RestaurantMenu
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MenuRepository {
    fun observeRestaurants(): Flow<List<Restaurant>>
    fun observeRestaurant(id: String): Flow<Restaurant?>
    fun observeMenu(restaurantId: String): Flow<RestaurantMenu>

    /** [imageUri] 為 content:// 或 file:// */
    suspend fun uploadMenuPhoto(restaurantId: String, imageUri: String): UploadOutcome
}

interface MemberRepository {
    /** 含停用成員，依建立順序 */
    fun observeMembers(): Flow<List<Member>>
    suspend fun addMember(name: String): Member
    suspend fun updateMember(member: Member)
}

interface SessionRepository {
    /** 新到舊 */
    fun observeSessions(): Flow<List<OrderSession>>
    fun observeSession(id: String): Flow<OrderSession?>

    suspend fun createSession(date: LocalDate, mealType: String, restaurant: Restaurant): String
    suspend fun upsertLine(line: OrderLine)
    suspend fun removeLine(sessionId: String, lineId: String)
    suspend fun updateNote(sessionId: String, note: String)

    /** 刪除整團；上傳過的會一併從 Google Sheet 刪除（需要網路），失敗時本機保留 */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    suspend fun submit(sessionId: String): UploadOutcome

    /** 開單：送出目前手機上的單並標記「開放中」，Chrome 插件就能跟單；之後再呼叫一次＝同步跟單 */
    suspend fun openForJoin(sessionId: String, openedBy: String): UploadOutcome

    /** 結單：插件不能再加，送出並帶回所有人的明細 */
    suspend fun closeJoin(sessionId: String): UploadOutcome
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setExcludeRecentEnabled(enabled: Boolean)
    suspend fun setExcludeDays(days: Int)
    suspend fun setOpenerName(name: String)
}

interface SyncRepository {
    /** local.properties 是否已填後端網址與 token */
    val isBackendConfigured: Boolean

    /** 從 Sheet 下載餐廳、品項、客製選項；成員名單只在本機為空時匯入 */
    suspend fun syncMasterData(): Result<Unit>

    /** 待上傳的訂單、照片、成員異動數量 */
    fun observePendingCount(): Flow<Int>

    fun retryUploadsNow()
}

sealed interface UploadOutcome {
    data object Uploaded : UploadOutcome

    /** 已存在本機，恢復網路後自動補送 */
    data class Queued(val reason: String) : UploadOutcome

    data class Failed(val reason: String) : UploadOutcome
}

/** 方便測試的時間來源 */
interface AppClock {
    fun today(): LocalDate
    fun nowMillis(): Long
}
