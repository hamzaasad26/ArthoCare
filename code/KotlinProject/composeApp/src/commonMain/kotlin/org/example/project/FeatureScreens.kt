package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Joint { SHOULDER, ELBOW, WRIST, KNEE }
private enum class Side { LEFT, RIGHT }

private data class JointHotspot(
    val joint: Joint,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val side: Side
)

private val skeletonHotspots = listOf(
    JointHotspot(Joint.SHOULDER, 0.35f, 0.22f, Side.LEFT),
    JointHotspot(Joint.SHOULDER, 0.65f, 0.22f, Side.RIGHT),
    JointHotspot(Joint.ELBOW, 0.28f, 0.38f, Side.LEFT),
    JointHotspot(Joint.ELBOW, 0.72f, 0.38f, Side.RIGHT),
    JointHotspot(Joint.WRIST, 0.22f, 0.50f, Side.LEFT),
    JointHotspot(Joint.WRIST, 0.78f, 0.50f, Side.RIGHT),
    JointHotspot(Joint.KNEE, 0.40f, 0.68f, Side.LEFT),
    JointHotspot(Joint.KNEE, 0.60f, 0.68f, Side.RIGHT)
)

private fun Joint.displayName(): String = when (this) {
    Joint.SHOULDER -> "Shoulder"
    Joint.ELBOW -> "Elbow"
    Joint.WRIST -> "Wrist"
    Joint.KNEE -> "Knee"
}

// A helper function to create a standard Top App Bar with a back button.
// Styled to match the dashboard chrome so every feature screen shares the
// same dark surface, logo-purple title, and accent-tinted back action.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureTopAppBar(title: String, onNavigateBack: () -> Unit) {
    val colors = LocalAppColors.current
    TopAppBar(
        title = {
            Text(
                title,
                color = colors.sectionHighlight,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        },
        navigationIcon = {
            TextButton(
                onClick = onNavigateBack,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.linkAccent)
            ) {
                Text("Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.chromeSurface,
            titleContentColor = colors.sectionHighlight,
            navigationIconContentColor = colors.linkAccent,
            actionIconContentColor = colors.sectionHighlight
        )
    )
}

// A helper for the main content area of each feature screen.
@Composable
fun FeatureContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Content for $title goes here.",
            textAlign = TextAlign.Center,
            color = LocalAppColors.current.onSurfaceSecondary
        )
        content()
    }
}

// --- 1. RA Predictions Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaPredictionsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "RA Predictions", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            FeatureContent("RA Predictions") {
                // TODO: Add UI for showing flare-up predictions.
            }
        }
    }
}

// --- 2. Diet Plans Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietPlansScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Diet Plans", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            FeatureContent("Diet Plans") {
                // TODO: Add UI for displaying recipes and dietary advice.
            }
        }
    }
}

// --- 3. Weather Alerts Screen → implemented in WeatherAlertsScreen.kt ---

