package com.symmetry.ordereat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDataDao {
    @Query("SELECT * FROM restaurants ORDER BY sortOrder")
    fun observeRestaurants(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    fun observeRestaurant(id: String): Flow<RestaurantEntity?>

    @Query("SELECT * FROM menu_items WHERE restaurantId = :restaurantId ORDER BY sortOrder")
    fun observeMenuItems(restaurantId: String): Flow<List<MenuItemEntity>>

    /** 該店專用 + 通用（restaurantId = ''） */
    @Query("SELECT * FROM option_groups WHERE restaurantId = :restaurantId OR restaurantId = '' ORDER BY sortOrder")
    fun observeOptionGroups(restaurantId: String): Flow<List<OptionGroupEntity>>

    @Query("UPDATE restaurants SET menuPhotoUrl = :url WHERE id = :id")
    suspend fun updatePhotoUrl(id: String, url: String)

    @Query("DELETE FROM restaurants")
    suspend fun clearRestaurants()

    @Query("DELETE FROM menu_items")
    suspend fun clearMenuItems()

    @Query("DELETE FROM option_groups")
    suspend fun clearOptionGroups()

    @Insert
    suspend fun insertRestaurants(items: List<RestaurantEntity>)

    @Insert
    suspend fun insertMenuItems(items: List<MenuItemEntity>)

    @Insert
    suspend fun insertOptionGroups(items: List<OptionGroupEntity>)

    @Transaction
    suspend fun replaceAll(
        restaurants: List<RestaurantEntity>,
        menuItems: List<MenuItemEntity>,
        optionGroups: List<OptionGroupEntity>,
    ) {
        clearRestaurants()
        clearMenuItems()
        clearOptionGroups()
        insertRestaurants(restaurants)
        insertMenuItems(menuItems)
        insertOptionGroups(optionGroups)
    }
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY sortOrder, name")
    suspend fun getAll(): List<MemberEntity>

    @Query("SELECT COUNT(*) FROM members")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM members")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insertAll(members: List<MemberEntity>)

    @Upsert
    suspend fun upsert(member: MemberEntity)
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date DESC, updatedAt DESC")
    fun observeAllWithLines(): Flow<List<SessionWithLines>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeWithLines(id: String): Flow<SessionWithLines?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getWithLines(id: String): SessionWithLines?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT id FROM sessions WHERE status = :status")
    suspend fun idsWithStatus(status: String): List<String>

    /** 以 Sheet 為準的團：上傳過（含開團中、改過未重送）或從雲端匯入的記錄。從沒上傳過的不算 */
    @Query("SELECT id FROM sessions WHERE isRemoteOnly = 1 OR submittedAt IS NOT NULL")
    suspend fun syncedIds(): List<String>

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("SELECT id FROM sessions WHERE joinOpen = 1")
    suspend fun joinOpenIds(): List<String>

    @Query("UPDATE sessions SET joinOpen = 0 WHERE id = :id")
    suspend fun markJoinClosed(id: String)

    @Query("SELECT id FROM sessions WHERE isRemoteOnly = 0")
    suspend fun localIds(): List<String>

    @Query("SELECT COUNT(*) FROM sessions WHERE status = :status")
    fun observeCountWithStatus(status: String): Flow<Int>

    @Insert
    suspend fun insert(session: SessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("UPDATE sessions SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    /** 只有仍在等待上傳時才標成已送出（上傳途中被修改就保持「修改中」） */
    @Query("UPDATE sessions SET status = 'SUBMITTED', submittedAt = :submittedAt WHERE id = :id AND status = 'PENDING_UPLOAD'")
    suspend fun markSubmittedIfPending(id: String, submittedAt: Long)

    @Query("UPDATE sessions SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun updateNote(id: String, note: String, now: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sessions SET joinOpen = :open, openedBy = :openedBy, updatedAt = :now WHERE id = :id")
    suspend fun updateJoin(id: String, open: Boolean, openedBy: String, now: Long)

    @Query("DELETE FROM order_lines WHERE sessionId = :sessionId AND fromExtension = 1")
    suspend fun deleteExtensionLines(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<OrderLineEntity>)

    /** 插件跟單的明細以 Sheet 為準，整批換掉 */
    @Transaction
    suspend fun replaceExtensionLines(sessionId: String, lines: List<OrderLineEntity>) {
        deleteExtensionLines(sessionId)
        if (lines.isNotEmpty()) insertLines(lines)
    }

    @Upsert
    suspend fun upsertLine(line: OrderLineEntity)

    @Query("DELETE FROM order_lines WHERE id = :lineId")
    suspend fun deleteLine(lineId: String)
}

@Dao
interface PendingPhotoDao {
    @Query("SELECT * FROM pending_photos")
    fun observeAll(): Flow<List<PendingPhotoEntity>>

    @Query("SELECT * FROM pending_photos")
    suspend fun getAll(): List<PendingPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: PendingPhotoEntity)

    @Query("DELETE FROM pending_photos WHERE restaurantId = :restaurantId AND filePath = :filePath")
    suspend fun deleteIfSame(restaurantId: String, filePath: String)
}
