package de.easybolus.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.domain.BackupManager
import de.easybolus.domain.BackupMerger
import de.easybolus.notification.EasyBolusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class ManualRestoreSummary(
    val success: Boolean,
    val addedReadings: Int = 0,
    val addedEntries: Int = 0,
    val errorMessage: String? = null
)

object DriveSyncManager {

    private const val AUTO_SYNC_WORK_NAME = "easybolus_drive_auto_sync"
    private const val MIN_AUTO_SYNC_INTERVAL_MS = 5 * 60_000L
    private const val AUTO_SYNC_DEBOUNCE_SECONDS = 30L

    fun scheduleAutoSyncAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = DriveSyncPreferences(appContext)
            val state = prefs.getCurrentState()
            if (!state.isSignedIn || !state.autoSyncEnabled) return@launch

            val lastSync = state.lastSyncMillis ?: 0L
            val elapsed = System.currentTimeMillis() - lastSync
            val delaySec = if (elapsed < MIN_AUTO_SYNC_INTERVAL_MS) {
                ((MIN_AUTO_SYNC_INTERVAL_MS - elapsed) / 1000L).coerceAtLeast(AUTO_SYNC_DEBOUNCE_SECONDS)
            } else {
                AUTO_SYNC_DEBOUNCE_SECONDS
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                AUTO_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    suspend fun performBackup(
        context: Context,
        accessTokenOverride: String? = null
    ): DriveAuthTokenResult {
        val appContext = context.applicationContext
        val prefs = DriveSyncPreferences(appContext)
        val state = prefs.getCurrentState()
        if (!state.isSignedIn) {
            return DriveAuthTokenResult.Error("Not signed in")
        }

        val token = if (!accessTokenOverride.isNullOrBlank()) {
            accessTokenOverride
        } else {
            when (val tokenRes = GoogleAuthManager.requestDriveAccessToken(appContext)) {
                is DriveAuthTokenResult.Success -> tokenRes.accessToken
                is DriveAuthTokenResult.NeedsResolution -> return tokenRes
                is DriveAuthTokenResult.Error -> {
                    prefs.updateSyncStatus(SyncStatus.ERROR, message = tokenRes.message)
                    return tokenRes
                }
            }
        }

        prefs.updateSyncStatus(SyncStatus.SYNCING)

        val dao = EasyBolusDatabase.getInstance(appContext).dao()
        val settingsRepo = SettingsRepository(appContext)
        val settings = settingsRepo.getCurrentSettings()
        val readings = dao.getAllGlucoseReadings()
        val entries = dao.getAllDiaryEntries()
        val json = BackupManager.serializeFullBackup(settings, readings, entries)

        val uploadResult = GoogleDriveBackupClient.uploadBackup(
            accessToken = token,
            existingFileId = state.driveFileId,
            jsonContent = json
        )

        return uploadResult.fold(
            onSuccess = { meta ->
                prefs.updateSyncStatus(
                    status = SyncStatus.SUCCESS,
                    message = null,
                    lastSyncMillis = System.currentTimeMillis(),
                    driveFileId = meta.id
                )
                DriveAuthTokenResult.Success(token)
            },
            onFailure = { err ->
                val msg = err.localizedMessage ?: "Drive upload failed"
                prefs.updateSyncStatus(SyncStatus.ERROR, message = msg)
                DriveAuthTokenResult.Error(msg)
            }
        )
    }

    suspend fun performRestoreMerge(
        context: Context,
        accessToken: String
    ): ManualRestoreSummary {
        val appContext = context.applicationContext
        val prefs = DriveSyncPreferences(appContext)
        val state = prefs.getCurrentState()
        prefs.updateSyncStatus(SyncStatus.SYNCING)

        val fileMeta = if (!state.driveFileId.isNullOrBlank()) {
            DriveFileMeta(state.driveFileId, GoogleDriveBackupClient.BACKUP_FILE_NAME)
        } else {
            GoogleDriveBackupClient.findBackupFile(accessToken).getOrElse { err ->
                val msg = err.localizedMessage ?: "Drive search failed"
                prefs.updateSyncStatus(SyncStatus.ERROR, message = msg)
                return ManualRestoreSummary(success = false, errorMessage = msg)
            }
        }

        if (fileMeta == null) {
            val msg = "No backup file found in Google Drive"
            prefs.updateSyncStatus(SyncStatus.ERROR, message = msg)
            return ManualRestoreSummary(success = false, errorMessage = msg)
        }

        val json = GoogleDriveBackupClient.downloadBackup(accessToken, fileMeta.id).getOrElse { err ->
            val msg = err.localizedMessage ?: "Drive download failed"
            prefs.updateSyncStatus(SyncStatus.ERROR, message = msg)
            return ManualRestoreSummary(success = false, errorMessage = msg)
        }

        val payload = BackupManager.deserializeFullBackup(json)
            ?: return ManualRestoreSummary(success = false, errorMessage = "Invalid backup file").also {
                prefs.updateSyncStatus(SyncStatus.ERROR, message = "Invalid backup file")
            }

        val dao = EasyBolusDatabase.getInstance(appContext).dao()
        val settingsRepo = SettingsRepository(appContext)

        payload.settings?.let { settingsRepo.updateSettings(it) }
        val diff = BackupMerger.computeDiff(
            existingReadings = dao.getAllGlucoseReadings(),
            existingEntries = dao.getAllDiaryEntries(),
            incomingReadings = payload.readings,
            incomingEntries = payload.entries
        )

        if (diff.newReadings.isNotEmpty()) {
            dao.insertGlucoseReadings(diff.newReadings)
        }
        if (diff.newEntries.isNotEmpty()) {
            dao.insertDiaryEntries(diff.newEntries)
        }

        EasyBolusNotifier.refreshAll(appContext, evaluateCorrection = false)
        prefs.updateSyncStatus(
            status = SyncStatus.SUCCESS,
            message = null,
            lastSyncMillis = System.currentTimeMillis(),
            driveFileId = fileMeta.id
        )

        return ManualRestoreSummary(
            success = true,
            addedReadings = diff.newReadings.size,
            addedEntries = diff.newEntries.size
        )
    }
}

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (DriveSyncManager.performBackup(applicationContext)) {
            is DriveAuthTokenResult.Success -> Result.success()
            is DriveAuthTokenResult.NeedsResolution -> Result.failure()
            is DriveAuthTokenResult.Error -> Result.retry()
        }
    }
}
