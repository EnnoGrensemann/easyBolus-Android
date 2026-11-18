package de.easybolus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import de.easybolus.domain.CsvExporter
import de.easybolus.ui.MainViewModel
import de.easybolus.ui.bolus.BolusAndEntrySheet
import de.easybolus.ui.diary.DiaryScreen
import de.easybolus.ui.home.HomeScreen
import de.easybolus.ui.settings.SettingsScreen
import de.easybolus.ui.theme.EasyBolusTheme

private enum class AppScreen {
    HOME, DIARY, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyBolusTheme {
                EasyBolusRoot()
            }
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

    if (currentScreen == AppScreen.SETTINGS) {
        SettingsScreen(
            settings = settings,
            onBack = { currentScreen = AppScreen.HOME },
            onSaveSettings = { viewModel.updateSettings(it) },
            onInsertDemoData = { viewModel.insertDemoData() },
            onClearDemoData = { viewModel.clearDemoData() }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.HOME,
                    onClick = { currentScreen = AppScreen.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Startseite") },
                    label = { Text("Startseite") }
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DIARY,
                    onClick = { currentScreen = AppScreen.DIARY },
                    icon = { Icon(Icons.Default.History, contentDescription = "Tagebuch") },
                    label = { Text("Tagebuch") }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                uiState = homeState,
                onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                onOpenBolusSheet = { showBolusSheet = true },
                modifier = Modifier.padding(innerPadding)
            )
            AppScreen.DIARY -> DiaryScreen(
                entries = diaryEntries,
                onExportCsv = { CsvExporter.shareCsv(context, diaryEntries) },
                onUpdateEntry = { viewModel.updateDiaryEntry(it) },
                onDeleteEntry = { viewModel.deleteDiaryEntry(it) },
                modifier = Modifier.padding(innerPadding)
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
            onSaveBolusAndCarbs = { bolus, carbs, note, bg ->
                viewModel.saveBolusAndCarbs(bolus, carbs, note, bg)
            },
            onAddQuickEntry = { type, bolus, carbs, basal, note ->
                viewModel.addQuickEntry(type, bolus, carbs, basal, note)
            }
        )
    }
}
