@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private sealed class ProfileViewState {
    object Loading : ProfileViewState()
    data class Loaded(val snapshot: ProfileSnapshot) : ProfileViewState()
    data class Error(val message: String) : ProfileViewState()
}

@Composable
fun ProfileScreen(onNavigateBack: () -> Unit) {
    val user = remember { AuthService.getCurrentUser() }
    var state by remember { mutableStateOf<ProfileViewState>(ProfileViewState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(user?.id, reloadKey) {
        if (user == null) {
            state = ProfileViewState.Error("You are not signed in.")
            return@LaunchedEffect
        }
        state = ProfileViewState.Loading
        val result = withContext(Dispatchers.Default) {
            ProfileRepository.loadSnapshot(user)
        }
        state = result.fold(
            onSuccess = { ProfileViewState.Loaded(it) },
            onFailure = { ProfileViewState.Error(it.message ?: "Could not load profile.") }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Profile", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = state) {
                is ProfileViewState.Loading -> ProfileLoading()
                is ProfileViewState.Error -> ProfileError(message = s.message, onRetry = { reloadKey++ })
                is ProfileViewState.Loaded -> ProfileContent(snapshot = s.snapshot)
            }
        }
    }
}

@Composable
private fun ProfileLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = LocalAppColors.current.linkAccent
        )
    }
}

@Composable
private fun ProfileError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(
                    "Could not load profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(snapshot: ProfileSnapshot) {
    val profile = snapshot.profile
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item { Spacer(Modifier.height(AppSpacing.sm)) }
        item { UserHeaderCard(user = snapshot.user) }
        if (profile == null) {
            item { ProfileMissingBanner() }
        } else {
            item { HealthOverviewSection(profile = profile) }
            item { NutritionSummarySection(profile = profile) }
            item { ConditionsSection(profile = profile) }
            item { DemographicsSection(profile = profile) }
        }
        item { Spacer(Modifier.height(AppSpacing.sm)) }
    }
}

@Composable
private fun UserHeaderCard(user: User) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSpacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(LocalAppColors.current.primaryButton.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.account),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = LocalAppColors.current.sectionHighlight
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    user.fullName.ifBlank { user.username },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.onSurfaceStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.onSurfaceSecondary
                )
                user.createdAt?.let { iso ->
                    Text(
                        "Joined ${formatAssessmentTime(iso)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.onSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMissingBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                "Profile details not yet completed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                "Health overview, nutrition and demographic data will appear here once your profile is set up.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun HealthOverviewSection(profile: UserProfile) {
    val conditionsCount = listOf(profile.hypertension, profile.diabetes, profile.hyperlipidemia)
        .count { it.isYesLike() }
    SectionLabel("Health overview")
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            StatTile(
                label = "Activity",
                value = profile.physicalActivity.displayValue(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Smoking",
                value = profile.smoking.displayValue(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            StatTile(
                label = "Drinking",
                value = profile.drinking.displayValue(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Conditions",
                value = if (conditionsCount > 0) "$conditionsCount active" else "None reported",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NutritionSummarySection(profile: UserProfile) {
    SectionLabel("Daily nutrition")
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            StatTile(
                label = "Calories",
                value = profile.caloriesPerDay?.let { "$it kcal" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Protein",
                value = formatGrams(profile.proteinG),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Carbs",
                value = formatGrams(profile.carbsG),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            StatTile(
                label = "Fat",
                value = formatGrams(profile.fatG),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Fiber",
                value = formatGrams(profile.fiberG),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Caffeine",
                value = formatGrams(profile.caffeineG),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConditionsSection(profile: UserProfile) {
    SectionLabel("Medical conditions")
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConditionChip("Hypertension", profile.hypertension, Modifier.weight(1f))
            ConditionChip("Diabetes", profile.diabetes, Modifier.weight(1f))
            ConditionChip("Hyperlipidemia", profile.hyperlipidemia, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DemographicsSection(profile: UserProfile) {
    SectionLabel("Demographics")
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            DemographicRow("Date of birth", profile.dateOfBirth.displayValue())
            DemographicRow("Gender", profile.gender.displayValue())
            DemographicRow("Race / ethnicity", profile.raceEthnicity.displayValue())
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.sectionHighlight,
        modifier = Modifier.padding(start = 2.dp, top = AppSpacing.xs, bottom = AppSpacing.xs)
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.onSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConditionChip(
    label: String,
    rawStatus: String?,
    modifier: Modifier = Modifier
) {
    val isYes = rawStatus.isYesLike()
    val container = when {
        isYes -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        rawStatus.isNoLike() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val labelColor = when {
        isYes -> MaterialTheme.colorScheme.onErrorContainer
        rawStatus.isNoLike() -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    rawStatus.displayValue(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = labelColor
        )
    )
}

@Composable
private fun DemographicRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onSurfaceMuted
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalAppColors.current.onSurfaceStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = AppSpacing.md)
        )
    }
}

private fun String?.displayValue(): String {
    val v = this?.trim().orEmpty()
    return if (v.isEmpty()) "—" else v.replaceFirstChar { it.uppercase() }
}

private fun String?.isYesLike(): Boolean {
    val v = this?.trim()?.lowercase().orEmpty()
    return v == "yes" || v == "y" || v == "true" || v == "1"
}

private fun String?.isNoLike(): Boolean {
    val v = this?.trim()?.lowercase().orEmpty()
    return v == "no" || v == "n" || v == "false" || v == "0"
}

private fun formatGrams(value: Double?): String {
    if (value == null) return "—"
    val rounded = (value * 10).toLong() / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
    return "$text g"
}
