@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class EducationTopic(
    val title: String,
    val subtitle: String,
    val body: String
)

private val educationTopics = listOf(
    EducationTopic(
        title = "What is ROM?",
        subtitle = "Range of motion explained",
        body = "Range of Motion (ROM) measures how far a joint can move through its normal arc — for example, " +
            "how far you can bend an elbow or rotate a shoulder. Reduced ROM is one of the earliest, most " +
            "objective signs of joint disease activity in inflammatory arthritis."
    ),
    EducationTopic(
        title = "What causes stiffness?",
        subtitle = "Morning and post-rest stiffness",
        body = "Stiffness in inflammatory arthritis is driven by overnight accumulation of synovial fluid and " +
            "inflammatory mediators. It typically eases with gentle movement. Persistent stiffness lasting " +
            "more than 30–60 minutes is a flag worth tracking."
    ),
    EducationTopic(
        title = "Inflammation effects",
        subtitle = "How synovial inflammation harms joints",
        body = "Active synovitis thickens the joint lining, releases enzymes that erode cartilage, and over " +
            "time can damage bone. Catching flares early — using ROM, symptom logs, and weather context — " +
            "helps protect joint surfaces before structural damage develops."
    ),
    EducationTopic(
        title = "Recovery science",
        subtitle = "Mobility, pacing, and rebuild",
        body = "Recovery combines pharmacologic control with graded movement: short, frequent ROM drills " +
            "preserve joint capsule extensibility; load-bearing exercise rebuilds protective muscle. " +
            "Pacing — alternating effort with rest — prevents flares from undoing progress."
    )
)

@Composable
fun EducationalScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Educational", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Text(
                    "Learn about ROM, stiffness, inflammation, and recovery science.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary
                )
            }
            items(educationTopics) { topic -> EducationTopicCard(topic) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun EducationTopicCard(topic: EducationTopic) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LocalAppColors.current.primaryButton.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = LocalAppColors.current.sectionHighlight,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        topic.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.onSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        topic.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalAppColors.current.linkAccent
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    topic.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
