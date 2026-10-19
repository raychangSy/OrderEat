package com.symmetry.ordereat.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categories: List<String>,
    val phone: String,
    val address: String,
    val note: String,
    val menuPhotoUrl: String,
    @ColumnInfo(defaultValue = "")
    val mapUrl: String,
    @ColumnInfo(defaultValue = "")
    val menuUrl: String,
    val sortOrder: Int,
)

@Entity(tableName = "menu_items", indices = [Index("restaurantId")])
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val price: Int,
    val optionGroupIds: List<String>,
    val sortOrder: Int,
)

@Serializable
data class OptionChoiceJson(val name: String, val price: Int)

@Entity(tableName = "option_groups")
data class OptionGroupEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val maxSelect: Int,
    val choices: List<OptionChoiceJson>,
    val sortOrder: Int,
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean,
    val updatedAt: Long,
    val sortOrder: Int,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    /** yyyy-MM-dd */
    val date: String,
    val mealType: String,
    val restaurantId: String?,
    val restaurantName: String,
    val note: String,
    val status: String,
    val submittedAt: Long?,
    val isRemoteOnly: Boolean,
    val remoteTotal: Int,
    val updatedAt: Long,
    /** 已開單、Chrome 插件可以跟單 */
    @ColumnInfo(defaultValue = "0")
    val joinOpen: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val openedBy: String = "",
)

@Serializable
data class SelectedOptionJson(
    val groupId: String,
    val groupName: String,
    val choiceName: String,
    val price: Int,
)

@Entity(
    tableName = "order_lines",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class OrderLineEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val memberId: String?,
    val memberName: String,
    val menuItemId: String?,
    val itemName: String,
    val selections: List<SelectedOptionJson>,
    val quantity: Int,
    val unitPrice: Int,
    val note: String,
    val createdAt: Long,
    /** 別人用 Chrome 插件跟單的明細：從 Sheet 帶回、手機上唯讀，送出時不會上傳 */
    @ColumnInfo(defaultValue = "0")
    val fromExtension: Boolean = false,
)

data class SessionWithLines(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val lines: List<OrderLineEntity>,
)

/** 菜單照片離線待上傳；一店一張 */
@Entity(tableName = "pending_photos")
data class PendingPhotoEntity(
    @PrimaryKey val restaurantId: String,
    val filePath: String,
    val createdAt: Long,
)
