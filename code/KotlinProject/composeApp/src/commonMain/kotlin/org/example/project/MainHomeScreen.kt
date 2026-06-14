@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.account
import kotlinproject.composeapp.generated.resources.nav_data
import kotlinproject.composeapp.generated.resources.nav_do
import kotlinproject.composeapp.generated.resources.logo_copy
import kotlinproject.composeapp.generated.resources.nav_home
import kotlinproject.composeapp.generated.resources.setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class RootTab(
    val screen: Screen,
    val label: String,
    val iconRes: DrawableResource? = null,
    val iconVector: ImageVector? = null
)

private val rootTabs = listOf(
    RootTab(Screen.DASHBOARD, "Dashboard", iconRes = Res.drawable.nav_home),
    RootTab(Screen.ACTIONS, "Actions", iconRes = Res.drawable.nav_do),
    RootTab(Screen.INSIGHTS, "Insights", iconRes = Res.drawable.nav_data),
    RootTab(
        screen = Screen.BROWSE,
        label = "Browse",
        iconVector = Icons.Filled.GridView
    )
)

@Composable
private fun MainHomeTopBar(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val c = LocalAppColors.current
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onProfileClick) {
                Icon(
                    painter = painterResource(Res.drawable.account),
                    contentDescription = "Profile",
                    modifier = Modifier.size(26.dp),
                    tint = c.sectionHighlight
                )
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo_copy),
                    contentDescription = "ArthoCare",
                    colorFilter = ColorFilter.tint(c.sectionHighlight),
                    modifier = Modifier
                        .height(44.dp)
                        .wrapContentWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(Res.drawable.setting),
                    contentDescription = "Settings",
                    modifier = Modifier.size(26.dp),
                    tint = c.sectionHighlight
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.chromeSurface,
            titleContentColor = c.sectionHighlight,
            navigationIconContentColor = c.sectionHighlight,
            actionIconContentColor = c.sectionHighlight
        )
    )
}

@Composable
fun MainHomeScreen(
    userName: String,
    selectedRootScreen: Screen,
    onRootScreenSelected: (Screen) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val c = LocalAppColors.current
    var showQuickAddSheet by remember { mutableStateOf(false) }
    Scaffold(
        // Transparent so the AppNavigation-level dark gradient bleeds through.
        containerColor = Color.Transparent,
        topBar = {
            MainHomeTopBar(
                onProfileClick = { onNavigate(Screen.PROFILE) },
                onSettingsClick = { onNavigate(Screen.SETTINGS) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = c.chromeSurface,
                contentColor = c.sectionHighlight
            ) {
                rootTabs.forEach { tab ->
                    val isSelected = selectedRootScreen == tab.screen
                    val iconTint = if (isSelected) c.sectionHighlight else c.chromeOnSurfaceMuted
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onRootScreenSelected(tab.screen) },
                        icon = {
                            when {
                                tab.iconRes != null -> Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconTint
                                )
                                tab.iconVector != null -> Icon(
                                    imageVector = tab.iconVector,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconTint
                                )
                            }
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.sectionHighlight,
                            selectedTextColor = c.sectionHighlight,
                            unselectedIconColor = c.chromeOnSurfaceMuted,
                            unselectedTextColor = c.chromeOnSurfaceMuted,
                            indicatorColor = c.linkAccent.copy(alpha = 0.22f)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickAddSheet = true },
                containerColor = c.primaryButton,
                contentColor = c.onAccent,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Quick add"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        when (selectedRootScreen) {
            Screen.DASHBOARD -> {
                AppTabContainer(paddingValues) {
                    DashboardScreen(
                        userName = userName,
                        onNavigate = onNavigate
                    )
                }
            }
            Screen.ACTIONS -> {
                AppTabContainer(paddingValues) {
                    ActionsTabScreen(onNavigate = onNavigate)
                }
            }
            Screen.INSIGHTS -> {
                AppTabContainer(paddingValues) {
                    InsightsTabScreen(onNavigate = onNavigate)
                }
            }
            Screen.BROWSE -> {
                AppTabContainer(paddingValues) {
                    BrowseTabScreen(onNavigate = onNavigate)
                }
            }
            else -> Unit
        }
    }

    if (showQuickAddSheet) {
        QuickAddSheet(
            onDismiss = { showQuickAddSheet = false },
            onNavigate = { destination ->
                showQuickAddSheet = false
                onNavigate(destination)
            }
        )
    }
}

@Composable
private fun AppTabContainer(
    paddingValues: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        content()
    }
}

@Composable
private fun ActionsTabScreen(onNavigate: (Screen) -> Unit) {
    val latestLogAt = WeeklyLogStore.latestUpdatedAtMillis
    val latestPredictionAt = PredictionStore.latestUpdatedAtMillis
    val gaugeAt = PredictionStore.cachedGauges?.capturedAtMillis
    val romBurden = RaLensStore.latestRomAnalysis?.overallRomBurden
    val prevRomBurden = RaLensStore.previousOverallRomBurden
    val summary = remember(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        HealthIntelligence.computeSummary()
    }
    val nextStep = remember(summary, latestLogAt, latestPredictionAt) {
        HealthIntelligence.computeRecommendedStep(summary)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                title = "Actions for today",
                subtitle = "Complete active tasks and guided care workflows."
            )
        }
        item {
            SectionHeader("Today's tasks", "High-value interactions")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionSummaryCard(
                    title = nextStep.title,
                    body = nextStep.body,
                    actionLabel = nextStep.actionLabel,
                    primaryAction = true,
                    onActionClick = { onNavigate(nextStep.destination) }
                )
            }
        }
        item {
            SectionHeader("Primary workflows", "Execute daily health tasks")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionSummaryCard(
                    title = "Symptom logging",
                    body = "Record today's symptom and activity details.",
                    actionLabel = "Open weekly log",
                    onActionClick = { onNavigate(Screen.DAILY_LOG) }
                )
            }
        }
        item {
            RaLensWorkflowCard(onStartFlow = { onNavigate(Screen.RA_LENS) })
        }
        item {
            SectionHeader("Quick actions", "Supportive execution tools")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionButton(label = "Reminders", onClick = { onNavigate(Screen.REMINDERS) })
            }
        }
    }
}

