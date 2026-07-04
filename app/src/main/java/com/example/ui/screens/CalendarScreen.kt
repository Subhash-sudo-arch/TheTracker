package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackingSession
import com.example.viewmodel.TrackingViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TrackingViewModel,
    modifier: Modifier = Modifier
) {
    val completedSessions by viewModel.completedSessions.collectAsState()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val selectedDateString = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val daySessions = completedSessions.filter { it.dateString == selectedDateString }

    // Map of date strings to color coding based on completed session quality
    val dateColorMap = remember(completedSessions) {
        completedSessions.groupBy { it.dateString }.mapValues { (_, sessions) ->
            val goods = sessions.count { it.quality == "GOOD" }
            val bads = sessions.count { it.quality == "BAD" }
            when {
                goods > bads -> Color(0xFFE8F5E9) // Excellent Light Green
                bads > goods -> Color(0xFFFFEBEE) // Poor Light Red
                else -> Color(0xFFFFFDE7)         // Average Light Yellow
            }
        }
    }

    val dateTextColorMap = remember(completedSessions) {
        completedSessions.groupBy { it.dateString }.mapValues { (_, sessions) ->
            val goods = sessions.count { it.quality == "GOOD" }
            val bads = sessions.count { it.quality == "BAD" }
            when {
                goods > bads -> Color(0xFF2E7D32) // Excellent Dark Green
                bads > goods -> Color(0xFFC62828) // Poor Dark Red
                else -> Color(0xFFF57F17)         // Average Dark Yellow
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", fontWeight = FontWeight.Bold) },
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
            // Month Switcher Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Month")
                    }
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                    }
                }
            }

            // Grid Calendar Component
            item {
                CalendarGrid(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    onDateSelect = { selectedDate = it },
                    dateColorMap = dateColorMap,
                    dateTextColorMap = dateTextColorMap
                )
            }

            // Day Summary Section
            item {
                Text(
                    text = "Sessions for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (daySessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No logs for this date ⚪",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(daySessions) { session ->
                    CalendarSessionCard(session = session, onDelete = { viewModel.deleteSession(session) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    dateColorMap: Map<String, Color>,
    dateTextColorMap: Map<String, Color>
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // Sun=0, Mon=1...

    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), shape = RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Days Grid rows
        val totalCells = daysInMonth + firstDayOfWeek
        var cellIndex = 0

        while (cellIndex < totalCells) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0..6) {
                    if (cellIndex < firstDayOfWeek || cellIndex >= totalCells) {
                        // Empty spacer cell
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        val dayNum = cellIndex - firstDayOfWeek + 1
                        val localDate = currentMonth.atDay(dayNum)
                        val dateString = localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                        val isSelected = localDate == selectedDate
                        val colorCodeBg = dateColorMap[dateString]
                        val colorCodeText = dateTextColorMap[dateString]

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        colorCodeBg != null -> colorCodeBg
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateSelect(localDate) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || colorCodeBg != null) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    colorCodeText != null -> colorCodeText
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    cellIndex++
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun CalendarSessionCard(
    session: TrackingSession,
    onDelete: () -> Unit
) {
    val durationMin = (session.endTime - session.startTime) / (1000 * 60)
    val durationStr = if (durationMin >= 60) {
        "${durationMin / 60}h ${durationMin % 60}m"
    } else {
        "${durationMin}m"
    }

    val (emoji, color) = getTrackerVisuals(session.trackerType)

    val ratingEmoji = when (session.quality) {
        "GOOD" -> "😊 Good"
        "NORMAL" -> "😐 Normal"
        else -> "😞 Bad"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = session.trackerType,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(ratingEmoji) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Duration: $durationStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (session.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Notes: ${session.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Log",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
