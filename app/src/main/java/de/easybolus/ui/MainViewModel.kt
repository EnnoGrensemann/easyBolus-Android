package de.easybolus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.data.settings.UserSettings
import de.easybolus.domain.BolusCalculationResult
import de.easybolus.domain.BolusCalculator
import de.easybolus.domain.IobCobCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.sin

enum class StatsPeriod {
    TODAY,
    HOURS_24,
    DAYS_7,
    DAYS_90
}

data class PeriodStats(
    val readingCount: Int = 0,
    val averageBg: Double? = null,
    val gmiPercent: Double? = null,
    val timeInRangePercent: Double? = null,
    val timeBelowPercent: Double? = null,
    val timeAbovePercent: Double? = null,
    val totalBolusUnits: Double = 0.0,
    val avgDailyBolusUnits: Double? = null,
    val totalCarbsGrams: Double = 0.0
)

data class HomeUiState(
    val latestGlucose: GlucoseReadingEntity? = null,
    val latestDeltaMgDl: Int? = null,
    val minutesAgo: Long? = null,
    val chartReadings: List<GlucoseReadingEntity> = emptyList(),
    val chartEntries: List<DiaryEntryEntity> = emptyList(),
    val currentIob: Double = 0.0,
    val currentCob: Double = 0.0,
    val settings: UserSettings = UserSettings(),
    val chartWindowHours: Int = 6,
    val hasJugglucoData: Boolean = false,
    val statsByPeriod: Map<StatsPeriod, PeriodStats> = emptyMap()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = EasyBolusDatabase.getInstance(application).dao()
    private val settingsRepo = SettingsRepository(application)
    private val driveSyncPrefs = de.easybolus.data.sync.DriveSyncPreferences(application)

    val driveSyncState: StateFlow<de.easybolus.data.sync.DriveSyncState> = driveSyncPrefs.syncStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), de.easybolus.data.sync.DriveSyncState())

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    private val windowHours = 6
    private val maxChartHistoryDays = 7
    private val statsHistoryDays = 90
    private val sinceMillis = System.currentTimeMillis() - statsHistoryDays * 24L * 3600_000L

    val settingsFlow: StateFlow<UserSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allDiaryEntries: StateFlow<List<DiaryEntryEntity>> = dao.observeAllDiaryEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportReadings90d: StateFlow<List<GlucoseReadingEntity>> = dao.observeGlucoseSince(sinceMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeUiState: StateFlow<HomeUiState> = combine(
        dao.observeLatestGlucose(),
        reportReadings90d,
        allDiaryEntries,
        settingsRepo.settingsFlow,
        tickerFlow
    ) { latest, readings, entries, settings, now ->
        val chartCutoff = now - maxChartHistoryDays * 24L * 3600_000L
        val recentReadings = readings.filter { it.timestampMillis >= chartCutoff }
        val recentEntries = entries.filter { it.timestampMillis >= chartCutoff }.sortedBy { it.timestampMillis }
        val currentIob = IobCobCalculator.calculateExponentialIob(
            entries = entries,
            atTimeMillis = now,
            diaHours = settings.diaHours,
            peakMinutes = settings.insulinType.peakMinutes
        )
        val currentCob = IobCobCalculator.calculateLinearCob(entries, now, settings.carbAbsorptionHours)
        val minsAgo = latest?.let { ((now - it.timestampMillis) / 60_000L).coerceAtLeast(0L) }
        val latestDelta = latest?.let { cur ->
            val prev = readings.lastOrNull { it.timestampMillis < cur.timestampMillis }
            if (prev != null && (cur.timestampMillis - prev.timestampMillis) <= 30 * 60_000L) {
                cur.mgDl - prev.mgDl
            } else {
                null
            }
        }
        val hasJuggluco = readings.any { it.source != "Demo" && it.source != "Manual" } ||
            (latest != null && latest.source != "Demo" && latest.source != "Manual")

        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val statsMap = mapOf(
            StatsPeriod.TODAY to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = todayStart,
                toMillis = now,
                settings = settings,
                maxDaysForAvg = null
            ),
            StatsPeriod.HOURS_24 to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = now - 24L * 3600_000L,
                toMillis = now,
                settings = settings,
                maxDaysForAvg = null
            ),
            StatsPeriod.DAYS_7 to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = now - 7L * 24 * 3600_000L,
                toMillis = now,
                settings = settings,
                maxDaysForAvg = 7.0
            ),
            StatsPeriod.DAYS_90 to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = now - 90L * 24 * 3600_000L,
                toMillis = now,
                settings = settings,
                maxDaysForAvg = 90.0
            )
        )

        HomeUiState(
            latestGlucose = latest,
            latestDeltaMgDl = latestDelta,
            minutesAgo = minsAgo,
            chartReadings = recentReadings,
            chartEntries = recentEntries,
            currentIob = currentIob,
            currentCob = currentCob,
            settings = settings,
            chartWindowHours = windowHours,
            hasJugglucoData = hasJuggluco,
            statsByPeriod = statsMap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun computePeriodStats(
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>,
        fromMillis: Long,
        toMillis: Long,
        settings: UserSettings,
        maxDaysForAvg: Double?
    ): PeriodStats {
        val periodReadings = readings.filter { it.timestampMillis in fromMillis..toMillis }
        val periodEntries = entries.filter { it.timestampMillis in fromMillis..toMillis }

        val count = periodReadings.size
        val avgBg = if (count > 0) periodReadings.map { it.mgDl }.average() else null
        val gmi = avgBg?.let { 3.31 + 0.02392 * it }
        val belowCount = periodReadings.count { it.mgDl < settings.targetRangeMin }
        val aboveCount = periodReadings.count { it.mgDl > settings.targetRangeMax }
        val inRangeCount = count - belowCount - aboveCount

        val totalBolus = periodEntries.sumOf { it.bolusUnits ?: 0.0 }
        val totalCarbs = periodEntries.sumOf { it.carbsGrams ?: 0.0 }

        val avgDailyBolus = maxDaysForAvg?.let { maxDays ->
            val earliestEntry = periodEntries.minOfOrNull { it.timestampMillis }
            val earliestReading = periodReadings.minOfOrNull { it.timestampMillis }
            val earliest = listOfNotNull(earliestEntry, earliestReading).minOrNull()
            val spannedDays = if (earliest != null) {
                (((toMillis - earliest).toDouble() / (24.0 * 3600_000.0)).coerceIn(1.0, maxDays))
            } else {
                maxDays
            }
            totalBolus / spannedDays
        }

        return PeriodStats(
            readingCount = count,
            averageBg = avgBg,
            gmiPercent = gmi,
            timeInRangePercent = if (count > 0) inRangeCount * 100.0 / count else null,
            timeBelowPercent = if (count > 0) belowCount * 100.0 / count else null,
            timeAbovePercent = if (count > 0) aboveCount * 100.0 / count else null,
            totalBolusUnits = totalBolus,
            avgDailyBolusUnits = avgDailyBolus,
            totalCarbsGrams = totalCarbs
        )
    }

    fun calculateBolus(bgMgDl: Int, carbsGrams: Double): BolusCalculationResult {
        val state = homeUiState.value
        return BolusCalculator.calculate(
            currentBgMgDl = bgMgDl,
            carbsGrams = carbsGrams,
            activeIob = state.currentIob,
            settings = state.settings
        )
    }

    fun saveManualGlucose(mgDl: Int, timestampMillis: Long = System.currentTimeMillis()) {
        if (mgDl <= 0) return
        viewModelScope.launch {
            dao.insertGlucoseReading(
                GlucoseReadingEntity(
                    timestampMillis = timestampMillis,
                    mgDl = mgDl.coerceIn(20, 600),
                    trendArrow = TrendArrow.UNKNOWN,
                    deltaMgDl = 0.0,
                    source = "Manual"
                )
            )
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = true)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun saveBolusAndCarbs(
        bolusUnits: Double,
        carbsGrams: Double,
        note: String?,
        bgAtTime: Int?,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val state = homeUiState.value
            val matchedReading = state.chartReadings
                .minByOrNull { kotlin.math.abs(it.timestampMillis - timestampMillis) }
                ?.takeIf { kotlin.math.abs(it.timestampMillis - timestampMillis) <= 15 * 60_000L }
                ?: state.latestGlucose?.takeIf { kotlin.math.abs(it.timestampMillis - timestampMillis) <= 15 * 60_000L }
            val bg = bgAtTime ?: matchedReading?.mgDl
            val trend = if (bgAtTime != null && !state.settings.jugglucoEnabled) {
                TrendArrow.UNKNOWN
            } else {
                matchedReading?.trendArrow
            }

            if (!state.settings.jugglucoEnabled && bgAtTime != null && bgAtTime > 0) {
                dao.insertGlucoseReading(
                    GlucoseReadingEntity(
                        timestampMillis = timestampMillis,
                        mgDl = bgAtTime.coerceIn(20, 600),
                        trendArrow = TrendArrow.UNKNOWN,
                        deltaMgDl = 0.0,
                        source = "Manual"
                    )
                )
            }

            if (bolusUnits > 0.0) {
                dao.insertDiaryEntry(
                    DiaryEntryEntity(
                        timestampMillis = timestampMillis,
                        type = DiaryEntryType.BOLUS,
                        bolusUnits = bolusUnits,
                        note = note?.takeIf { it.isNotBlank() },
                        bgAtTimeMgDl = bg,
                        trendAtTime = trend
                    )
                )
                de.easybolus.notification.EasyBolusNotifier.resetCorrectionTimerOnBolus(getApplication())
            }
            if (carbsGrams > 0.0) {
                dao.insertDiaryEntry(
                    DiaryEntryEntity(
                        timestampMillis = timestampMillis + 1,
                        type = DiaryEntryType.CARBS,
                        carbsGrams = carbsGrams,
                        note = if (bolusUnits <= 0.0) note?.takeIf { it.isNotBlank() } else null,
                        bgAtTimeMgDl = bg,
                        trendAtTime = trend
                    )
                )
            }
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun addQuickEntry(
        type: DiaryEntryType,
        bolusUnits: Double? = null,
        carbsGrams: Double? = null,
        basalUnits: Double? = null,
        note: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val state = homeUiState.value
            val matchedReading = state.chartReadings
                .minByOrNull { kotlin.math.abs(it.timestampMillis - timestampMillis) }
                ?.takeIf { kotlin.math.abs(it.timestampMillis - timestampMillis) <= 15 * 60_000L }
                ?: state.latestGlucose?.takeIf { kotlin.math.abs(it.timestampMillis - timestampMillis) <= 15 * 60_000L }
            dao.insertDiaryEntry(
                DiaryEntryEntity(
                    timestampMillis = timestampMillis,
                    type = type,
                    bolusUnits = bolusUnits,
                    carbsGrams = carbsGrams,
                    basalUnits = basalUnits,
                    note = note?.takeIf { it.isNotBlank() },
                    bgAtTimeMgDl = matchedReading?.mgDl,
                    trendAtTime = matchedReading?.trendArrow
                )
            )
            if ((bolusUnits ?: 0.0) > 0.0) {
                de.easybolus.notification.EasyBolusNotifier.resetCorrectionTimerOnBolus(getApplication())
            }
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun updateDiaryEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            dao.updateDiaryEntry(entry)
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            dao.deleteDiaryEntry(entry)
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun updateSettings(newSettings: UserSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(newSettings)
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
        }
    }

    fun exportAllDataCsv(onReady: (List<GlucoseReadingEntity>, List<DiaryEntryEntity>) -> Unit) {
        viewModelScope.launch {
            val readings = dao.getAllGlucoseReadings()
            val entries = dao.getAllDiaryEntries()
            onReady(readings, entries)
        }
    }

    fun buildFullBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val currentSettings = settingsRepo.getCurrentSettings()
            val readings = dao.getAllGlucoseReadings()
            val entries = dao.getAllDiaryEntries()
            val json = de.easybolus.domain.BackupManager.serializeFullBackup(currentSettings, readings, entries)
            onReady(json)
        }
    }

    fun restoreFullBackupJson(json: String, onResult: (Boolean, Int, Int) -> Unit) {
        viewModelScope.launch {
            val payload = de.easybolus.domain.BackupManager.deserializeFullBackup(json)
            if (payload == null) {
                onResult(false, 0, 0)
                return@launch
            }
            payload.settings?.let { settingsRepo.updateSettings(it) }
            val diff = de.easybolus.domain.BackupMerger.computeDiff(
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
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
            de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
            onResult(true, diff.newReadings.size, diff.newEntries.size)
        }
    }

    fun onGoogleSignedIn(email: String, displayName: String?, photoUrl: String?) {
        viewModelScope.launch {
            driveSyncPrefs.setAccount(email, displayName, photoUrl)
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            driveSyncPrefs.setAutoSyncEnabled(enabled)
            if (enabled) {
                de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(getApplication())
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            de.easybolus.data.sync.GoogleAuthManager.signOut(getApplication())
            driveSyncPrefs.clearAccount()
        }
    }

    fun backupToDriveNow(
        accessTokenOverride: String? = null,
        onNeedsResolution: (android.app.PendingIntent) -> Unit,
        onFinished: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = de.easybolus.data.sync.DriveSyncManager.performBackup(getApplication(), accessTokenOverride)) {
                is de.easybolus.data.sync.DriveAuthTokenResult.Success -> onFinished(true, null)
                is de.easybolus.data.sync.DriveAuthTokenResult.NeedsResolution -> onNeedsResolution(res.pendingIntent)
                is de.easybolus.data.sync.DriveAuthTokenResult.Error -> onFinished(false, res.message)
            }
        }
    }

    fun restoreFromDriveNow(
        accessTokenOverride: String? = null,
        onNeedsResolution: (android.app.PendingIntent) -> Unit,
        onFinished: (de.easybolus.data.sync.ManualRestoreSummary) -> Unit
    ) {
        viewModelScope.launch {
            val token = if (!accessTokenOverride.isNullOrBlank()) {
                accessTokenOverride
            } else {
                when (val tokenRes = de.easybolus.data.sync.GoogleAuthManager.requestDriveAccessToken(getApplication())) {
                    is de.easybolus.data.sync.DriveAuthTokenResult.Success -> tokenRes.accessToken
                    is de.easybolus.data.sync.DriveAuthTokenResult.NeedsResolution -> {
                        onNeedsResolution(tokenRes.pendingIntent)
                        return@launch
                    }
                    is de.easybolus.data.sync.DriveAuthTokenResult.Error -> {
                        driveSyncPrefs.updateSyncStatus(de.easybolus.data.sync.SyncStatus.ERROR, tokenRes.message)
                        onFinished(de.easybolus.data.sync.ManualRestoreSummary(false, errorMessage = tokenRes.message))
                        return@launch
                    }
                }
            }
            val result = de.easybolus.data.sync.DriveSyncManager.performRestoreMerge(getApplication(), token)
            onFinished(result)
        }
    }

    fun insertDemoData() {
        viewModelScope.launch {
            dao.clearDemoGlucose()
            dao.clearDemoDiary()

            val now = System.currentTimeMillis()
            val readings = (0..72).map { step ->
                val ts = now - (72 - step) * 5 * 60_000L
                val wave = 135.0 + 35.0 * sin(step / 9.0) + (if (step > 60) (step - 60) * 4.0 else 0.0)
                val bg = wave.toInt().coerceIn(70, 235)
                val trend = when {
                    step > 64 -> TrendArrow.UP
                    step % 15 < 5 -> TrendArrow.UP_RIGHT
                    else -> TrendArrow.FLAT
                }
                GlucoseReadingEntity(
                    timestampMillis = ts,
                    mgDl = bg,
                    trendArrow = trend,
                    deltaMgDl = 0.0,
                    source = "Demo"
                )
            }
            dao.insertGlucoseReadings(readings)

            dao.insertDiaryEntry(
                DiaryEntryEntity(
                    timestampMillis = now - 4 * 3600_000L,
                    type = DiaryEntryType.BOLUS,
                    bolusUnits = 1.5,
                    note = "[Demo] Frühstück",
                    bgAtTimeMgDl = 118,
                    trendAtTime = TrendArrow.FLAT
                )
            )
            dao.insertDiaryEntry(
                DiaryEntryEntity(
                    timestampMillis = now - 4 * 3600_000L + 1000,
                    type = DiaryEntryType.CARBS,
                    carbsGrams = 30.0,
                    note = "[Demo] Haferflocken",
                    bgAtTimeMgDl = 118,
                    trendAtTime = TrendArrow.FLAT
                )
            )
            dao.insertDiaryEntry(
                DiaryEntryEntity(
                    timestampMillis = now - 60 * 60_000L,
                    type = DiaryEntryType.BOLUS,
                    bolusUnits = 2.0,
                    note = "[Demo] Mittagessen",
                    bgAtTimeMgDl = 132,
                    trendAtTime = TrendArrow.UP_RIGHT
                )
            )
            dao.insertDiaryEntry(
                DiaryEntryEntity(
                    timestampMillis = now - 60 * 60_000L + 1000,
                    type = DiaryEntryType.CARBS,
                    carbsGrams = 40.0,
                    note = "[Demo] Pasta",
                    bgAtTimeMgDl = 132,
                    trendAtTime = TrendArrow.UP_RIGHT
                )
            )
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            dao.clearDemoGlucose()
            dao.clearDemoDiary()
            de.easybolus.notification.EasyBolusNotifier.refreshAll(getApplication(), evaluateCorrection = false)
        }
    }
}