@Composable
private fun InsightsTabScreen(onNavigate: (Screen) -> Unit) {
    val colors = LocalAppColors.current
    val latestLogAt = WeeklyLogStore.latestUpdatedAtMillis
    val latestPredictionAt = PredictionStore.latestUpdatedAtMillis
    val gaugeAt = PredictionStore.cachedGauges?.capturedAtMillis
    val romBurden = RaLensStore.latestRomAnalysis?.overallRomBurden
    val prevRomBurden = RaLensStore.previousOverallRomBurden
    val summary = remember(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        HealthIntelligence.computeSummary()
    }

    var longitudinal by remember { mutableStateOf<RomDashboardLongitudinalState?>(null) }
    LaunchedEffect(latestLogAt, latestPredictionAt, gaugeAt, romBurden, prevRomBurden) {
        longitudinal = withContext(Dispatchers.Default) {
            defaultRomInsightsRepository().loadDashboardState()
        }
    }

    val nowForecast = remember { SharedEnvironmentalSignals.standardForecastWindows.first() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            InsightsWeatherCard(
                forecast = nowForecast,
                summary = summary,
                onOpenWeather = { onNavigate(Screen.WEATHER_ALERTS) }
            )
        }
        item {
            SectionHeader("Mobility analytics", "Derived from longitudinal RA Lens sessions")
        }
        item {
            // Swipeable pager: weakest joint, joint ROM bars.
            val analyticsPagerState = rememberPagerState(pageCount = { 2 })
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = analyticsPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) { page ->
                    val pageModifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                    when (page) {
                        0 -> WeakestJointCard(
                            latestSession = longitudinal?.latestSession,
                            history = longitudinal?.weakestJointHistory.orEmpty(),
                            modifier = pageModifier
                        )
                        1 -> JointRomBarChartCard(
                            modifier = pageModifier,
                            latestSessionTimestamp = longitudinal?.latestSession?.timestamp,
                            sessionCount = longitudinal?.allSessions?.size ?: 0
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(2) { index ->
                        val isSelected = analyticsPagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.linkAccent
                                    else colors.divider.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
            }
        }
        item {
            SectionHeader("Modeled flare drivers", "Interpret current model outputs")
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryCard(
                    title = "Current interpretation",
                    body = summary.insightsModelInterpretation
                )
                if (PredictionStore.cachedGauges?.isFallbackEstimate == true) {
                    Text(
                        text = "Estimated from symptom history — run RA Predictions for full analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceMuted.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }
        }
        // Note: the "complete your log / refresh predictions" nudge is
        // intentionally NOT shown here. It lives in exactly one place — the
        // Dashboard "Recommended Next Action" card (built by
        // [HealthIntelligence.computeRecommendedStep]) — so users don't see
        // the same prompt repeated across tabs.
        item {
            ActionSummaryCard(
                title = "Detailed analytics",
                body = "Open the full prediction screen for charts, gauges, and ROM-linked interpretation.",
                actionLabel = "Open RA predictions",
                onActionClick = { onNavigate(Screen.RA_PREDICTIONS) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickActionButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = LocalAppColors.current.linkAccent
        )
    ) {
        Text(label)
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun ActionSummaryCard(
    title: String,
    body: String,
    actionLabel: String,
    primaryAction: Boolean = false,
    onActionClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (primaryAction) {
                    Button(
                        onClick = onActionClick,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = LocalAppColors.current.primaryButton,
                            contentColor = LocalAppColors.current.onAccent
                        )
                    ) { Text(actionLabel) }
                } else {
                    OutlinedButton(
                        onClick = onActionClick,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalAppColors.current.linkAccent
                        )
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun RaLensWorkflowCard(
    onStartFlow: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RA Lens ROM analysis",
                style = MaterialTheme.typography.titleSmall,
                color = LocalAppColors.current.onSurfaceStrong
            )
            Text(
                text = "Capture a joint movement and run analysis to review ROM deficits and affected-joint ranking.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onStartFlow,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.primaryButton,
                        contentColor = LocalAppColors.current.onAccent
                    )
                ) { Text("Start RA Lens") }
            }
        }
    }
}

@Composable
private fun QuickAddSheet(
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Add",
                style = MaterialTheme.typography.titleLarge,
                color = LocalAppColors.current.sectionHighlight
            )
            Text(
                text = "Capture quick health inputs without leaving your current tab.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.onSurfaceSecondary
            )
            val sheetButtonColors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = LocalAppColors.current.linkAccent
            )
            TextButton(
                onClick = { onNavigate(Screen.DAILY_LOG) },
                colors = sheetButtonColors,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Quick symptom log") }
            TextButton(
                onClick = { onNavigate(Screen.DAILY_LOG) },
                colors = sheetButtonColors,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Quick pain entry") }
            TextButton(
                onClick = { onNavigate(Screen.DAILY_LOG) },
                colors = sheetButtonColors,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Quick fatigue check") }
            TextButton(
                onClick = { onNavigate(Screen.DAILY_LOG) },
                colors = sheetButtonColors,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Quick note entry") }
        }
    }
}
