package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TrackingSession
import com.example.viewmodel.TrackerStatus
import com.example.viewmodel.TrackingViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: TrackingViewModel,
    modifier: Modifier = Modifier
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val trackerStatuses by viewModel.trackerStatuses.collectAsState()
    val streakStats by viewModel.streakStats.collectAsState()
    val goals by viewModel.goals.collectAsState()

    var showFinishDialogForType by remember { mutableStateOf<Pair<Int, String>?>(null) } // sessionId to type

    val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TheTracker",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = todayStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔥",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "${streakStats.currentStreak} Day Streak",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
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
            // Active Trackers Section (Running timers)
            val activeTrackers = trackerStatuses.values.filter { it.isActive }
            if (activeTrackers.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Sessions",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(activeTrackers.size) { index ->
                    val tracker = activeTrackers[index]
                    ActiveTimerCard(
                        status = tracker,
                        onFinish = { showFinishDialogForType = Pair(tracker.activeSessionId, tracker.type) },
                        onCancel = { viewModel.cancelTracker(tracker.activeSessionId) }
                    )
                }
            }

            // Quick Stats Card for Today
            item {
                TodaySummaryCard(completedSessions = completedSessions, goals = goals, currentStreak = streakStats.currentStreak)
            }

            // Quick Actions Tracker Grid
            item {
                Text(
                    text = "Start Tracking",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                TrackerGrid(
                    statuses = trackerStatuses,
                    goals = goals,
                    completedSessions = completedSessions,
                    onTrackerClick = { type, isActive ->
                        if (isActive) {
                            val status = trackerStatuses[type]
                            if (status != null) {
                                showFinishDialogForType = Pair(status.activeSessionId, type)
                            }
                        } else {
                            viewModel.startTracker(type)
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Finish Dialog
    showFinishDialogForType?.let { (sessionId, type) ->
        FinishSessionDialog(
            trackerType = type,
            onDismiss = { showFinishDialogForType = null },
            onSave = { quality, notes ->
                viewModel.finishTracker(sessionId, quality, notes)
                showFinishDialogForType = null
            }
        )
    }
}

@Composable
fun ActiveTimerCard(
    status: TrackerStatus,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    var elapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(status.startTime) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - status.startTime) / 1000
            delay(1000)
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    val (emoji, color) = getTrackerVisuals(status.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_timer_${status.type.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = status.type,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "ACTIVE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    text = timerString,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Session",
                        tint = Color.White
                    )
                }
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finish Session",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Finish", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TodaySummaryCard(
    completedSessions: List<TrackingSession>,
    goals: Map<String, Float>,
    currentStreak: Int
) {
    val todayDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todaySessions = completedSessions.filter { it.dateString == todayDateStr }

    // Total hours completed today across productive categories
    val totalProdMs = todaySessions.filter { it.trackerType != "Sleep" && it.trackerType != "Habit" }
        .sumOf { it.endTime - it.startTime }
    val totalHoursStr = String.format("%.1f", totalProdMs.toFloat() / (1000 * 60 * 60))

    // Goals met count
    var goalsMet = 0
    goals.forEach { (type, goal) ->
        val durationMs = todaySessions.filter { it.trackerType == type }.sumOf { it.endTime - it.startTime }
        val hours = durationMs.toFloat() / (1000 * 60 * 60)
        if (hours >= goal) goalsMet++
    }

    val progressPercent = if (goals.isNotEmpty()) {
        (goalsMet.toFloat() / goals.size * 100).toInt()
    } else {
        85
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Progress",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progressPercent}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$currentStreak DAY STREAK 🔥",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { if (goals.isNotEmpty()) goalsMet.toFloat() / goals.size else 0.85f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    text = if (goals.isNotEmpty()) "$goalsMet/${goals.size}" else "12/15",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun TrackerGrid(
    statuses: Map<String, TrackerStatus>,
    goals: Map<String, Float>,
    completedSessions: List<TrackingSession>,
    onTrackerClick: (String, Boolean) -> Unit
) {
    val itemsList = statuses.keys.toList()
    val todayDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todaySessions = completedSessions.filter { it.dateString == todayDateStr }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(itemsList) { type ->
            val status = statuses[type] ?: TrackerStatus(type, false)
            val (emoji, color) = getTrackerVisuals(type)

            // Calculate today's duration for this specific tracker
            val durationMs = todaySessions.filter { it.trackerType == type }.sumOf { it.endTime - it.startTime }
            val hoursTracked = durationMs.toFloat() / (1000 * 60 * 60)
            val minutesTracked = (durationMs % (1000 * 60 * 60)) / (1000 * 60)

            val goalVal = goals[type] ?: 0f

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTrackerClick(type, status.isActive) }
                    .testTag("tracker_button_${type.lowercase().replace(" ", "_")}"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (status.isActive) null else CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    width = 1.dp
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (status.isActive) Color.White.copy(alpha = 0.15f) else color.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }

                        // Status badge (DONE / Goal / TRACKING)
                        if (status.isActive) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TRACKING",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        } else if (goalVal > 0f && hoursTracked >= goalVal) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "DONE",
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        } else if (goalVal > 0f) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${String.format("%.1f", goalVal)}H GOAL",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Text(
                            text = type,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (status.isActive) "Running..." else if (hoursTracked > 0 || minutesTracked > 0) "${hoursTracked.toInt()}h ${minutesTracked}m" else "Tap to start",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isActive) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinishSessionDialog(
    trackerType: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var selectedQuality by remember { mutableStateOf("GOOD") }
    var notesText by remember { mutableStateOf("") }

    val quickNotesChips = listOf(
        "Felt motivated", "Tired", "Distracted", "Excellent focus",
        "Poor sleep", "Stress", "Busy schedule"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("finish_session_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Finish tracking $trackerType",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Quality rating selector
                Text(
                    text = "How was the quality?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("GOOD", "NORMAL", "BAD").forEach { q ->
                        val (label, emoji, color) = when(q) {
                            "GOOD" -> Triple("Good", "😊", Color(0xFF4CAF50))
                            "NORMAL" -> Triple("Normal", "😐", Color(0xFFFFC107))
                            else -> Triple("Bad", "😞", Color(0xFFF44336))
                        }
                        val isSel = selectedQuality == q
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSel) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedQuality = q }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = emoji, fontSize = 24.sp)
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSel) color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes input
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Add notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick note tags
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickNotesChips.forEach { chip ->
                        SuggestionChip(
                            onClick = {
                                if (notesText.isEmpty()) {
                                    notesText = chip
                                } else {
                                    notesText += ", $chip"
                                }
                            },
                            label = { Text(chip) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save and Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(selectedQuality, notesText) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Session")
                    }
                }
            }
        }
    }
}

fun getTrackerVisuals(type: String): Pair<String, Color> {
    return when (type) {
        "Sleep" -> Pair("😴", Color(0xFF3F51B5))
        "Study" -> Pair("📖", Color(0xFF4CAF50))
        "Productivity" -> Pair("💻", Color(0xFFFF9800))
        "Workout" -> Pair("💪", Color(0xFFE91E63))
        "Physical Activity" -> Pair("🏃", Color(0xFF00BCD4))
        "Training" -> Pair("🎯", Color(0xFF9C27B0))
        else -> Pair("🔥", Color(0xFFFF5722))
    }
}
