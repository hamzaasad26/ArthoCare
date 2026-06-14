@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ExerciseItem(val name: String, val description: String)

private data class JointSection(val jointName: String, val exercises: List<ExerciseItem>)

private val jointExerciseContent = listOf(
    JointSection(
        jointName = "KNEE",
        exercises = listOf(
            ExerciseItem(
                name = "Seated Knee Extension",
                description = "Sit in a chair, slowly straighten leg until parallel to floor, hold 5 seconds, lower. 3×10 reps."
            ),
            ExerciseItem(
                name = "Heel Slides",
                description = "Lie flat, slowly slide heel toward your body bending the knee, then straighten. 3×15 reps."
            ),
            ExerciseItem(
                name = "Standing Knee Flexion",
                description = "Hold a chair, slowly bend knee behind you as far as comfortable, hold 3 seconds. 3×10 reps."
            )
        )
    ),
    JointSection(
        jointName = "WRIST",
        exercises = listOf(
            ExerciseItem(
                name = "Wrist Circles",
                description = "Extend arm forward, slowly rotate wrist clockwise 10 times then counter-clockwise. 3 sets."
            ),
            ExerciseItem(
                name = "Wrist Flexion Stretch",
                description = "Extend arm, use other hand to gently pull fingers back toward forearm, hold 20 seconds. 3 sets."
            ),
            ExerciseItem(
                name = "Finger Spread",
                description = "Place hand flat on table, slowly spread fingers apart, hold 5 seconds, relax. 3×10 reps."
            )
        )
    ),
    JointSection(
        jointName = "ELBOW",
        exercises = listOf(
            ExerciseItem(
                name = "Elbow Flexion Curl",
                description = "Slowly bend elbow from straight to fully bent, lower slowly. 3×12 reps."
            ),
            ExerciseItem(
                name = "Forearm Rotation",
                description = "Elbow at 90°, slowly rotate forearm palm-up then palm-down. 3×15 reps."
            ),
            ExerciseItem(
                name = "Elbow Extension Stretch",
                description = "Raise arm overhead, use other hand to gently push elbow straight, hold 20 seconds. 3 sets."
            )
        )
    ),
    JointSection(
        jointName = "SHOULDER",
        exercises = listOf(
            ExerciseItem(
                name = "Pendulum Swing",
                description = "Lean forward slightly, let arm hang, gently swing in small circles. 2 minutes each direction."
            ),
            ExerciseItem(
                name = "Wall Crawl",
                description = "Face wall, walk fingers slowly up as high as comfortable, hold 5 seconds, lower. 3×10 reps."
            ),
            ExerciseItem(
                name = "Cross-Body Shoulder Stretch",
                description = "Bring arm across chest, use other hand to hold and pull gently, hold 20 seconds. 3 sets."
            )
        )
    ),
    JointSection(
        jointName = "ANKLE",
        exercises = listOf(
            ExerciseItem(
                name = "Ankle Circles",
                description = "Seated, lift foot slightly, rotate ankle slowly clockwise 10 times then counter-clockwise. 3 sets."
            ),
            ExerciseItem(
                name = "Towel Calf Stretch",
                description = "Seated, loop towel around foot, gently pull toes toward you, hold 20 seconds. 3 sets."
            ),
            ExerciseItem(
                name = "Heel Raises",
                description = "Stand holding chair, slowly rise onto toes, hold 3 seconds, lower slowly. 3×15 reps."
            )
        )
    )
)

private val ExerciseCardBorder = BorderStroke(0.5.dp, Color(0xFF6B4FA0).copy(alpha = 0.3f))
private val ExerciseBgClosed = Color(0xFF1E1630)
private val ExerciseBgOpen = Color(0xFF2A1F3D)
private val ExerciseScreenBg = Color(0xFF0D0B12)

@Composable
fun ExercisesScreen(onNavigateBack: () -> Unit) {
    var expandedJoint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = ExerciseScreenBg,
        topBar = { FeatureTopAppBar(title = "Exercises", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ExerciseScreenBg)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(jointExerciseContent, key = { it.jointName }) { section ->
                JointExerciseCard(
                    section = section,
                    expanded = expandedJoint == section.jointName,
                    onHeaderClick = {
                        expandedJoint =
                            if (expandedJoint == section.jointName) null else section.jointName
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun JointExerciseCard(
    section: JointSection,
    expanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) ExerciseBgOpen else ExerciseBgClosed
        ),
        border = ExerciseCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.jointName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB08AFF),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFB08AFF)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    section.exercises.forEachIndexed { index, ex ->
                        Text(
                            text = ex.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFCFB3FF)
                        )
                        Text(
                            text = ex.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        if (index < section.exercises.lastIndex) {
                            Divider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }
    }
}
