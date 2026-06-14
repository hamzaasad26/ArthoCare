package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ReminderItem(
    val id: String,
    val title: String,
    val time: String,
    var enabled: Boolean = true
)

private data class ReminderSection(
    val name: String,
    val itemIds: List<String>
)

@Composable
fun RemindersScreen(onNavigateBack: () -> Unit) {
    val reminders = remember {
        mutableStateListOf(
            ReminderItem("med_morning", "Take Morning Medication", "8:00 AM", true),
            ReminderItem("med_evening", "Take Evening Medication", "9:00 PM", true),
            ReminderItem("med_refill", "Medication Refill Due", "Every Monday 10:00 AM", false),
            ReminderItem("track_today", "Log Today's Symptoms", "7:00 PM", true),
            ReminderItem("track_weekly", "Complete Weekly Summary", "Every Sunday 6:00 PM", false),
            ReminderItem("rom_weekly", "Weekly ROM Assessment", "Every Saturday 10:00 AM", true),
            ReminderItem("rom_overdue", "ROM Assessment Overdue", "Fires if no session in 7 days", true),
            ReminderItem("act_morning", "Morning Mobility Exercises", "7:30 AM", true),
            ReminderItem("act_break", "Afternoon Movement Break", "2:00 PM", false),
            ReminderItem("act_evening", "Evening Stretch", "8:30 PM", false),
            ReminderItem("well_hydration", "Hydration Reminder", "Every 3 hours", false),
            ReminderItem("well_weather", "Weather Flare Alert", "Daily 7:00 AM", true)
        )
    }

    val sections = remember {
        listOf(
            ReminderSection("MEDICATION", listOf("med_morning", "med_evening", "med_refill")),
            ReminderSection("DAILY TRACKING", listOf("track_today", "track_weekly")),
            ReminderSection("RA LENS", listOf("rom_weekly", "rom_overdue")),
            ReminderSection("ACTIVITY", listOf("act_morning", "act_break", "act_evening")),
            ReminderSection("WELLNESS", listOf("well_hydration", "well_weather"))
        )
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedReminderId by remember { mutableStateOf<String?>(null) }

    val selectedReminder = selectedReminderId?.let { id -> reminders.firstOrNull { it.id == id } }

    fun updateReminder(id: String, updater: (ReminderItem) -> ReminderItem) {
        val idx = reminders.indexOfFirst { it.id == id }
        if (idx >= 0) reminders[idx] = updater(reminders[idx])
    }

    fun isIntervalReminder(item: ReminderItem): Boolean {
        return item.time.startsWith("Every ") || item.time.startsWith("Fires if no session")
    }

    Scaffold(
        containerColor = Color(0xFF0D0B12),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF0D0B12))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0B12))
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1F35))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Reminders are for reference only. Enable device notifications to receive alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            sections.forEach { section ->
                item {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D0B12))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                val sectionItems = section.itemIds.mapNotNull { id -> reminders.firstOrNull { it.id == id } }
                itemsIndexed(sectionItems, key = { _, item -> item.id }) { index, reminder ->
                    if (index > 0) {
                        Divider(
                            color = Color.White.copy(alpha = 0.06f),
                            thickness = 1.dp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1630))
                            .clickable {
                                selectedReminderId = reminder.id
                                if (isIntervalReminder(reminder)) {
                                    showInfoDialog = true
                                } else {
                                    showTimePicker = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                reminder.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                reminder.time,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { checked ->
                                updateReminder(reminder.id) { it.copy(enabled = checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF7B5EA7),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog && selectedReminder != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color(0xFF1E1630),
            title = {
                Text(
                    text = selectedReminder.title,
                    color = Color(0xFFCFB3FF),
                    style = MaterialTheme.typography.titleSmall
                )
            },
            text = {
                Text(
                    text = "Scheduling options coming soon",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Cancel", color = Color(0xFFB08AFF))
                }
            }
        )
    }

    if (showTimePicker && selectedReminder != null) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color(0xFF1E1630),
            title = {
                Text(
                    selectedReminder.title,
                    color = Color(0xFFCFB3FF),
                    style = MaterialTheme.typography.titleSmall
                )
            },
            text = {
                TimePickerContent(
                    initialTime = selectedReminder.time,
                    onTimeSelected = { newTime ->
                        updateReminder(selectedReminder.id) { it.copy(time = newTime) }
                        showTimePicker = false
                    }
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = Color(0xFFB08AFF))
                }
            }
        )
    }
}

@Composable
private fun TimePickerContent(
    initialTime: String,
    onTimeSelected: (String) -> Unit
) {
    val prefix = remember(initialTime) {
        val match = Regex("""\d{1,2}:\d{2}\s[AP]M""").find(initialTime)
        val idx = match?.range?.first ?: return@remember ""
        initialTime.substring(0, idx).trim().let { if (it.isBlank()) "" else "$it " }
    }
    val baseTime = remember(initialTime) {
        Regex("""\d{1,2}:\d{2}\s[AP]M""").find(initialTime)?.value ?: "8:00 AM"
    }
    val parsed = remember(baseTime) { parseTime(baseTime) }

    var hour by remember(baseTime) { mutableStateOf(parsed.first) }
    var minute by remember(baseTime) { mutableStateOf(parsed.second) }
    var amPm by remember(baseTime) { mutableStateOf(parsed.third) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimeStep("Hour", hour.toString()) {
                hour = if (hour == 12) 1 else hour + 1
            }
            TimeStep("Minute", minute.toString().padStart(2, '0')) {
                minute = (minute + 5) % 60
            }
            TimeStep("AM/PM", amPm) {
                amPm = if (amPm == "AM") "PM" else "AM"
            }
        }
        TextButton(
            onClick = { onTimeSelected(prefix + formatTime(hour, minute, amPm)) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Set Time", color = Color(0xFFB08AFF))
        }
    }
}

@Composable
private fun TimeStep(label: String, value: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        TextButton(onClick = onClick) {
            Text(text = value, color = Color.White)
        }
    }
}

private fun parseTime(value: String): Triple<Int, Int, String> {
    val match = Regex("""(\d{1,2}):(\d{2})\s(AM|PM)""").find(value)
    if (match == null) return Triple(8, 0, "AM")
    val hour = match.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 8
    val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
    val amPm = match.groupValues[3]
    return Triple(hour, minute, amPm)
}

private fun formatTime(hour: Int, minute: Int, amPm: String): String {
    val h = hour.coerceIn(1, 12)
    val m = minute.coerceIn(0, 59)
    val period = if (amPm == "PM") "PM" else "AM"
    return "$h:${m.toString().padStart(2, '0')} $period"
}
