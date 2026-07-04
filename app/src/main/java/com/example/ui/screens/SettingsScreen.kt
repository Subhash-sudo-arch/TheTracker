package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.viewmodel.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TrackingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    var showGoalsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf<String?>(null) } // null or type "json" / "csv"
    var showImportDialog by remember { mutableStateOf<String?>(null) } // null or type "json" / "csv"
    var showResetDialog by remember { mutableStateOf(false) }

    // Runtime Permission for Notifications (Android 13+)
    var pendingReminderType by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingReminderType?.let { type ->
                viewModel.updateReminder(type, true)
            }
        } else {
            Toast.makeText(context, "Permission denied. Cannot enable reminders.", Toast.LENGTH_SHORT).show()
        }
        pendingReminderType = null
    }

    fun handleReminderToggle(type: String, enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.updateReminder(type, true)
                } else {
                    pendingReminderType = type
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.updateReminder(type, true)
            }
        } else {
            viewModel.updateReminder(type, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            // Theme Section
            item {
                SettingsHeader("Display Theme")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf("System Default" to 0, "Light Theme" to 1, "Dark Theme" to 2).forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setThemeMode(value) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                RadioButton(
                                    selected = themeMode == value,
                                    onClick = { viewModel.setThemeMode(value) }
                                )
                            }
                        }
                    }
                }
            }

            // Material You Switch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Dynamic Colors", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Use Material You colors on supported devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = dynamicColors,
                                onCheckedChange = { viewModel.setDynamicColors(it) }
                            )
                        }
                    }
                }
            }

            // Daily Goals Section
            item {
                SettingsHeader("Goals & Reminders")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column {
                        // Goals Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGoalsDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Daily Hours Goals", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Configure targeted daily hours for trackers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = "Edit Goals", modifier = Modifier.size(16.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Reminders
                        Text(
                            text = "Daily reminders (9AM / 5PM / 8PM / 10PM)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )

                        listOf("Sleep", "Study", "Workout", "Habit").forEach { rType ->
                            val isEnabled = reminders[rType] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "$rType Tracker Alert", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { handleReminderToggle(rType, it) }
                                )
                            }
                        }
                    }
                }
            }

            // Data Management Section
            item {
                SettingsHeader("Data Management (100% Offline)")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column {
                        // Export JSON
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExportDialog = "json" }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export JSON", modifier = Modifier.padding(end = 16.dp))
                            Text("Export backup database as JSON", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Export CSV
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExportDialog = "csv" }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", modifier = Modifier.padding(end = 16.dp))
                            Text("Export logs as CSV (for Excel)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Import Data
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showImportDialog = "json" }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Import JSON", modifier = Modifier.padding(end = 16.dp))
                            Text("Restore backup from JSON string", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Reset Stats
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showResetDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Reset Stats", tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 16.dp))
                            Text("Reset all statistics and delete logs", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // About Section
            item {
                SettingsHeader("About")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TheTracker v1.0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A premium-quality, 100% offline-first Android application. Your data remains completely encrypted on your device and never leaves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Goals config dialog
    if (showGoalsDialog) {
        GoalsConfigurationDialog(
            goals = goals,
            onDismiss = { showGoalsDialog = false },
            onSave = { type, hours ->
                viewModel.updateGoal(type, hours)
            }
        )
    }

    // Export Dialog
    showExportDialog?.let { type ->
        val exportString = if (type == "json") viewModel.exportToJson() else viewModel.exportToCsv()
        ExportDataDialog(
            dataType = type,
            data = exportString,
            onDismiss = { showExportDialog = null }
        )
    }

    // Import Dialog
    showImportDialog?.let { type ->
        ImportDataDialog(
            type = type,
            onDismiss = { showImportDialog = null },
            onImport = { input ->
                val ok = if (type == "json") viewModel.importFromJson(input) else viewModel.importFromCsv(input)
                if (ok) {
                    Toast.makeText(context, "Data imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to parse data. Check format.", Toast.LENGTH_LONG).show()
                }
                showImportDialog = null
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Data?") },
            text = { Text("Are you sure you want to clear all logs? This action is offline, permanent, and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showResetDialog = false
                        Toast.makeText(context, "Database wiped.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun GoalsConfigurationDialog(
    goals: Map<String, Float>,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Daily Hours Goals", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Define targeting hours for daily completion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                items(goals.keys.toList()) { key ->
                    var goalVal by remember { mutableStateOf(goals[key] ?: 1.0f) }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = key, fontWeight = FontWeight.Bold)
                            Text(text = "${"%.1f".format(goalVal)} hrs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = goalVal,
                            onValueChange = {
                                goalVal = it
                                onSave(key, it)
                            },
                            valueRange = 0.5f..24.0f,
                            steps = 47
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportDataDialog(
    dataType: String,
    data: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Export ${dataType.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Copy the code below to save your backup securely. You can paste it anytime to restore.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = data,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("TheTracker Backup", data)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Code")
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, data)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share backup via..."))
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ImportDataDialog(
    type: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Import Backup String",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Paste your previously copied backup code below to restore your logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Paste Code here") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onImport(textInput) },
                        enabled = textInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Restore Now")
                    }
                }
            }
        }
    }
}
