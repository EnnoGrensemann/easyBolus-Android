package de.easybolus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import de.easybolus.ui.theme.FabTeal

private enum class AppScreen {
    HOME, DIARY, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            EasyBolusRoot()
        }
    }
}

@Composable
private fun EasyBolusRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val homeState by viewModel.homeUiState.collectAsState()
    val diaryEntries by viewModel.allDiaryEntries.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var showBolusSheet by remember { mutableStateOf(false) }
    var bolusReminder by remember { mutableStateOf<BolusReminderData?>(null) }

    EasyBolusTheme(themeMode = settings.themeMode) {
        if (currentScreen == AppScreen.SETTINGS) {
            SettingsScreen(
                settings = settings,
                hasJugglucoData = homeState.hasJugglucoData,
                onBack = { currentScreen = AppScreen.HOME },
                onSaveSettings = { viewModel.updateSettings(it) },
                onInsertDemoData = { viewModel.insertDemoData() },
                onClearDemoData = { viewModel.clearDemoData() }
            )
            return@EasyBolusTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.HOME,
                        onClick = { currentScreen = AppScreen.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Startseite") },
                        label = { Text("Startseite") }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.85f)
                            .height(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { showBolusSheet = true },
                            color = FabTeal,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(width = 58.dp, height = 46.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Bolus berechnen oder Eintrag hinzufügen",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.DIARY,
                        onClick = { currentScreen = AppScreen.DIARY },
                        icon = { Icon(Icons.Default.History, contentDescription = "Tagebuch") },
                        label = { Text("Tagebuch") }
                    )
                }
            }
        ) { innerPadding ->
            val bottomModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    uiState = homeState,
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    modifier = bottomModifier
                )
                AppScreen.DIARY -> DiaryScreen(
                    entries = diaryEntries,
                    onExportCsv = { CsvExporter.shareCsv(context, diaryEntries) },
                    onUpdateEntry = { viewModel.updateDiaryEntry(it) },
                    onDeleteEntry = { viewModel.deleteDiaryEntry(it) },
                    modifier = bottomModifier
                )
                AppScreen.SETTINGS -> Unit
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
                            carbsGrams = carbs?.takeIf { it > 0.0 }
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
