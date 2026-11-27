package de.easybolus.car

import android.content.Intent
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.easybolus.R
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.domain.IobCobCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class EasyBolusCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = EasyBolusCarSession()
}

class EasyBolusCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = EasyBolusCarScreen(carContext)
}

class EasyBolusCarScreen(carContext: CarContext) : Screen(carContext) {

    private var surface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var surfaceDpi: Int = 0
    private var visibleArea: Rect? = null
    private var latestState: CarDashboardState = CarDashboardState()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
            surfaceWidth = surfaceContainer.width
            surfaceHeight = surfaceContainer.height
            surfaceDpi = surfaceContainer.dpi
            renderSurface()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surface = null
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            this@EasyBolusCarScreen.visibleArea = visibleArea
            renderSurface()
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                startObserving()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                observerJob?.cancel()
                scope.cancel()
            }
        })
    }

    private fun startObserving() {
        observerJob?.cancel()
        val dao = EasyBolusDatabase.getInstance(carContext).dao()
        val settingsRepo = SettingsRepository(carContext)
        val sinceMillis = System.currentTimeMillis() - 6L * 3600_000L
        val tickerFlow = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(30_000L)
            }
        }

        observerJob = scope.launch {
            combine(
                dao.observeLatestGlucose(),
                dao.observeGlucoseSince(sinceMillis),
                dao.observeAllDiaryEntries(),
                settingsRepo.settingsFlow,
                tickerFlow
            ) { latest, readings, entries, settings, now ->
                val minsAgo = latest?.let { ((now - it.timestampMillis) / 60_000L).coerceAtLeast(0L) }
                val delta = latest?.let { cur ->
                    val prev = readings.lastOrNull { it.timestampMillis < cur.timestampMillis }
                    if (prev != null && (cur.timestampMillis - prev.timestampMillis) <= 30 * 60_000L) {
                        cur.mgDl - prev.mgDl
                    } else null
                }
                val iob = IobCobCalculator.calculateExponentialIob(
                    entries = entries,
                    atTimeMillis = now,
                    diaHours = settings.diaHours,
                    peakMinutes = settings.insulinType.peakMinutes
                )
                val cob = IobCobCalculator.calculateLinearCob(
                    entries = entries,
                    atTimeMillis = now,
                    absorptionHours = settings.carbAbsorptionHours
                )
                CarDashboardState(
                    latestGlucose = latest,
                    deltaMgDl = delta,
                    minutesAgo = minsAgo,
                    readings = readings,
                    entries = entries,
                    currentIob = iob,
                    currentCob = cob,
                    settings = settings
                )
            }.collect { state ->
                latestState = state
                renderSurface()
            }
        }
    }

    private fun renderSurface() {
        val target = surface ?: return
        if (!target.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val canvas = runCatching { target.lockCanvas(null) }.getOrNull() ?: return
        try {
            CarDashboardRenderer.draw(
                canvas = canvas,
                width = surfaceWidth,
                height = surfaceHeight,
                dpi = surfaceDpi,
                visibleArea = visibleArea,
                state = latestState,
                context = carContext
            )
        } finally {
            runCatching { target.unlockCanvasAndPost(canvas) }
        }
    }

    override fun onGetTemplate(): Template {
        val refreshAction = Action.Builder()
            .setTitle(carContext.getString(R.string.car_action_refresh))
            .setOnClickListener { renderSurface() }
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(refreshAction)
                    .build()
            )
            .build()
    }
}
