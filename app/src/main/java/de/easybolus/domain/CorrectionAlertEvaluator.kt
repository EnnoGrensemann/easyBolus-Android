package de.easybolus.domain

import de.easybolus.data.settings.UserSettings

data class CorrectionAlertState(
    val conditionSinceMillis: Long = 0L,
    val lastAlertMillis: Long = 0L
)

data class CorrectionAlertDecision(
    val shouldNotify: Boolean,
    val recommendedCorrectionUnits: Double,
    val nextState: CorrectionAlertState
)

object CorrectionAlertEvaluator {

    const val COOLDOWN_MILLIS = 60L * 60_000L // 60 Minuten Pause nach einer Meldung

    fun evaluate(
        currentBgMgDl: Int,
        activeIob: Double,
        settings: UserSettings,
        previousState: CorrectionAlertState,
        nowMillis: Long
    ): CorrectionAlertDecision {
        if (!settings.correctionAlertEnabled || settings.isf <= 0.0) {
            return CorrectionAlertDecision(
                shouldNotify = false,
                recommendedCorrectionUnits = 0.0,
                nextState = previousState.copy(conditionSinceMillis = 0L)
            )
        }

        val rawCorrection = (currentBgMgDl - settings.targetBg) / settings.isf
        val netCorrection = (rawCorrection - activeIob).coerceAtLeast(0.0)

        if (netCorrection < settings.correctionAlertMinUnits) {
            // Wieder unter Schwelle -> Timer zurücksetzen
            return CorrectionAlertDecision(
                shouldNotify = false,
                recommendedCorrectionUnits = netCorrection,
                nextState = previousState.copy(conditionSinceMillis = 0L)
            )
        }

        val since = if (previousState.conditionSinceMillis > 0L) {
            previousState.conditionSinceMillis
        } else {
            nowMillis
        }
        val waitMillis = settings.correctionAlertWaitMinutes.coerceAtLeast(1) * 60_000L
        val waitedLongEnough = (nowMillis - since) >= waitMillis
        val cooldownPassed = (nowMillis - previousState.lastAlertMillis) >= COOLDOWN_MILLIS

        return if (waitedLongEnough && cooldownPassed) {
            val rounded = BolusCalculator.roundToPenStep(netCorrection, settings.penStepSize)
            CorrectionAlertDecision(
                shouldNotify = true,
                recommendedCorrectionUnits = rounded.coerceAtLeast(settings.correctionAlertMinUnits),
                nextState = CorrectionAlertState(
                    conditionSinceMillis = since,
                    lastAlertMillis = nowMillis
                )
            )
        } else {
            CorrectionAlertDecision(
                shouldNotify = false,
                recommendedCorrectionUnits = netCorrection,
                nextState = previousState.copy(conditionSinceMillis = since)
            )
        }
    }
}
