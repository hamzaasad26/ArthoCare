package org.example.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(onNavigateBack: () -> Unit) {
    // State variables for the daily log form
    var painLevel by remember { mutableStateOf(5f) } // daily pain (0–10)
    var fatigueLevel by remember { mutableStateOf<String?>(null) } // "Low", "Medium", "High"
    var notes by remember { mutableStateOf("") }

    // Weekly symptom log (1–10)
    var weeklyPain by remember { mutableStateOf(5f) }
    var weeklyStiffness by remember { mutableStateOf(5f) }
    var weeklyFatigue by remember { mutableStateOf(5f) }
    var weeklyDifficulty by remember { mutableStateOf(5f) }

    // Physical activity (last 7 days)
    var vigorousDays by remember { mutableStateOf("") }
    var vigorousHours by remember { mutableStateOf("") }
    var moderateDays by remember { mutableStateOf("") }
    var moderateHours by remember { mutableStateOf("") }
    var walkingDays by remember { mutableStateOf("") }
    var walkingHours by remember { mutableStateOf("") }
    var sittingHoursPerWeekday by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FeatureTopAppBar(title = "Weekly Log", onNavigateBack = onNavigateBack)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DailyLogSectionHeading("How would you rate your pain level today?")
            DailyLogSlider(value = painLevel, onValueChange = { painLevel = it }, valueRange = 0f..10f, steps = 9)
            Text(
                text = "Pain Level: ${painLevel.roundToInt()}/10",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceSecondary
            )

            DailyLogSectionHeading("How would you rate your fatigue level?")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RadioButtonOption(text = "Low", selected = fatigueLevel == "Low") { fatigueLevel = "Low" }
                RadioButtonOption(text = "Medium", selected = fatigueLevel == "Medium") { fatigueLevel = "Medium" }
                RadioButtonOption(text = "High", selected = fatigueLevel == "High") { fatigueLevel = "High" }
            }

            DailyLogSectionHeading("Any additional notes?")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("e.g., morning stiffness, specific joint pain...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                colors = darkTextFieldColors()
            )

            DailyLogSectionHeading("Weekly symptom log (1 = None, 10 = Severe)")

            DailyLogSliderField("Pain", weeklyPain, { weeklyPain = it })
            DailyLogSliderField("Stiffness", weeklyStiffness, { weeklyStiffness = it })
            DailyLogSliderField("Fatigue", weeklyFatigue, { weeklyFatigue = it })
            DailyLogSliderField("Physical difficulty in doing tasks", weeklyDifficulty, { weeklyDifficulty = it })

            DailyLogSectionHeading("Physical activity (last 7 days)")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyLogTextField(value = vigorousDays, onValueChange = { vigorousDays = it }, label = "Vigorous days", modifier = Modifier.weight(1f))
                DailyLogTextField(value = vigorousHours, onValueChange = { vigorousHours = it }, label = "Vigorous hours", modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyLogTextField(value = moderateDays, onValueChange = { moderateDays = it }, label = "Moderate days", modifier = Modifier.weight(1f))
                DailyLogTextField(value = moderateHours, onValueChange = { moderateHours = it }, label = "Moderate hours", modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyLogTextField(value = walkingDays, onValueChange = { walkingDays = it }, label = "Walking days", modifier = Modifier.weight(1f))
                DailyLogTextField(value = walkingHours, onValueChange = { walkingHours = it }, label = "Walking hours", modifier = Modifier.weight(1f))
            }

            DailyLogTextField(
                value = sittingHoursPerWeekday,
                onValueChange = { sittingHoursPerWeekday = it },
                label = "Sitting hours per weekday",
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    WeeklyLogStore.update(
                        WeeklyLogSnapshot(
                            pain = weeklyPain.toDouble(),
                            stiffness = weeklyStiffness.toDouble(),
                            fatigue = weeklyFatigue.toDouble(),
                            physicalDifficulty = weeklyDifficulty.toDouble(),
                            vigorousDays = vigorousDays.toDoubleOrNull() ?: 0.0,
                            vigorousHours = vigorousHours.toDoubleOrNull() ?: 0.0,
                            moderateDays = moderateDays.toDoubleOrNull() ?: 0.0,
                            moderateHours = moderateHours.toDoubleOrNull() ?: 0.0,
                            walkingDays = walkingDays.toDoubleOrNull() ?: 0.0,
                            walkingHours = walkingHours.toDoubleOrNull() ?: 0.0,
                            sittingHoursPerWeekday = sittingHoursPerWeekday.toDoubleOrNull() ?: 0.0
                        )
                    )
                    onNavigateBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalAppColors.current.primaryButton,
                    contentColor = LocalAppColors.current.onAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Log")
            }
        }
    }
}

@Composable
private fun DailyLogSectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.sectionHighlight
    )
}

@Composable
private fun DailyLogSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = LocalAppColors.current.linkAccent,
            activeTrackColor = LocalAppColors.current.linkAccent,
            inactiveTrackColor = LocalAppColors.current.divider
        )
    )
}

@Composable
private fun DailyLogSliderField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalAppColors.current.onSurfaceStrong
    )
    DailyLogSlider(value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8)
    Text(
        text = "$label: ${value.roundToInt()}/10",
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppColors.current.onSurfaceSecondary
    )
}

@Composable
private fun DailyLogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        colors = darkTextFieldColors()
    )
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalAppColors.current.onSurfaceStrong,
    unfocusedTextColor = LocalAppColors.current.onSurfaceStrong,
    focusedContainerColor = LocalAppColors.current.inputFill,
    unfocusedContainerColor = LocalAppColors.current.inputFill,
    cursorColor = LocalAppColors.current.linkAccent,
    focusedBorderColor = LocalAppColors.current.linkAccent,
    unfocusedBorderColor = LocalAppColors.current.cardBorder.copy(alpha = 0.5f),
    focusedLabelColor = LocalAppColors.current.linkAccent,
    unfocusedLabelColor = LocalAppColors.current.onSurfaceMuted
)

@Composable
private fun RadioButtonOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = LocalAppColors.current.linkAccent,
                unselectedColor = LocalAppColors.current.onSurfaceMuted
            )
        )
        Text(
            text,
            modifier = Modifier.padding(start = 4.dp),
            color = LocalAppColors.current.onSurfaceStrong
        )
    }
}
