package de.easybolus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.easybolus.ui.HomeUiState
import de.easybolus.ui.theme.CobPillBg
import de.easybolus.ui.theme.FabTeal
import de.easybolus.ui.theme.HeaderGradientBottom
import de.easybolus.ui.theme.HeaderGradientBottomDark
import de.easybolus.ui.theme.HeaderGradientTop
import de.easybolus.ui.theme.HeaderGradientTopDark
import de.easybolus.ui.theme.IobPillBg
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenBolusSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val gradTop = if (isDark) HeaderGradientTopDark else HeaderGradientTop
    val gradBottom = if (isDark) HeaderGradientBottomDark else HeaderGradientBottom

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Kompakte Header-Karte bis hinter die Statusleiste
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(gradTop, gradBottom),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f)
                        )
                    )
                }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Einstellungen-Icon oben links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Einstellungen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Hauptzeile: Glukosewert + Trend links/mittig, IE & KH kompakt übereinander rechts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        val bgText = uiState.latestGlucose?.mgDl?.toString() ?: "--"
                        val arrowText = uiState.latestGlucose?.trendArrow?.symbol ?: ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bgText,
                                color = Color.White,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = (-2).sp
                            )
                            if (arrowText.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = arrowText,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }

                        val timeLabel = when (val mins = uiState.minutesAgo) {
                            null -> "Warte auf Juggluco-Broadcast…"
                            0L -> "gerade eben"
                            else -> "vor $mins min"
                        }
                        Text(
                            text = timeLabel,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp
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
                            backgroundColor = IobPillBg.copy(alpha = 0.85f)
                        )
                        StatusPill(
                            icon = Icons.Default.Grain,
                            text = String.format(Locale.GERMANY, "%.1f g", uiState.currentCob),
                            backgroundColor = CobPillBg.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Dual-Axis Chart füllt die gesamte nutzbare Fläche
        if (uiState.chartReadings.isEmpty() && uiState.chartEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }

        // Teal FAB (+) mittig unten mit genügend Platz für den Schatten
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
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
                modifier = Modifier.size(56.dp)
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
