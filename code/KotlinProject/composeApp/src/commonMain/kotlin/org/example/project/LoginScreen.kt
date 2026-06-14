package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.logo_copy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(onLoginSuccess: (userName: String) -> Unit, onSignUpClicked: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.logo_copy),
            contentDescription = "ArthoCare",
            colorFilter = ColorFilter.tint(LocalAppColors.current.sectionHighlight),
            modifier = Modifier
                .height(56.dp)
                .wrapContentWidth(),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.heroSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Welcome Back!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.sectionHighlight
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = authTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLoading
                        ) {
                            Text(if (passwordVisible) "🙈" else "👁")
                        }
                    },
                    enabled = !isLoading,
                    colors = authTextFieldColors()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            loginMessage = "Error: Please fill in all fields"
                            return@Button
                        }

                        isLoading = true
                        loginMessage = ""

                        coroutineScope.launch {
                            try {
                                val result = AuthService.login(username, password)

                                if (result.isSuccess) {
                                    val user = result.getOrNull()
                                    loginMessage = "Login Successful!"
                                    onLoginSuccess(user?.fullName ?: username)
                                } else {
                                    loginMessage = "Error: ${result.exceptionOrNull()?.message ?: "Invalid credentials"}"
                                }
                            } catch (e: Exception) {
                                loginMessage = "Error: ${e.message ?: "Login failed"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.primaryButton,
                        contentColor = LocalAppColors.current.onAccent
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = LocalAppColors.current.onAccent
                        )
                    } else {
                        Text("Login")
                    }
                }

                if (loginMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loginMessage,
                        color = if (loginMessage.startsWith("Error"))
                            MaterialTheme.colorScheme.error
                        else
                            LocalAppColors.current.linkAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onSignUpClicked,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalAppColors.current.linkAccent),
            enabled = !isLoading
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalAppColors.current.onSurfaceStrong,
    unfocusedTextColor = LocalAppColors.current.onSurfaceStrong,
    focusedContainerColor = LocalAppColors.current.inputFill,
    unfocusedContainerColor = LocalAppColors.current.inputFill,
    cursorColor = LocalAppColors.current.linkAccent,
    focusedBorderColor = LocalAppColors.current.linkAccent,
    unfocusedBorderColor = LocalAppColors.current.cardBorder.copy(alpha = 0.5f),
    focusedLabelColor = LocalAppColors.current.linkAccent,
    unfocusedLabelColor = LocalAppColors.current.onSurfaceMuted
)