package org.example.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: (userName: String) -> Unit,
    onBackToLoginClicked: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(SignUpFormData()) }
    var signUpMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // "Sign In" link at TOP - away from Next button to prevent accidental taps
            TextButton(
                onClick = onBackToLoginClicked,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalAppColors.current.linkAccent),
                modifier = Modifier.align(Alignment.End),
                enabled = !isLoading
            ) {
                Text("Already have an account? Sign In")
            }
            LinearProgressIndicator(
                progress = (currentStep + 1).toFloat() / TOTAL_STEPS,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 16.dp),
                color = LocalAppColors.current.linkAccent,
                trackColor = LocalAppColors.current.cardSurface
            )
            Text(
                text = "Step ${currentStep + 1} of $TOTAL_STEPS",
                style = MaterialTheme.typography.labelMedium,
                color = LocalAppColors.current.onSurfaceMuted,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Step content - fill remaining space and scroll if needed
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentStep) {
                    0 -> SignUpStepAccount(
                        data = data,
                        onDataChange = { data = it },
                        enabled = !isLoading
                    )
                    1 -> SignUpStepPersonalInfo(
                        data = data,
                        onDataChange = { data = it },
                        enabled = !isLoading
                    )
                    2 -> SignUpStepLifestyleDiet(
                        data = data,
                        onDataChange = { data = it },
                        enabled = !isLoading
                    )
                }
            }

            // Message
            if (signUpMessage.isNotEmpty()) {
                Text(
                    text = signUpMessage,
                    color = if (signUpMessage.startsWith("Error"))
                        MaterialTheme.colorScheme.error
                    else
                        LocalAppColors.current.linkAccent,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.linkAccent),
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = {
                        when (currentStep) {
                            0 -> {
                                if (data.username.isBlank() || data.password.isBlank()) {
                                    signUpMessage = "Error: Username and password are required"
                                    return@Button
                                }
                                if (data.password != data.confirmPassword) {
                                    signUpMessage = "Error: Passwords do not match"
                                    return@Button
                                }
                                val validation = PasswordValidator.validate(data.password)
                                if (!validation.isValid) {
                                    signUpMessage = "Error: ${validation.message}"
                                    return@Button
                                }
                                signUpMessage = ""
                                currentStep++
                            }
                            1 -> {
                                if (data.fullName.isBlank()) {
                                    signUpMessage = "Error: Full name is required"
                                    return@Button
                                }
                                signUpMessage = ""
                                currentStep++
                            }
                            2 -> {
                                isLoading = true
                                signUpMessage = ""
                                coroutineScope.launch {
                                    try {
                                        val result = AuthService.signUp(data)
                                        if (result.isSuccess) {
                                            signUpMessage = "Account created successfully!"
                                            kotlinx.coroutines.delay(500)
                                            onSignUpSuccess(data.fullName)
                                        } else {
                                            signUpMessage =
                                                "Error: ${result.exceptionOrNull()?.message ?: "Sign up failed"}"
                                        }
                                    } catch (e: Exception) {
                                        signUpMessage = "Error: ${e.message ?: "Sign up failed"}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.primaryButton,
                        contentColor = LocalAppColors.current.onAccent
                    ),
                    enabled = !isLoading
                ) {
                    if (currentStep == 2 && isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = LocalAppColors.current.onAccent
                        )
                    } else {
                        Text(
                            if (currentStep == 2) "Create Account" else "Next",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// --- Step 0: Account ---
@Composable
private fun SignUpStepAccount(
    data: SignUpFormData,
    onDataChange: (SignUpFormData) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Create Your Account",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = LocalAppColors.current.sectionHighlight
        )
        Text(
            text = "Choose a username and password",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.onSurfaceSecondary
        )
        CompactOutlinedTextField(
            value = data.username,
            onValueChange = { onDataChange(data.copy(username = it)) },
            label = { Text("Username") },
            enabled = enabled
        )
        CompactOutlinedTextField(
            value = data.password,
            onValueChange = { onDataChange(data.copy(password = it)) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = enabled
        )
        CompactOutlinedTextField(
            value = data.confirmPassword,
            onValueChange = { onDataChange(data.copy(confirmPassword = it)) },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = enabled
        )
        if (data.confirmPassword.isNotEmpty() && data.password != data.confirmPassword) {
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (data.password.isNotEmpty()) {
            val validation = PasswordValidator.validate(data.password)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (validation.isValid)
                        LocalAppColors.current.heroSurface
                    else
                        LocalAppColors.current.warningSurface.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (validation.isValid) "✓ Strong password" else "Password requirements:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (validation.isValid)
                            LocalAppColors.current.linkAccent
                        else
                            MaterialTheme.colorScheme.error
                    )
                    if (!validation.isValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        PasswordValidator.getRequirements().forEach {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.onSurfaceSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Step 1: Personal Info ---
private val RACE_ETHNICITY_OPTIONS = listOf(
    "White", "Black or African American", "Asian", "Hispanic or Latino",
    "American Indian or Alaska Native", "Native Hawaiian or Pacific Islander",
    "Two or More Races", "Other", "Prefer not to say"
)

@Composable
private fun SignUpStepPersonalInfo(
    data: SignUpFormData,
    onDataChange: (SignUpFormData) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Personal Information",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = LocalAppColors.current.sectionHighlight
        )
        Text(
            text = "Tell us a bit about yourself",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.onSurfaceSecondary
        )
        CompactOutlinedTextField(
            value = data.fullName,
            onValueChange = { onDataChange(data.copy(fullName = it)) },
            label = { Text("Full Name") },
            enabled = enabled
        )
        Text(
            text = "Date of Birth",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.sectionHighlight,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FormDropdown(
                label = "Day",
                options = (1..31).map { it.toString() },
                selectedValue = data.birthDay,
                onValueSelected = { onDataChange(data.copy(birthDay = it)) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            FormDropdown(
                label = "Month",
                options = (1..12).map { it.toString() },
                selectedValue = data.birthMonth,
                onValueSelected = { onDataChange(data.copy(birthMonth = it)) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            FormDropdown(
                label = "Year",
                options = (2025 downTo 1900).map { it.toString() },
                selectedValue = data.birthYear,
                onValueSelected = { onDataChange(data.copy(birthYear = it)) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
        FormDropdown(
            label = "Gender",
            options = listOf("Male", "Female", "Other"),
            selectedValue = data.gender,
            onValueSelected = { onDataChange(data.copy(gender = it)) },
            enabled = enabled
        )
        FormDropdown(
            label = "Race / Ethnicity",
            options = RACE_ETHNICITY_OPTIONS,
            selectedValue = data.raceEthnicity,
            onValueSelected = { onDataChange(data.copy(raceEthnicity = it)) },
            enabled = enabled
        )
    }
}

// --- Step 2: Lifestyle & Diet ---
@Composable
private fun SignUpStepLifestyleDiet(
    data: SignUpFormData,
    onDataChange: (SignUpFormData) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Lifestyle & Diet",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = LocalAppColors.current.sectionHighlight
        )
        FormDropdown(
            label = "Physical Activity",
            options = listOf("Sedentary", "Moderate", "Rigorous"),
            selectedValue = data.physicalActivity,
            onValueSelected = { onDataChange(data.copy(physicalActivity = it)) },
            enabled = enabled
        )
        FormDropdown(
            label = "Smoking",
            options = listOf("Never", "Former", "Current"),
            selectedValue = data.smoking,
            onValueSelected = { onDataChange(data.copy(smoking = it)) },
            enabled = enabled
        )
        FormDropdown(
            label = "Drinking",
            options = listOf("Never", "Occasional", "Frequent"),
            selectedValue = data.drinking,
            onValueSelected = { onDataChange(data.copy(drinking = it)) },
            enabled = enabled
        )
        CompactOutlinedTextField(
            value = data.caloriesPerDay,
            onValueChange = { onDataChange(data.copy(caloriesPerDay = it)) },
            label = { Text("Calories per day (kcal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = data.proteinG,
                onValueChange = { onDataChange(data.copy(proteinG = it)) },
                label = { Text("Protein (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled
            )
            CompactOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = data.carbsG,
                onValueChange = { onDataChange(data.copy(carbsG = it)) },
                label = { Text("Carbs (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled
            )
            CompactOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = data.fatG,
                onValueChange = { onDataChange(data.copy(fatG = it)) },
                label = { Text("Fat (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled
            )
        }
        CompactOutlinedTextField(
            value = data.caffeineG,
            onValueChange = { onDataChange(data.copy(caffeineG = it)) },
            label = { Text("Caffeine (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled
        )
        CompactOutlinedTextField(
            value = data.fiberG,
            onValueChange = { onDataChange(data.copy(fiberG = it)) },
            label = { Text("Fiber (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled
        )
        FormDropdown(
            label = "Hypertension",
            options = listOf("Yes", "No"),
            selectedValue = data.hypertension,
            onValueSelected = { onDataChange(data.copy(hypertension = it)) },
            enabled = enabled
        )
        FormDropdown(
            label = "Diabetes",
            options = listOf("None", "Pre", "Diabetic"),
            selectedValue = data.diabetes,
            onValueSelected = { onDataChange(data.copy(diabetes = it)) },
            enabled = enabled
        )
        FormDropdown(
            label = "Hyperlipidemia",
            options = listOf("Yes", "No"),
            selectedValue = data.hyperlipidemia,
            onValueSelected = { onDataChange(data.copy(hyperlipidemia = it)) },
            enabled = enabled
        )
    }
}

// --- Helper composables ---
@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        enabled = enabled,
        colors = signUpFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDropdown(
    label: String,
    options: List<String>,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .height(56.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(12.dp),
            enabled = enabled,
            colors = signUpFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = LocalAppColors.current.onSurfaceStrong) },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun signUpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalAppColors.current.onSurfaceStrong,
    unfocusedTextColor = LocalAppColors.current.onSurfaceStrong,
    disabledTextColor = LocalAppColors.current.onSurfaceMuted,
    focusedContainerColor = LocalAppColors.current.inputFill,
    unfocusedContainerColor = LocalAppColors.current.inputFill,
    disabledContainerColor = LocalAppColors.current.inputFill,
    cursorColor = LocalAppColors.current.linkAccent,
    focusedBorderColor = LocalAppColors.current.linkAccent,
    unfocusedBorderColor = LocalAppColors.current.cardBorder.copy(alpha = 0.5f),
    disabledBorderColor = LocalAppColors.current.cardBorder.copy(alpha = 0.25f),
    focusedLabelColor = LocalAppColors.current.linkAccent,
    unfocusedLabelColor = LocalAppColors.current.onSurfaceMuted,
    disabledLabelColor = LocalAppColors.current.onSurfaceFaint
)