// --- 5. RA Lens Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("unused")
private fun LegacyRaLensScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val joints = remember { Joint.entries }
    val desktopJoints = remember { listOf("Shoulder", "Elbow", "Wrist", "Knee", "Hand") }
    var selectedJoint by remember { mutableStateOf(Joint.KNEE) }
    var selectedDesktopJoints by remember { mutableStateOf(setOf("Knee")) }
    var patientId by remember { mutableStateOf("P001") }
    var desktopRunId by remember { mutableStateOf<String?>(null) }
    var desktopStatus by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RaLensAnalyzeResponseApi?>(null) }

    Scaffold(
        topBar = { FeatureTopAppBar(title = "RA Lens", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RA Lens ROM analysis",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Choose a joint to run ROM analysis and continue with RA Lens exam steps.",
                style = MaterialTheme.typography.bodyMedium
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.35f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Select joint", style = MaterialTheme.typography.titleSmall)
                        joints.forEach { joint ->
                            FilterChip(
                                selected = selectedJoint == joint,
                                onClick = { selectedJoint = joint },
                                label = { Text(joint.displayName()) },
                                enabled = !loading,
                                modifier = Modifier.widthIn(min = 96.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .height(340.dp)
                    ) {
                        SkeletonJointSelector(
                            selectedJoint = selectedJoint,
                            onJointSelected = { selectedJoint = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    text = "Selected joint: ${selectedJoint.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            HorizontalDivider()
            Text("Desktop analyzer mode (laptop webcam)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use this to run the full Python analyzer window on your laptop camera and fetch its final ROM report back into app.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = { Text("Patient ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Select one or more joints for a single desktop run:",
                style = MaterialTheme.typography.bodySmall
            )
            desktopJoints.forEach { joint ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(joint)
                    Checkbox(
                        checked = selectedDesktopJoints.contains(joint),
                        onCheckedChange = { checked ->
                            selectedDesktopJoints = if (checked) {
                                selectedDesktopJoints + joint
                            } else {
                                selectedDesktopJoints - joint
                            }
                        },
                        enabled = !loading
                    )
                }
            }
            Button(
                onClick = {
                    loading = true
                    error = null
                    desktopStatus = null
                    scope.launch {
                        if (selectedDesktopJoints.isEmpty()) {
                            error = "Select at least one joint for desktop analyzer."
                            loading = false
                            return@launch
                        }
                        val started = MlApiService.startRaLensDesktopAnalyzer(
                            RaLensDesktopStartRequestApi(
                                joints = desktopJoints.filter { selectedDesktopJoints.contains(it) },
                                patientId = patientId.ifBlank { "P001" },
                                cameraChoice = "1"
                            )
                        )
                        val ok = started.getOrNull()
                        if (ok == null) {
                            error = started.exceptionOrNull()?.message
                        } else {
                            desktopRunId = ok.runId
                            desktopStatus = "Desktop analyzer started. Complete both sides in the opened window."
                            var attempts = 180
                            while (attempts > 0) {
                                attempts--
                                val status = MlApiService.getRaLensDesktopStatus(ok.runId).getOrNull()
                                if (status == null) {
                                    desktopStatus = "Could not read status from backend. Tap Start laptop analyzer to try again."
                                    break
                                }
                                if (status.status == "COMPLETED" && status.analysis != null) {
                                    result = status.analysis
                                    RaLensStore.mergeAnalysis(
                                        status.analysis,
                                        captureMode = "desktop",
                                        sessionJointLabel = selectedDesktopJoints.joinToString(",").lowercase(),
                                        sessionSide = "unspecified"
                                    )
                                    desktopStatus = "Desktop analyzer completed and synced."
                                    break
                                }
                                if (status.status == "FAILED") {
                                    error = "Desktop analyzer failed. Check backend log."
                                    desktopStatus = "Desktop analyzer failed."
                                    break
                                }
                                if (status.status == "COMPLETED" && status.analysis == null &&
                                    status.reportParseError != null
                                ) {
                                    error = status.reportParseError
                                    desktopStatus = "Report file could not be read."
                                    break
                                }
                                if (status.status == "COMPLETED" && status.analysis == null) {
                                    desktopStatus = "Analyzer finished but no report data was returned."
                                    break
                                }
                                desktopStatus = when (status.status) {
                                    "PENDING_REPORT" -> "Finalizing report… checking status"
                                    else -> "Waiting for desktop analyzer… checking status"
                                }
                                delay(2000)
                            }
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start laptop analyzer") }
            Text(
                "When the laptop analyzer finishes, results sync automatically (no refresh button).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (desktopRunId != null) {
                Text("Desktop run id: $desktopRunId", style = MaterialTheme.typography.bodySmall)
            }
            if (desktopStatus != null) {
                Text(desktopStatus!!, style = MaterialTheme.typography.bodySmall)
            }

            if (error != null) {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

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
                            JointRankRow(
                                rank = index + 1,
                                title = row.joint.toDisplayJointName(),
                                status = row.status,
                                deficitPercent = row.deficitPct
                            )
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
            }
        }
    }
}

@Composable
private fun SkeletonJointSelector(
    selectedJoint: Joint,
    onJointSelected: (Joint) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val unselectedColor = Color(0xFF378ADD).copy(alpha = 0.3f)
    val glowColor = Color(0xFF378ADD).copy(alpha = 0.25f)
    val ringColor = Color(0xFF185FA5)
    val dotColor = Color(0xFF378ADD)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tapOffset ->
                val maxDistancePx = with(density) { 40.dp.toPx() }
                val nearest = skeletonHotspots.minByOrNull { hotspot ->
                    val hx = hotspot.offsetXFraction * size.width
                    val hy = hotspot.offsetYFraction * size.height
                    val dx = tapOffset.x - hx
                    val dy = tapOffset.y - hy
                    dx * dx + dy * dy
                }
                if (nearest != null) {
                    val hx = nearest.offsetXFraction * size.width
                    val hy = nearest.offsetYFraction * size.height
                    val dx = tapOffset.x - hx
                    val dy = tapOffset.y - hy
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance <= maxDistancePx) onJointSelected(nearest.joint)
                }
            }
        }
    ) {
        val stroke = 3.dp.toPx()
        val headCx = 0.5f * size.width
        val headCy = 0.10f * size.height
        val headW = 0.18f * size.width
        val headH = 0.14f * size.height
        drawOval(
            color = skeletonColor,
            topLeft = Offset(headCx - headW / 2f, headCy - headH / 2f),
            size = Size(headW, headH),
            style = Stroke(width = stroke)
        )

        val neck = Offset(0.5f * size.width, 0.18f * size.height)
        val shoulderLeft = Offset(0.35f * size.width, 0.22f * size.height)
        val shoulderRight = Offset(0.65f * size.width, 0.22f * size.height)
        val hipLeft = Offset(0.44f * size.width, 0.56f * size.height)
        val hipRight = Offset(0.56f * size.width, 0.56f * size.height)
        val pelvis = Offset(0.5f * size.width, 0.59f * size.height)

        val torso = Path().apply {
            moveTo(shoulderLeft.x, shoulderLeft.y)
            lineTo(shoulderRight.x, shoulderRight.y)
            lineTo(hipRight.x, hipRight.y)
            lineTo(hipLeft.x, hipLeft.y)
            close()
        }
        drawPath(path = torso, color = skeletonColor, style = Stroke(width = stroke))
        drawLine(skeletonColor, neck, pelvis, strokeWidth = stroke, cap = StrokeCap.Round)

        val elbowLeft = Offset(0.28f * size.width, 0.38f * size.height)
        val wristLeft = Offset(0.22f * size.width, 0.50f * size.height)
        val elbowRight = Offset(0.72f * size.width, 0.38f * size.height)
        val wristRight = Offset(0.78f * size.width, 0.50f * size.height)
        drawLine(skeletonColor, shoulderLeft, elbowLeft, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, elbowLeft, wristLeft, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, shoulderRight, elbowRight, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, elbowRight, wristRight, strokeWidth = stroke, cap = StrokeCap.Round)
        drawOval(
            color = skeletonColor,
            topLeft = Offset(wristLeft.x - 0.02f * size.width, wristLeft.y - 0.015f * size.height),
            size = Size(0.04f * size.width, 0.03f * size.height),
            style = Stroke(width = stroke)
        )
        drawOval(
            color = skeletonColor,
            topLeft = Offset(wristRight.x - 0.02f * size.width, wristRight.y - 0.015f * size.height),
            size = Size(0.04f * size.width, 0.03f * size.height),
            style = Stroke(width = stroke)
        )

        val kneeLeft = Offset(0.40f * size.width, 0.68f * size.height)
        val footAnchorLeft = Offset(0.40f * size.width, 0.87f * size.height)
        val kneeRight = Offset(0.60f * size.width, 0.68f * size.height)
        val footAnchorRight = Offset(0.60f * size.width, 0.87f * size.height)
        drawLine(skeletonColor, hipLeft, kneeLeft, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, kneeLeft, footAnchorLeft, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, hipRight, kneeRight, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(skeletonColor, kneeRight, footAnchorRight, strokeWidth = stroke, cap = StrokeCap.Round)

        val leftFoot = Path().apply {
            moveTo(footAnchorLeft.x - 0.03f * size.width, footAnchorLeft.y + 0.01f * size.height)
            lineTo(footAnchorLeft.x + 0.045f * size.width, footAnchorLeft.y + 0.01f * size.height)
            lineTo(footAnchorLeft.x + 0.03f * size.width, footAnchorLeft.y + 0.03f * size.height)
            lineTo(footAnchorLeft.x - 0.025f * size.width, footAnchorLeft.y + 0.03f * size.height)
            close()
        }
        val rightFoot = Path().apply {
            moveTo(footAnchorRight.x - 0.045f * size.width, footAnchorRight.y + 0.01f * size.height)
            lineTo(footAnchorRight.x + 0.03f * size.width, footAnchorRight.y + 0.01f * size.height)
            lineTo(footAnchorRight.x + 0.025f * size.width, footAnchorRight.y + 0.03f * size.height)
            lineTo(footAnchorRight.x - 0.03f * size.width, footAnchorRight.y + 0.03f * size.height)
            close()
        }
        drawPath(path = leftFoot, color = skeletonColor, style = Stroke(width = stroke))
        drawPath(path = rightFoot, color = skeletonColor, style = Stroke(width = stroke))

        skeletonHotspots.forEach { hotspot ->
            val center = Offset(
                hotspot.offsetXFraction * size.width,
                hotspot.offsetYFraction * size.height
            )
            if (hotspot.joint == selectedJoint) {
                drawCircle(color = glowColor, radius = 32.dp.toPx(), center = center)
                drawCircle(
                    color = ringColor,
                    radius = 18.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(color = dotColor, radius = 9.dp.toPx(), center = center)
            } else {
                drawCircle(
                    color = unselectedColor,
                    radius = 12.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun JointRankRow(
    rank: Int,
    title: String,
    status: String,
    deficitPercent: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$rank. $title")
        Text("${deficitPercent.toInt()}% ($status)", style = MaterialTheme.typography.bodySmall)
    }
}

// --- 6. Tips and Exercises Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsAndExercisesScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Tips & Exercises", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            FeatureContent("Tips & Exercises") {
                // TODO: Display a list of gentle exercises and RA management tips.
            }
        }
    }
}

// --- 7. Settings Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopAppBar(title = "Settings", onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = colors.linkAccent,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Appearance",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurfaceStrong,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            if (isDarkTheme) "Dark theme is on" else "Light theme is on",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary
                        )
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onDarkThemeChange,
                    )
                }
            }
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelLarge,
                color = colors.sectionHighlight,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Card(
                onClick = onLogout,
                colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Logout",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            "Sign out of this account",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary
                        )
                    }
                    Text(
                        ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceMuted
                    )
                }
            }
        }
    }
}
