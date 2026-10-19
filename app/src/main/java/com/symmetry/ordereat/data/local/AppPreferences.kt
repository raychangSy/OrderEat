package com.symmetry.ordereat.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.symmetry.ordereat.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "order_eat")

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store get() = context.dataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            excludeRecentEnabled = prefs[EXCLUDE_ENABLED] ?: true,
            excludeDays = prefs[EXCLUDE_DAYS] ?: AppSettings.DEFAULT_EXCLUDE_DAYS,
            lastSyncAt = prefs[LAST_SYNC_AT],
            openerName = prefs[OPENER_NAME].orEmpty(),
        )
    }

    val membersDirty: Flow<Boolean> = store.data.map { it[MEMBERS_DIRTY] ?: false }

    suspend fun isMembersDirty(): Boolean = membersDirty.first()

    suspend fun setMembersDirty(dirty: Boolean) = set(MEMBERS_DIRTY, dirty)

    suspend fun setExcludeEnabled(enabled: Boolean) = set(EXCLUDE_ENABLED, enabled)

    suspend fun setExcludeDays(days: Int) = set(EXCLUDE_DAYS, days)

    suspend fun setLastSyncAt(millis: Long) = set(LAST_SYNC_AT, millis)

    suspend fun setOpenerName(name: String) = set(OPENER_NAME, name)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val EXCLUDE_ENABLED = booleanPreferencesKey("exclude_recent_enabled")
        val EXCLUDE_DAYS = intPreferencesKey("exclude_days")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val MEMBERS_DIRTY = booleanPreferencesKey("members_dirty")
        val OPENER_NAME = stringPreferencesKey("opener_name")
    }
}
