package de.easybolus.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driveSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "easybolus_drive_sync")

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

data class DriveSyncState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val photoUrl: String? = null,
    val autoSyncEnabled: Boolean = true,
    val lastSyncMillis: Long? = null,
    val lastSyncStatus: SyncStatus = SyncStatus.IDLE,
    val lastSyncMessage: String? = null,
    val driveFileId: String? = null
)

class DriveSyncPreferences(private val context: Context) {

    private object Keys {
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ACCOUNT_NAME = stringPreferencesKey("account_name")
        val PHOTO_URL = stringPreferencesKey("photo_url")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val DRIVE_FILE_ID = stringPreferencesKey("drive_file_id")
    }

    val syncStateFlow: Flow<DriveSyncState> = context.driveSyncDataStore.data.map { prefs ->
        val email = prefs[Keys.ACCOUNT_EMAIL]
        val status = prefs[Keys.LAST_SYNC_STATUS]
            ?.let { raw -> SyncStatus.entries.firstOrNull { it.name == raw } }
            ?: SyncStatus.IDLE
        DriveSyncState(
            isSignedIn = !email.isNullOrBlank(),
            accountEmail = email,
            accountName = prefs[Keys.ACCOUNT_NAME],
            photoUrl = prefs[Keys.PHOTO_URL],
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS],
            lastSyncStatus = status,
            lastSyncMessage = prefs[Keys.LAST_SYNC_MESSAGE],
            driveFileId = prefs[Keys.DRIVE_FILE_ID]
        )
    }

    suspend fun getCurrentState(): DriveSyncState = syncStateFlow.first()

    suspend fun setAccount(email: String, name: String?, photoUrl: String?) {
        context.driveSyncDataStore.edit { prefs ->
            prefs[Keys.ACCOUNT_EMAIL] = email
            if (name != null) prefs[Keys.ACCOUNT_NAME] = name else prefs.remove(Keys.ACCOUNT_NAME)
            if (photoUrl != null) prefs[Keys.PHOTO_URL] = photoUrl else prefs.remove(Keys.PHOTO_URL)
            prefs[Keys.LAST_SYNC_STATUS] = SyncStatus.IDLE.name
            prefs.remove(Keys.LAST_SYNC_MESSAGE)
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.driveSyncDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun updateSyncStatus(
        status: SyncStatus,
        message: String? = null,
        lastSyncMillis: Long? = null,
        driveFileId: String? = null
    ) {
        context.driveSyncDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_STATUS] = status.name
            if (message != null) prefs[Keys.LAST_SYNC_MESSAGE] = message else prefs.remove(Keys.LAST_SYNC_MESSAGE)
            if (lastSyncMillis != null) prefs[Keys.LAST_SYNC_MILLIS] = lastSyncMillis
            if (driveFileId != null) prefs[Keys.DRIVE_FILE_ID] = driveFileId
        }
    }

    suspend fun clearAccount() {
        context.driveSyncDataStore.edit { prefs ->
            prefs.remove(Keys.ACCOUNT_EMAIL)
            prefs.remove(Keys.ACCOUNT_NAME)
            prefs.remove(Keys.PHOTO_URL)
            prefs.remove(Keys.LAST_SYNC_MILLIS)
            prefs.remove(Keys.LAST_SYNC_STATUS)
            prefs.remove(Keys.LAST_SYNC_MESSAGE)
            prefs.remove(Keys.DRIVE_FILE_ID)
        }
    }
}
