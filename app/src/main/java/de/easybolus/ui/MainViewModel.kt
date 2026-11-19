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

enum class StatsPeriod(val label: String) {
    TODAY("Heute"),
    HOURS_24("24h"),
    DAYS_7("7 Tage")
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

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    private val windowHours = 6
    private val maxChartHistoryHours = 24
    private val statsHistoryDays = 7
    private val sinceMillis = System.currentTimeMillis() - statsHistoryDays * 24 * 3600_000L

    val settingsFlow: StateFlow<UserSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allDiaryEntries: StateFlow<List<DiaryEntryEntity>> = dao.observeAllDiaryEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeUiState: StateFlow<HomeUiState> = combine(
        dao.observeLatestGlucose(),
        dao.observeGlucoseSince(sinceMillis),
        dao.observeAllDiaryEntries(),
        settingsRepo.settingsFlow,
        tickerFlow
    ) { latest, readings, entries, settings, now ->
        val chartCutoff = now - maxChartHistoryHours * 3600_000L
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
        val hasJuggluco = readings.any { it.source != "Demo" } || (latest != null && latest.source != "Demo")

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
                daysForDailyAvg = null
            ),
            StatsPeriod.HOURS_24 to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = now - 24 * 3600_000L,
                toMillis = now,
                settings = settings,
                daysForDailyAvg = null
            ),
            StatsPeriod.DAYS_7 to computePeriodStats(
                readings = readings,
                entries = entries,
                fromMillis = now - 7L * 24 * 3600_000L,
                toMillis = now,
                settings = settings,
                daysForDailyAvg = 7.0
            )
        )

        HomeUiState(
            latestGlucose = latest,
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
        daysForDailyAvg: Double?
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

        return PeriodStats(
            readingCount = count,
            averageBg = avgBg,
            gmiPercent = gmi,
            timeInRangePercent = if (count > 0) inRangeCount * 100.0 / count else null,
            timeBelowPercent = if (count > 0) belowCount * 100.0 / count else null,
            timeAbovePercent = if (count > 0) aboveCount * 100.0 / count else null,
            totalBolusUnits = totalBolus,
            avgDailyBolusUnits = daysForDailyAvg?.let { totalBolus / it },
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
            val trend = matchedReading?.trendArrow

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
        }
    }

    fun updateDiaryEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            dao.updateDiaryEntry(entry)
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            dao.deleteDiaryEntry(entry)
        }
    }

    fun updateSettings(newSettings: UserSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(newSettings)
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
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            dao.clearDemoGlucose()
            dao.clearDemoDiary()
        }
    }
}
