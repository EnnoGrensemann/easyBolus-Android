package de.easybolus

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.easybolus.domain.CsvExporter
import de.easybolus.ui.MainViewModel
import de.easybolus.ui.bolus.BolusAndEntrySheet
import de.easybolus.ui.bolus.BolusReminderData
import de.easybolus.ui.bolus.BolusReminderDialog
import de.easybolus.ui.diary.DiaryScreen
import de.easybolus.ui.home.HomeScreen
import de.easybolus.ui.settings.SettingsScreen
import de.easybolus.ui.theme.EasyBolusTheme
import de.easybolus.ui.theme.isEasyBolusDarkTheme

private enum class AppScreen {
    HOME, DIARY, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyBolusRoot(activity = this)
        }
    }
}

@Composable
private fun EasyBolusRoot(
    activity: ComponentActivity,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val homeState by viewModel.homeUiState.collectAsState()
    val diaryEntries by viewModel.allDiaryEntries.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var showBolusSheet by remember { mutableStateOf(false) }
    var bolusReminder by remember { mutableStateOf<BolusReminderData?>(null) }

    val isDark = isEasyBolusDarkTheme(settings.themeMode)

    DisposableEffect(isDark) {
        activity.enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
        )
        onDispose {}
    }

    EasyBolusTheme(themeMode = settings.themeMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (currentScreen == AppScreen.HOME) {
                    ExtendedFloatingActionButton(
                        onClick = { showBolusSheet = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.fab_entry_cd)
                            )
                        },
                        text = { Text(stringResource(R.string.fab_entry)) },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 6.dp
                        )
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.HOME,
                        onClick = { currentScreen = AppScreen.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_home)) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.DIARY,
                        onClick = { currentScreen = AppScreen.DIARY },
                        icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_diary)) },
                        label = { Text(stringResource(R.string.nav_diary)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SETTINGS,
                        onClick = { currentScreen = AppScreen.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        ) { innerPadding ->
            val bottomModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    uiState = homeState,
                    onSaveManualGlucose = { mgDl -> viewModel.saveManualGlucose(mgDl) },
                    modifier = bottomModifier
                )
                AppScreen.DIARY -> DiaryScreen(
                    entries = diaryEntries,
                    glucoseUnit = settings.glucoseUnit,
                    onExportCsv = { CsvExporter.shareCsv(context, diaryEntries) },
                    onUpdateEntry = { viewModel.updateDiaryEntry(it) },
                    onDeleteEntry = { viewModel.deleteDiaryEntry(it) },
                    modifier = bottomModifier
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    hasJugglucoData = homeState.hasJugglucoData,
                    onSaveSettings = { viewModel.updateSettings(it) },
                    onInsertDemoData = { viewModel.insertDemoData() },
                    onClearDemoData = { viewModel.clearDemoData() },
                    modifier = bottomModifier
                )
            }
        }

        if (showBolusSheet) {
            BolusAndEntrySheet(
                currentBgMgDl = homeState.latestGlucose?.mgDl,
                currentIob = homeState.currentIob,
                settings = settings,
                onDismiss = { showBolusSheet = false },
                onSaveBolusAndCarbs = { bolus, carbs, note, bg, timestampMillis ->
                    viewModel.saveBolusAndCarbs(bolus, carbs, note, bg, timestampMillis)
                    if (bolus > 0.0) {
                        bolusReminder = BolusReminderData(
                            bolusUnits = bolus,
                            carbsGrams = carbs.takeIf { it > 0.0 }
                        )
                    }
                },
                onAddQuickEntry = { type, bolus, carbs, basal, note, timestampMillis ->
                    viewModel.addQuickEntry(type, bolus, carbs, basal, note, timestampMillis)
                    if (bolus != null && bolus > 0.0) {
                        bolusReminder = BolusReminderData(
                            bolusUnits = bolus,
                            carbsGrams = carbs?.takeIf { it > 0.0 }
                        )
                    }
                },
                onSaveManualGlucose = { mgDl, timestampMillis ->
                    viewModel.saveManualGlucose(mgDl, timestampMillis)
                }
            )
        }

        bolusReminder?.let { reminder ->
            BolusReminderDialog(
                reminder = reminder,
                onDismiss = { bolusReminder = null }
            )
        }
    }
}
