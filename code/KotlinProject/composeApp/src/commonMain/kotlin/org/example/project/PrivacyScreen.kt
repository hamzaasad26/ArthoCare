@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class PrivacyDestinationRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen
)

private val privacyRows = listOf(
    PrivacyDestinationRow(
        title = "Terms of Service",
        subtitle = "Legal terms for using the app",
        icon = Icons.Filled.Shield,
        screen = Screen.TERMS_OF_SERVICE
    ),
    PrivacyDestinationRow(
        title = "Privacy Policy",
        subtitle = "How we collect, store, and use your data",
        icon = Icons.Filled.Lock,
        screen = Screen.PRIVACY_POLICY
    ),
    PrivacyDestinationRow(
        title = "Contact Support",
        subtitle = "Email, feedback, and data requests",
        icon = Icons.Filled.Email,
        screen = Screen.CONTACT_SUPPORT
    )
)

@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Privacy", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            privacyRows.forEach { row ->
                PrivacyListCard(
                    title = row.title,
                    subtitle = row.subtitle,
                    icon = row.icon,
                    onClick = { onNavigate(row.screen) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrivacyListCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalAppColors.current.sectionHighlight,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.onSurfaceStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalAppColors.current.linkAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
