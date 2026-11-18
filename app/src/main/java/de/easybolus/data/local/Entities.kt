package de.easybolus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrendArrow(val symbol: String, val label: String) {
    DOUBLE_UP("⇈", "Stark steigend"),
    UP("↑", "Steigend"),
    UP_RIGHT("↗", "Leicht steigend"),
    FLAT("→", "Stabil"),
    DOWN_RIGHT("↘", "Leicht fallend"),
    DOWN("↓", "Fallend"),
    DOUBLE_DOWN("⇊", "Stark fallend"),
    UNKNOWN("?", "Unbekannt");

    companion object {
        fun fromRate(ratePerMin: Double): TrendArrow = when {
            ratePerMin >= 3.0 -> DOUBLE_UP
            ratePerMin >= 1.5 -> UP
            ratePerMin >= 0.75 -> UP_RIGHT
            ratePerMin > -0.75 -> FLAT
            ratePerMin > -1.5 -> DOWN_RIGHT
            ratePerMin > -3.0 -> DOWN
            else -> DOUBLE_DOWN
        }

        fun fromSlopeName(name: String?): TrendArrow = when (name?.trim()?.lowercase()) {
            "doubleup", "double_up" -> DOUBLE_UP
            "singleup", "up" -> UP
            "fortyfiveup", "up_right" -> UP_RIGHT
            "flat" -> FLAT
            "fortyfivedown", "down_right" -> DOWN_RIGHT
            "singledown", "down" -> DOWN
            "doubledown", "double_down" -> DOUBLE_DOWN
            else -> UNKNOWN
        }
    }
}

enum class DiaryEntryType(val label: String) {
    BOLUS("Bolus"),
    CARBS("Kohlenhydrate"),
    BASAL("Basal"),
    NOTE("Notiz")
}

@Entity(tableName = "glucose_readings")
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val mgDl: Int,
    val trendArrow: TrendArrow = TrendArrow.FLAT,
    val deltaMgDl: Double = 0.0,
    val source: String = "Juggluco"
)

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val type: DiaryEntryType,
    val bolusUnits: Double? = null,
    val carbsGrams: Double? = null,
    val basalUnits: Double? = null,
    val note: String? = null,
    val bgAtTimeMgDl: Int? = null,
    val trendAtTime: TrendArrow? = null
)
