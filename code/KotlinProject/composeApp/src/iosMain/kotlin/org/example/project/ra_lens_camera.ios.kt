package org.example.project

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun RaLensCameraSection(
    modifier: Modifier,
    showCaptureButton: Boolean,
    captureTrigger: Long,
    onCapture: (bytes: ByteArray) -> Unit
) {
    Text("Live camera preview is currently implemented for Android.")
}

