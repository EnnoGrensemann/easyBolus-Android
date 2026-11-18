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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import de.easybolus.ui.theme.CardWhite
import de.easybolus.ui.theme.CobPillBg
import de.easybolus.ui.theme.FabTeal
import de.easybolus.ui.theme.HeaderGradientBottom
import de.easybolus.ui.theme.HeaderGradientTop
import de.easybolus.ui.theme.IobPillBg
import de.easybolus.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenBolusSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CardWhite)
    ) {
        // Obere Gradient-Karte mit diagonalem Blau-Teal-Verlauf
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(HeaderGradientTop, HeaderGradientBottom),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f)
                        )
                    )
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Einstellungen-Icon oben links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Einstellungen",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Großer Blutzuckerwert + Trendpfeil
                val bgText = uiState.latestGlucose?.mgDl?.toString() ?: "--"
                val arrowText = uiState.latestGlucose?.trendArrow?.symbol ?: ""
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = bgText,
                        color = Color.White,
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-2).sp
                    )
                    if (arrowText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = arrowText,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }

                // Zeitstempel ("vor X min")
                val timeLabel = when (val mins = uiState.minutesAgo) {
                    null -> "Warte auf Juggluco-Broadcast…"
                    0L -> "gerade eben"
                    else -> "vor $mins min"
                }
                Text(
                    text = timeLabel,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // IOB & COB Pill-Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(
                        icon = Icons.Default.Vaccines,
                        text = String.format(Locale.GERMANY, "%.2f IE", uiState.currentIob),
                        backgroundColor = IobPillBg,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        icon = Icons.Default.Grain,
                        text = String.format(Locale.GERMANY, "%.1f g", uiState.currentCob),
                        backgroundColor = CobPillBg,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    color = TextSecondary,
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

        // Teal FAB (+) mittig unten auf einer Trennlinie
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalDivider(
                color = Color(0xFFE2E8F0),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )
            FloatingActionButton(
                onClick = onOpenBolusSheet,
                containerColor = FabTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(58.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Bolus berechnen oder Eintrag hinzufügen",
                    modifier = Modifier.size(30.dp)
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
        modifier = modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
