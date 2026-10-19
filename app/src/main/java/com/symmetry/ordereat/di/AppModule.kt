package com.symmetry.ordereat.di

import android.content.Context
import androidx.room.Room
import com.symmetry.ordereat.BuildConfig
import com.symmetry.ordereat.data.local.MasterDataDao
import com.symmetry.ordereat.data.local.MemberDao
import com.symmetry.ordereat.data.local.OrderEatDatabase
import com.symmetry.ordereat.data.local.PendingPhotoDao
import com.symmetry.ordereat.data.local.SessionDao
import com.symmetry.ordereat.data.remote.ApiConfig
import com.symmetry.ordereat.data.repository.MemberRepositoryImpl
import com.symmetry.ordereat.data.repository.MenuRepositoryImpl
import com.symmetry.ordereat.data.repository.SessionRepositoryImpl
import com.symmetry.ordereat.data.repository.SettingsRepositoryImpl
import com.symmetry.ordereat.data.repository.SyncRepositoryImpl
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MemberRepository
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Apps Script 冷啟動可能要好幾秒，上傳照片更久
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    fun provideApiConfig(): ApiConfig = ApiConfig(BuildConfig.SCRIPT_URL, BuildConfig.API_TOKEN)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OrderEatDatabase =
        Room.databaseBuilder(context, OrderEatDatabase::class.java, "order_eat.db")
            .addMigrations(OrderEatDatabase.MIGRATION_1_2, OrderEatDatabase.MIGRATION_2_3, OrderEatDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideMasterDataDao(db: OrderEatDatabase): MasterDataDao = db.masterDataDao()

    @Provides
    fun provideMemberDao(db: OrderEatDatabase): MemberDao = db.memberDao()

    @Provides
    fun provideSessionDao(db: OrderEatDatabase): SessionDao = db.sessionDao()

    @Provides
    fun providePendingPhotoDao(db: OrderEatDatabase): PendingPhotoDao = db.pendingPhotoDao()

    @Provides
    @Singleton
    fun provideClock(): AppClock = object : AppClock {
        private val zone = ZoneId.of("Asia/Taipei")
        override fun today(): LocalDate = LocalDate.now(zone)
        override fun nowMillis(): Long = System.currentTimeMillis()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindMenuRepository(impl: MenuRepositoryImpl): MenuRepository

    @Binds
    abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository

    @Binds
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}
