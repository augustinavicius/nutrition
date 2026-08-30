package io.github.augustinavicius.nutrition.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.augustinavicius.nutrition.core.Goals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UpdateSettings(
    val autoCheck: Boolean = true,
    val owner: String,
    val repo: String,
    val lastCheckedAt: Long = 0L,
    /** Version the user chose to skip, so a declined update stops nagging. */
    val skippedVersionCode: Int = 0,
)

data class SyncSettings(
    val serverUrl: String = "",
    val username: String = "",
    val folder: String = "nutrition",
    val lastSyncedAt: Long = 0L,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank()
}

class SettingsStore(context: Context, private val defaultOwner: String, private val defaultRepo: String) {

    private val store = context.applicationContext.dataStore

    val goals: Flow<Goals> = store.data.map { prefs ->
        Goals(
            kcal = prefs[KEY_GOAL_KCAL] ?: DEFAULTS.kcal,
            proteinG = prefs.macroGoal(KEY_GOAL_PROTEIN, DEFAULTS.proteinG),
            carbsG = prefs.macroGoal(KEY_GOAL_CARBS, DEFAULTS.carbsG),
            fatG = prefs.macroGoal(KEY_GOAL_FAT, DEFAULTS.fatG),
        )
    }

    /**
     * An absent key means the goal was never set, so the starting suggestion applies.
     * A stored -1 means the user deliberately emptied the field, and must stay empty.
     */
    private fun Preferences.macroGoal(key: Preferences.Key<Int>, default: Int?): Int? =
        if (contains(key)) this[key]?.takeIf { it >= 0 } else default

    val updateSettings: Flow<UpdateSettings> = store.data.map { prefs ->
        UpdateSettings(
            autoCheck = prefs[KEY_AUTO_CHECK] ?: true,
            owner = prefs[KEY_OWNER]?.takeIf { it.isNotBlank() } ?: defaultOwner,
            repo = prefs[KEY_REPO]?.takeIf { it.isNotBlank() } ?: defaultRepo,
            lastCheckedAt = prefs[KEY_LAST_CHECKED] ?: 0L,
            skippedVersionCode = prefs[KEY_SKIPPED_VERSION] ?: 0,
        )
    }

    val syncSettings: Flow<SyncSettings> = store.data.map { prefs ->
        SyncSettings(
            serverUrl = prefs[KEY_SYNC_URL].orEmpty(),
            username = prefs[KEY_SYNC_USER].orEmpty(),
            folder = prefs[KEY_SYNC_FOLDER] ?: "nutrition",
            lastSyncedAt = prefs[KEY_SYNC_LAST_AT] ?: 0L,
        )
    }

    suspend fun setSyncServer(serverUrl: String, username: String, folder: String) =
        store.edit { prefs ->
            prefs[KEY_SYNC_URL] = serverUrl.trim()
            prefs[KEY_SYNC_USER] = username.trim()
            prefs[KEY_SYNC_FOLDER] = folder.trim().trim('/')
        }

    suspend fun setSyncLastAt(at: Long) = store.edit { it[KEY_SYNC_LAST_AT] = at }

    suspend fun setGoals(goals: Goals) {
        store.edit { prefs ->
            prefs[KEY_GOAL_KCAL] = goals.kcal
            prefs[KEY_GOAL_PROTEIN] = goals.proteinG ?: -1
            prefs[KEY_GOAL_CARBS] = goals.carbsG ?: -1
            prefs[KEY_GOAL_FAT] = goals.fatG ?: -1
        }
    }

    suspend fun setAutoCheck(enabled: Boolean) = store.edit { it[KEY_AUTO_CHECK] = enabled }

    suspend fun setRepository(owner: String, repo: String) = store.edit { prefs ->
        prefs[KEY_OWNER] = owner.trim()
        prefs[KEY_REPO] = repo.trim()
    }

    suspend fun setLastCheckedAt(at: Long) = store.edit { it[KEY_LAST_CHECKED] = at }

    suspend fun setSkippedVersionCode(versionCode: Int) = store.edit { it[KEY_SKIPPED_VERSION] = versionCode }

    private companion object {
        val DEFAULTS = Goals()

        val KEY_GOAL_KCAL = intPreferencesKey("goal_kcal")
        val KEY_GOAL_PROTEIN = intPreferencesKey("goal_protein")
        val KEY_GOAL_CARBS = intPreferencesKey("goal_carbs")
        val KEY_GOAL_FAT = intPreferencesKey("goal_fat")
        val KEY_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val KEY_OWNER = stringPreferencesKey("update_owner")
        val KEY_REPO = stringPreferencesKey("update_repo")
        val KEY_LAST_CHECKED = longPreferencesKey("update_last_checked")
        val KEY_SKIPPED_VERSION = intPreferencesKey("update_skipped_version")
        val KEY_SYNC_URL = stringPreferencesKey("sync_url")
        val KEY_SYNC_USER = stringPreferencesKey("sync_user")
        val KEY_SYNC_FOLDER = stringPreferencesKey("sync_folder")
        val KEY_SYNC_LAST_AT = longPreferencesKey("sync_last_at")
    }
}
