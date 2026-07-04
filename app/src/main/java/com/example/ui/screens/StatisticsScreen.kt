package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackingSession
import com.example.ui.components.ModernBarChart
import com.example.ui.components.ModernLineChart
import com.example.ui.components.ProgressRing
import com.example.viewmodel.TrackingViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: TrackingViewModel,
    modifier: Modifier = Modifier
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val streakStats by viewModel.streakStats.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("10 Days", "30 Days", "90 Days", "Lifetime")

    val filteredSessions = remember(completedSessions, selectedTabIndex) {
        val now = LocalDate.now()
        val limitDate = when (selectedTabIndex) {
            0 -> now.minusDays(10)
            1 -> now.minusDays(30)
            2 -> now.minusDays(90)
            else -> null
        }
        if (limitDate == null) {
            completedSessions
        } else {
            completedSessions.filter {
                try {
                    val date = LocalDate.parse(it.dateString, DateTimeFormatter.ISO_LOCAL_DATE)
                    !date.isBefore(limitDate)
                } catch (e: Exception) {
                    true
                }
            }
        }
    }

    // Calculations
    val totalHours = filteredSessions.sumOf { (it.endTime - it.startTime).toDouble() } / (1000.0 * 60.0 * 60.0)
    val sessionCount = filteredSessions.size
    val avgHoursPerSession = if (sessionCount > 0) totalHours / sessionCount else 0.0

    // Group by day to get Best Day
    val dailyHours = filteredSessions.groupBy { it.dateString }.mapValues { entry ->
        entry.value.sumOf { (it.endTime - it.startTime).toDouble() } / (1000.0 * 60.0 * 60.0)
    }
    val bestDayEntry = dailyHours.maxByOrNull { it.value }
    val bestDayHours = bestDayEntry?.value ?: 0.0
    val bestDayDate = bestDayEntry?.key ?: "N/A"

    // Quality distribution
    val goodCount = filteredSessions.count { it.quality == "GOOD" }
    val normalCount = filteredSessions.count { it.quality == "NORMAL" }
    val badCount = filteredSessions.count { it.quality == "BAD" }

    // Category breakdown
    val categoryBreakdown = filteredSessions.groupBy { it.trackerType }.mapValues { entry ->
        entry.value.sumOf { (it.endTime - it.startTime).toDouble() } / (1000.0 * 60.0 * 60.0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TabRow selector
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }
            }

            if (filteredSessions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No Data",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No completed sessions for this period yet.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Key metrics Grid
                item {
                    Text(
                        text = "Overview",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            label = "Total Tracked",
                            value = "${String.format(Locale.US, "%.1f", totalHours)} hrs",
                            subText = "$sessionCount sessions",
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        MetricCard(
                            label = "Average",
                            value = "${String.format(Locale.US, "%.1f", avgHoursPerSession)} hrs",
                            subText = "per activity",
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            label = "Best Daily Total",
                            value = "${String.format(Locale.US, "%.1f", bestDayHours)} hrs",
                            subText = bestDayDate,
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                        MetricCard(
                            label = "Max Streak",
                            value = "${streakStats.longestStreak} Days",
                            subText = "longest ever",
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    }
                }

                // Trend Chart
                item {
                    Text(
                        text = "Daily Trend",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        // Generate last 7 days metrics
                        val last7Days = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
                        val chartData = last7Days.map { date ->
                            dailyHours[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]?.toFloat() ?: 0f
                        }
                        val chartLabels = last7Days.map { date ->
                            date.format(DateTimeFormatter.ofPattern("E", Locale.getDefault()))
                        }

                        ModernLineChart(
                            data = chartData,
                            labels = chartLabels,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                // Quality Breakdown and Category Distribution Row
                item {
                    Text(
                        text = "Quality Distribution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        ModernBarChart(
                            data = listOf(goodCount.toFloat(), normalCount.toFloat(), badCount.toFloat()),
                            labels = listOf("😊 Good", "😐 Normal", "😞 Bad"),
                            barColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                // Category Breakdown
                item {
                    Text(
                        text = "Category Distribution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val sortedCategories = categoryBreakdown.entries.sortedByDescending { it.value }
                            sortedCategories.forEach { (category, hours) ->
                                val percentage = (hours / totalHours).toFloat().coerceIn(0f, 1f)
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = category,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", hours)} hrs (${(percentage * 100).toInt()}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        strokeCap = StrokeCap.Round,
                                        color = when(category) {
                                            "Sleep" -> Color(0xFF3F51B5)
                                            "Study" -> Color(0xFF4CAF50)
                                            "Productivity" -> Color(0xFFFF9800)
                                            "Workout" -> Color(0xFFE91E63)
                                            "Physical Activity" -> Color(0xFF00BCD4)
                                            "Training" -> Color(0xFF9C27B0)
                                            else -> Color(0xFFFF5722)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    subText: String,
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
