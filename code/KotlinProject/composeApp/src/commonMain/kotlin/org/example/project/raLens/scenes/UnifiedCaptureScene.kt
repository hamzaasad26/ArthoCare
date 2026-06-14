package org.example.project.raLens.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.LocalAppColors
import org.example.project.RaLensCameraSection
import org.example.project.raLens.Joint
import org.example.project.raLens.displayName

/**
 * Capture step UI.
 *
 * Two paths, selected by [usesInlineMobileCapture]:
 *  - Inline mobile (currently false on Android & iOS): the live phone camera
 *    preview renders immediately; the camera section's own "Capture frame"
 *    button takes the still that gets sent for analysis. No alignment chrome,
 *    no separate "Start Camera" button.
 *  - Desktop (default on Android/iOS): the desktop analyzer session is
 *    auto-started by the parent the moment this step is entered, so this UI
 *    is normally only visible for a brief flash before navigation moves to
 *    Processing. It only stays visible if a previous attempt errored — in
 *    that case the user sees a Retry button to restart the session.
 */
@Composable
fun UnifiedCaptureScene(
    selectedJoint: Joint,
    usesInlineMobileCapture: Boolean,
    onCaptured: (ByteArray) -> Unit,
    hasError: Boolean,
    onRetryDesktop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = selectedJoint.displayName(),
            style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.sectionHighlight
        )

        if (usesInlineMobileCapture) {
            // Live phone preview. The camera section renders its own
            // "Capture frame" button which fires onCapture(bytes) directly.
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                RaLensCameraSection(
                    modifier = Modifier.fillMaxWidth(),
                    showCaptureButton = true,
                    captureTrigger = 0L,
                    onCapture = onCaptured
                )
            }
        } else {
            // Desktop analyzer path. Parent auto-triggers the session on
            // Capture entry. Only render the retry affordance after an
            // error; otherwise show a brief "starting…" status that the
            // user normally won't see (immediate navigation to Processing).
            if (hasError) {
                Text(
                    text = "Last capture attempt didn't complete. " +
                            "Tap Retry to start a new session on the connected computer.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onRetryDesktop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry capture")
                }
            } else {
                Text(
                    text = "Starting capture session on the connected computer. " +
                            "Follow the on-screen prompts in the OpenCV window.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
