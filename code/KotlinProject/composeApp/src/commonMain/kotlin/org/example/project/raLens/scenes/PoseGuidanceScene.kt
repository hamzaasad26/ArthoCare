package org.example.project.raLens.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.raLens.Joint
import org.example.project.raLens.displayName
import org.example.project.raLens.poseGuides

@Composable
fun PoseGuidanceScene(
    selectedJoint: Joint,
    onContinue: () -> Unit
) {
    val poseGuide = poseGuides[selectedJoint]
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = selectedJoint.displayName(),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = poseGuide?.movementName ?: "Movement guidance",
            style = MaterialTheme.typography.titleMedium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text("Movement guide will appear here")
            }
        }
        Text(
            text = poseGuide?.instructionText ?: "Follow guided joint movement instructions.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}
