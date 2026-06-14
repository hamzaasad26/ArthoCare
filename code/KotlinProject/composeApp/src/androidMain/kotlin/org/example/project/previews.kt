package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun LoginScreenPreview() {
    LoginScreen(onLoginSuccess = {}, onSignUpClicked = {})
}

@Preview
@Composable
fun SignUpScreenPreview() {
    SignUpScreen(
        onSignUpSuccess = { userName ->
            // Preview mode - do nothing
        },
        onBackToLoginClicked = {}
    )
}

// In C:/Users/heerh/Downloads/KotlinProject/KotlinProject/composeApp/src/androidMain/kotlin/org/example/project/previews.kt

// In previews.kt
@Preview
@Composable
fun DashboardScreenPreview() {
    DashboardScreen(
        userName = "User",
        onNavigate = {}
    )
}
