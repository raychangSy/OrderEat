package com.symmetry.ordereat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RestaurantEntity::class,
        MenuItemEntity::class,
        OptionGroupEntity::class,
        MemberEntity::class,
        SessionEntity::class,
        OrderLineEntity::class,
        PendingPhotoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OrderEatDatabase : RoomDatabase() {
    abstract fun masterDataDao(): MasterDataDao
    abstract fun memberDao(): MemberDao
    abstract fun sessionDao(): SessionDao
    abstract fun pendingPhotoDao(): PendingPhotoDao

    companion object {
        /** v2：餐廳加 Google 地圖連結 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE restaurants ADD COLUMN mapUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3：餐廳加線上菜單連結 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE restaurants ADD COLUMN menuUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4：開單跟單 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN joinOpen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN openedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN fromExtension INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
