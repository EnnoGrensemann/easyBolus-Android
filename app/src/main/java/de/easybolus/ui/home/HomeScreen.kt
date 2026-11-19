package de.easybolus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.ui.HomeUiState
import de.easybolus.ui.PeriodStats
import de.easybolus.ui.StatsPeriod
import de.easybolus.ui.theme.BgHighOrange
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import de.easybolus.ui.theme.CobPillBg
import de.easybolus.ui.theme.FabTeal
import de.easybolus.ui.theme.HeaderGradientTop
import de.easybolus.ui.theme.HeaderGradientTopDark
import de.easybolus.ui.theme.IobPillBg
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenBolusSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.TODAY) }
    val currentStats = uiState.statsByPeriod[selectedPeriod] ?: PeriodStats()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "easyBOLUS",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Einstellungen",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) HeaderGradientTopDark else HeaderGradientTop
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Live-Werte-Karte über dem Chart (Elemente & Größe wie bisher)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        val latestBg = uiState.latestGlucose?.mgDl
                        val bgText = latestBg?.toString() ?: "--"
                        val arrowText = uiState.latestGlucose?.trendArrow?.symbol ?: ""
                        val statusDotColor = when {
                            latestBg == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            latestBg < uiState.settings.targetRangeMin -> BgLowRed
                            latestBg > uiState.settings.targetRangeMax -> BgHighOrange
                            else -> BgInRangeGreen
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bgText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = (-2).sp,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (arrowText.isNotEmpty()) {
                                    Text(
                                        text = arrowText,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                                Text(
                                    text = "mg/dl",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        val isStale = (uiState.minutesAgo ?: 0L) >= 15L
                        val timeLabel = when (val mins = uiState.minutesAgo) {
                            null -> "Warte auf Juggluco-Broadcast…"
                            0L -> "gerade eben"
                            else -> "vor $mins min"
                        }
                        Text(
                            text = timeLabel,
                            color = if (isStale) {
                                if (isDark) Color(0xFFFDE68A) else Color(0xFFD97706)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isStale) FontWeight.Medium else FontWeight.Normal
                        )
                    }

                    // Dezente IOB & COB Pill-Badges übereinander rechts
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        StatusPill(
                            icon = Icons.Default.Vaccines,
                            text = String.format(Locale.GERMANY, "%.2f IE", uiState.currentIob),
                            backgroundColor = IobPillBg
                        )
                        StatusPill(
                            icon = Icons.Default.Grain,
                            text = String.format(Locale.GERMANY, "%.1f g", uiState.currentCob),
                            backgroundColor = CobPillBg
                        )
                    }
                }
            }

            // 2. Kompakte Statistik-Leiste mit Zeitraum-Umschaltung (Heute / 24h / 7 Tage)
            StatsSummaryCard(
                stats = currentStats,
                selectedPeriod = selectedPeriod,
                onSelectPeriod = { selectedPeriod = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Dual-Axis Chart
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                if (uiState.chartReadings.isEmpty() && uiState.chartEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Verlaufsdaten vorhanden.\nAktiviere in Juggluco den Broadcast (glucodata.Minute / xDrip) oder lade Demo-Daten in den Einstellungen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    GlucoseDualAxisChart(
                        readings = uiState.chartReadings,
                        entries = uiState.chartEntries,
                        settings = uiState.settings,
                        windowHours = uiState.chartWindowHours,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }

            // 4. Teal FAB (+) mittig unten mit genügend Platz für den Schatten
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                FloatingActionButton(
                    onClick = onOpenBolusSheet,
                    containerColor = FabTeal,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp
                    ),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Bolus berechnen oder Eintrag hinzufügen",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsSummaryCard(
    stats: PeriodStats,
    selectedPeriod: StatsPeriod,
    onSelectPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kopfzeile: Titel + einfache Zeitraum-Buttons (Heute / 24h / 7 Tage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistik",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatsPeriod.entries.forEach { period ->
                        val active = period == selectedPeriod
                        Surface(
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { onSelectPeriod(period) }
                        ) {
                            Text(
                                text = period.label,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // TIR-Farbbalken + Low / Ziel / High Prozentwerte
            val lowPct = stats.timeBelowPercent ?: 0.0
            val inPct = stats.timeInRangePercent ?: 0.0
            val highPct = stats.timeAbovePercent ?: 0.0
            val hasReadings = stats.readingCount > 0

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (hasReadings) {
                        if (lowPct > 0.0) {
                            Box(
                                modifier = Modifier
                                    .weight(lowPct.toFloat().coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(BgLowRed)
                            )
                        }
                        if (inPct > 0.0) {
                            Box(
                                modifier = Modifier
                                    .weight(inPct.toFloat().coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(BgInRangeGreen)
                            )
                        }
                        if (highPct > 0.0) {
                            Box(
                                modifier = Modifier
                                    .weight(highPct.toFloat().coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(BgHighOrange)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (hasReadings) "Low ${lowPct.roundToInt()}%" else "Low --",
                        color = BgLowRed,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = if (hasReadings) "TIR ${inPct.roundToInt()}%" else "TIR --",
                        color = BgInRangeGreen,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = if (hasReadings) "High ${highPct.roundToInt()}%" else "High --",
                        color = BgHighOrange,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
                    )
                }
            }

            // 4 kompakte Kennzahlen: Ø BZ, GMI, Bolus gesamt (mit Ø/Tag bei 7T), KH gesamt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMetricItem(
                    label = "Ø BZ",
                    value = stats.averageBg?.let { "${it.roundToInt()}" } ?: "--",
                    subLabel = if (stats.averageBg != null) "mg/dl" else null,
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = "GMI",
                    value = stats.gmiPercent?.let { String.format(Locale.GERMANY, "%.1f%%", it) } ?: "--",
                    subLabel = "eHbA1c",
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = "Bolus",
                    value = String.format(Locale.GERMANY, "%.1f IE", stats.totalBolusUnits),
                    subLabel = stats.avgDailyBolusUnits?.let {
                        String.format(Locale.GERMANY, "Ø %.1f/T", it)
                    },
                    modifier = Modifier.weight(1f)
                )
                StatMetricItem(
                    label = "KH",
                    value = "${stats.totalCarbsGrams.roundToInt()} g",
                    subLabel = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatMetricItem(
    label: String,
    value: String,
    subLabel: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            )
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontSize = 9.sp, fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .width(114.dp)
            .height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = Color.White,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}
