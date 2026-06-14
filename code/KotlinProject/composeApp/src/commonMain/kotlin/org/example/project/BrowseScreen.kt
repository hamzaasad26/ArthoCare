@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class BrowseDestination(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: Screen
)

private val browseDestinations = listOf(
    BrowseDestination(
        title = "Reminders",
        subtitle = "Manage daily reminder schedule",
        icon = Icons.Filled.Notifications,
        destination = Screen.REMINDERS
    ),
    BrowseDestination(
        title = "Exercises",
        subtitle = "Mobility and ROM routines",
        icon = Icons.Filled.FitnessCenter,
        destination = Screen.EXERCISES
    ),
    BrowseDestination(
        title = "Educational",
        subtitle = "Learn about ROM and stiffness",
        icon = Icons.Filled.School,
        destination = Screen.EDUCATIONAL
    ),
    BrowseDestination(
        title = "Privacy",
        subtitle = "Policies, support and legal",
        icon = Icons.Filled.Shield,
        destination = Screen.PRIVACY
    ),
    BrowseDestination(
        title = "Recipes",
        subtitle = "Anti-inflammatory meals and ingredients",
        icon = Icons.Filled.RestaurantMenu,
        destination = Screen.RECIPES
    )
)

@Composable
fun BrowseTabScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        BrowseSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth()
        )
        BrowseExploreGrid(
            destinations = browseDestinations,
            onCardClick = { onNavigate(it.destination) },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true)
        )
    }
}

@Composable
private fun BrowseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = LocalAppColors.current.onSurfaceMuted
            )
        },
        placeholder = {
            Text(
                "Search exercises, education, recovery...",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalAppColors.current.inputFill,
            unfocusedContainerColor = LocalAppColors.current.inputFill,
            focusedTextColor = LocalAppColors.current.onSurfaceStrong,
            unfocusedTextColor = LocalAppColors.current.onSurfaceStrong,
            cursorColor = LocalAppColors.current.linkAccent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun BrowseExploreGrid(
    destinations: List<BrowseDestination>,
    onCardClick: (BrowseDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(destinations.size) { index ->
            val item = destinations[index]
            BrowseCard(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                onClick = { onCardClick(item) }
            )
        }
    }
}

@Composable
private fun BrowseCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(LocalAppColors.current.primaryButton.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LocalAppColors.current.sectionHighlight,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.onSurfaceStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
