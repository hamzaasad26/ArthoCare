package org.example.project.raLens.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.RaLensAnalyzeResponseApi
import org.example.project.toDisplayJointName

@Composable
fun ResultsScene(
    result: RaLensAnalyzeResponseApi?,
    onExamineAnotherJoint: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        result?.let { api ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Most affected joint", style = MaterialTheme.typography.titleMedium)
                    val top = api.mostAffectedJoint
                    if (top == null) {
                        Text("No joint data returned.")
                    } else {
                        Text("${top.joint.toDisplayJointName()}: ${top.deficitPct.toInt()}% deficit (${top.status})")
                    }
                    Text("Overall ROM burden: ${api.overallRomBurden.toInt()}%")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Joint ranking", style = MaterialTheme.typography.titleMedium)
                    api.jointScores.forEachIndexed { index, row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${index + 1}. ${row.joint.toDisplayJointName()}")
                            Text("${row.deficitPct.toInt()}% (${row.status})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (api.availableScripts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Detected RA Lens scripts: ${api.availableScripts.joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } ?: Text(
            text = "No result available.",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(onClick = onExamineAnotherJoint, modifier = Modifier.fillMaxWidth()) {
            Text("Examine Another Joint")
        }
    }
}
