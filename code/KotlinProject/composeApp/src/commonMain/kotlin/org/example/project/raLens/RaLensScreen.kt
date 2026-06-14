package org.example.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.raLens.Joint
import org.example.project.raLens.RaLensStep
import org.example.project.raLens.displayName
import org.example.project.raLens.scenes.JointSelectionScene
import org.example.project.raLens.scenes.ProcessingScene
import org.example.project.raLens.scenes.ResultsScene
import org.example.project.raLens.scenes.UnifiedCaptureScene

private enum class RaLensProcessingKind {
    MOBILE_CAPTURE,
    DESKTOP_WEBCAM
}

private const val RA_LENS_DEFAULT_PATIENT_ID = "P001"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaLensScreen(onNavigateBack: () -> Unit) {
    val usesInlineMobileCapture = raLensUsesInlineMobileCapture()
    var currentStep by remember { mutableStateOf<RaLensStep>(RaLensStep.JointSelection) }
    var selectedJoint by remember { mutableStateOf(Joint.KNEE) }
    var captureBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingProcessingKind by remember { mutableStateOf<RaLensProcessingKind?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RaLensAnalyzeResponseApi?>(null) }

    // Desktop-path auto-start: when the user lands on the Capture step on a
    // platform that uses the desktop analyzer (Android, iOS), kick off the
    // server-side OpenCV session immediately. Gated on (no in-flight session)
    // and (no leftover error from a previous attempt), so a failed run
    // returns the user to Capture with the Retry button instead of looping.
    // Keys include pendingProcessingKind so this effect does not read stale
    // snapshot state (Compose contract) and re-evaluates when pending clears.
    LaunchedEffect(currentStep, error, pendingProcessingKind, usesInlineMobileCapture) {
        if (currentStep == RaLensStep.Capture
            && !usesInlineMobileCapture
            && pendingProcessingKind == null
            && error == null
        ) {
            captureBytes = null
            pendingProcessingKind = RaLensProcessingKind.DESKTOP_WEBCAM
            currentStep = RaLensStep.Processing
        }
    }

    LaunchedEffect(currentStep, pendingProcessingKind) {
        if (currentStep != RaLensStep.Processing) return@LaunchedEffect
        if (pendingProcessingKind != null) return@LaunchedEffect
        error = "Something went wrong. Try again."
        currentStep = RaLensStep.Capture
    }

    // Desktop: do NOT key on captureBytes — unrelated ByteArray updates were restarting this
    // effect during / after auto-start, issuing a second POST /ralens/desktop/start (two terminals).
    // Long-running poll must respect cancellation (MlApiService.apiCall rethrows CancellationException).
    LaunchedEffect(currentStep, pendingProcessingKind, selectedJoint) {
        if (currentStep != RaLensStep.Processing) return@LaunchedEffect
        if (pendingProcessingKind != RaLensProcessingKind.DESKTOP_WEBCAM) return@LaunchedEffect

        error = null
        val started = MlApiService.startRaLensDesktopAnalyzer(
            RaLensDesktopStartRequestApi(
                joints = listOf(selectedJoint.displayName()),
                patientId = RA_LENS_DEFAULT_PATIENT_ID,
                cameraChoice = "1"
            )
        )
        val ok = started.getOrNull()
        if (ok == null) {
            error = started.exceptionOrNull()?.message
            currentStep = RaLensStep.Capture
            pendingProcessingKind = null
            return@LaunchedEffect
        }
        var attempts = 180
        var consecutiveFailures = 0
        val maxConsecutiveFailures = 5
        while (attempts > 0) {
            attempts--
            val status = MlApiService.getRaLensDesktopStatus(ok.runId).getOrNull()
            if (status == null) {
                consecutiveFailures++
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    error = "Could not read capture status. Try again."
                    break
                }
                delay(2000)
                continue
            }
            consecutiveFailures = 0
            if (status.status == "COMPLETED") {
                val analysis = status.analysis
                if (analysis != null) {
                    result = analysis
                    RaLensStore.mergeAnalysis(
                        analysis,
                        captureMode = if (raLensUsesInlineMobileCapture()) "mobile" else "desktop",
                        sessionJointLabel = selectedJoint.displayName().lowercase(),
                        sessionSide = "left"
                    )
                    currentStep = RaLensStep.Results
                    pendingProcessingKind = null
                    return@LaunchedEffect
                }
                error = status.reportParseError ?: "Capture finished but no report data was returned."
                break
            }
            if (status.status == "FAILED") {
                error = "Capture session failed. Check the computer window or try again."
                break
            }
            delay(2000)
        }
        if (currentStep == RaLensStep.Processing) {
            error = error ?: "Timed out waiting for capture to finish."
            currentStep = RaLensStep.Capture
        }
        pendingProcessingKind = null
    }

    LaunchedEffect(currentStep, pendingProcessingKind, captureBytes, selectedJoint) {
        if (currentStep != RaLensStep.Processing) return@LaunchedEffect
        if (pendingProcessingKind != RaLensProcessingKind.MOBILE_CAPTURE) return@LaunchedEffect

        error = null
        val bytes = captureBytes ?: run {
            error = "Capture a frame first."
            currentStep = RaLensStep.Capture
            pendingProcessingKind = null
            return@LaunchedEffect
        }
        val response = MlApiService.analyzeRaLensMedia(
            joint = selectedJoint.displayName(),
            side = "left",
            bytes = bytes
        )
        result = response.getOrNull()
        RaLensStore.mergeAnalysis(
            result,
            captureMode = if (raLensUsesInlineMobileCapture()) "mobile" else "desktop",
            sessionJointLabel = selectedJoint.displayName().lowercase(),
            sessionSide = "left"
        )
        error = response.exceptionOrNull()?.message
        if (response.isSuccess) currentStep = RaLensStep.Results
        else currentStep = RaLensStep.Capture
        pendingProcessingKind = null
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                "RA Lens ROM analysis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = LocalAppColors.current.sectionHighlight
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(300)),
                        initialContentExit = fadeOut(tween(300))
                    )
                },
                label = "ra_lens_step"
            ) { step ->
                when (step) {
                    RaLensStep.JointSelection -> JointSelectionScene(
                        selectedJoint = selectedJoint,
                        onJointSelected = { selectedJoint = it }
                    )

                    RaLensStep.Capture -> UnifiedCaptureScene(
                        selectedJoint = selectedJoint,
                        usesInlineMobileCapture = usesInlineMobileCapture,
                        onCaptured = { bytes ->
                            captureBytes = bytes
                            pendingProcessingKind = RaLensProcessingKind.MOBILE_CAPTURE
                            currentStep = RaLensStep.Processing
                        },
                        hasError = error != null,
                        onRetryDesktop = {
                            // Clearing error lets the auto-start
                            // LaunchedEffect re-trigger immediately.
                            error = null
                            captureBytes = null
                            pendingProcessingKind = RaLensProcessingKind.DESKTOP_WEBCAM
                            currentStep = RaLensStep.Processing
                        }
                    )

                    RaLensStep.Processing -> ProcessingScene()

                    RaLensStep.Results -> ResultsScene(
                        result = result,
                        onExamineAnotherJoint = {
                            captureBytes = null
                            result = null
                            error = null
                            pendingProcessingKind = null
                            currentStep = RaLensStep.JointSelection
                        }
                    )
                }
            }

            if (currentStep == RaLensStep.JointSelection) {
                androidx.compose.material3.Button(
                    onClick = { currentStep = RaLensStep.Capture },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Exam")
                }
            }

            error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
