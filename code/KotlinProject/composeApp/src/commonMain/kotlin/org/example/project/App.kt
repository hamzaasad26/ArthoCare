package org.example.project

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.project.screens.RAPredictionScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

// The Screen enum remains the same
enum class Screen(val title: String) {
    SPLASH("Splash"),
    LOGIN("Login"),
    DASHBOARD("Dashboard"),
    ACTIONS("Actions"),
    INSIGHTS("Insights"),
    BROWSE("Browse"),
    SIGN_UP("Sign Up"),
    DAILY_LOG("Weekly Log"),
    RA_PREDICTIONS("RA Predictions"),
    DIET_PLANS("Diet Plans"),
    WEATHER_ALERTS("Weather Alerts"),
    REMINDERS("Reminders"),
    RA_LENS("RA Lens"),
    TIPS_EXERCISES("Tips/Exercises"),
    SETTINGS("Settings"),
    PROFILE("Profile"),
    EDUCATIONAL("Educational"),
    NUTRITION("Nutrition Suggestions"),
    EXERCISES("Exercises"),
    RECIPES("Recipes"),
    PRIVACY("Privacy"),
    TERMS_OF_SERVICE("Terms of Service"),
    PRIVACY_POLICY("Privacy Policy"),
    CONTACT_SUPPORT("Contact Support")
}

/**
 * The main entry point of the app.
 * Its only job is to apply the theme.
 */
@Composable
@Preview
fun App() {
    LaunchedEffect(Unit) {
        RaLensDebugLogger.logRomPipelineAuditOnce()
        withContext(Dispatchers.Default) {
            InterpretedRomLocalHistory.bootstrapIfNeeded()
        }
    }
    var isDarkTheme by remember { mutableStateOf(ThemePreferenceStore.isDarkTheme()) }
    AppTheme(isDarkTheme = isDarkTheme) {
        AppNavigation(
            isDarkTheme = isDarkTheme,
            onDarkThemeChange = { useDark ->
                isDarkTheme = useDark
                ThemePreferenceStore.setDarkTheme(useDark)
            }
        )
    }
}

/**
 * Defensive hydration guard. The canonical hydration call already lives in
 * [AuthService.login] / [AuthService.signUp] (via
 * [HealthTimelineHydrator.hydrateAfterLogin]); this trigger re-runs the same
 * function whenever an authenticated user id appears in [AuthService] so that
 * [WeeklyLogStore], [RaLensStore] and [PredictionStore] are guaranteed to be
 * populated from Supabase before any patient-facing screen renders content
 * that depends on them. Failures are swallowed to preserve existing UX.
 */
@Composable
private fun HydrateRemoteStoresOnAuth(currentUserId: String?) {
    LaunchedEffect(currentUserId) {
        if (!currentUserId.isNullOrBlank()) {
            runCatching { HealthTimelineHydrator.hydrateAfterLogin(currentUserId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * This composable handles all the state and navigation logic.
 * Now integrated with Supabase authentication!
 */
@Composable
fun AppNavigation(
    isDarkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    // Paints the shared dashboard gradient behind every screen. Individual
    // Scaffolds use a transparent container so this wash bleeds through.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .appBackground(),
        color = Color.Transparent
    ) {
        var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
        var lastRootScreen by remember { mutableStateOf(Screen.DASHBOARD) }
        var loggedInUserName by remember { mutableStateOf("User") }

        // Re-key the hydration LaunchedEffect on the visible userName so that
        // it re-fires the moment login/sign-up flips us off the auth screens.
        HydrateRemoteStoresOnAuth(
            currentUserId = AuthService.getCurrentUser()?.id
        )

        val performLogout = {
            AuthService.logout()
            loggedInUserName = "User"
            lastRootScreen = Screen.DASHBOARD
            currentScreen = Screen.LOGIN
        }

        Crossfade(targetState = currentScreen) { screen ->
            when (screen) {
                Screen.SPLASH -> SplashScreen(
                    onNavigateToLogin = { currentScreen = Screen.LOGIN }
                )
                Screen.LOGIN -> LoginScreen(
                    onLoginSuccess = { userName ->
                        loggedInUserName = userName
                        currentScreen = Screen.DASHBOARD
                    },
                    onSignUpClicked = {
                        currentScreen = Screen.SIGN_UP
                    }
                )
                Screen.SIGN_UP -> SignUpScreen(
                    onSignUpSuccess = { userName ->
                        loggedInUserName = userName
                        currentScreen = Screen.DASHBOARD
                    },
                    onBackToLoginClicked = {
                        currentScreen = Screen.LOGIN
                    }
                )
                Screen.DASHBOARD, Screen.ACTIONS, Screen.INSIGHTS, Screen.BROWSE -> MainHomeScreen(
                    userName = loggedInUserName,
                    selectedRootScreen = screen,
                    onRootScreenSelected = { selected ->
                        lastRootScreen = selected
                        currentScreen = selected
                    },
                    onNavigate = { destination ->
                        currentScreen = destination
                    }
                )
                Screen.DAILY_LOG -> DailyLogScreen(
                    onNavigateBack = { currentScreen = Screen.ACTIONS }
                )
                Screen.RA_PREDICTIONS -> RAPredictionScreen(
                    onNavigateBack = { currentScreen = Screen.INSIGHTS }
                )
                Screen.DIET_PLANS -> DietPlansScreen(
                    onNavigateBack = { currentScreen = Screen.DASHBOARD }
                )
                Screen.WEATHER_ALERTS -> WeatherAlertsScreen(
                    onNavigateBack = { currentScreen = Screen.INSIGHTS }
                )
                Screen.REMINDERS -> RemindersScreen(
                    onNavigateBack = { currentScreen = Screen.ACTIONS }
                )
                Screen.RA_LENS -> RaLensScreen(
                    onNavigateBack = { currentScreen = Screen.ACTIONS }
                )
                Screen.TIPS_EXERCISES -> TipsAndExercisesScreen(
                    onNavigateBack = { currentScreen = Screen.DASHBOARD }
                )
                Screen.SETTINGS -> SettingsScreen(
                    onNavigateBack = { currentScreen = lastRootScreen },
                    onLogout = performLogout,
                    isDarkTheme = isDarkTheme,
                    onDarkThemeChange = onDarkThemeChange
                )
                Screen.PROFILE -> ProfileScreen(
                    onNavigateBack = { currentScreen = lastRootScreen }
                )
                Screen.EDUCATIONAL -> EducationalScreen(
                    onNavigateBack = { currentScreen = Screen.BROWSE }
                )
                Screen.NUTRITION -> NutritionSuggestionsScreen(
                    onNavigateBack = { currentScreen = Screen.BROWSE }
                )
                Screen.EXERCISES -> ExercisesScreen(
                    onNavigateBack = { currentScreen = Screen.BROWSE }
                )
                Screen.RECIPES -> RecipesScreen(
                    onNavigateBack = { currentScreen = Screen.BROWSE }
                )
                Screen.PRIVACY -> PrivacyScreen(
                    onNavigateBack = { currentScreen = Screen.BROWSE },
                    onNavigate = { currentScreen = it }
                )
                Screen.TERMS_OF_SERVICE -> TermsOfServiceScreen(
                    onNavigateBack = { currentScreen = Screen.PRIVACY }
                )
                Screen.PRIVACY_POLICY -> PrivacyPolicyScreen(
                    onNavigateBack = { currentScreen = Screen.PRIVACY }
                )
                Screen.CONTACT_SUPPORT -> ContactSupportScreen(
                    onNavigateBack = { currentScreen = Screen.PRIVACY }
                )
            }
        }
    }
}
