package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun RaLensCameraSection(
    modifier: Modifier = Modifier,
    showCaptureButton: Boolean = true,
    captureTrigger: Long = 0L,
    onCapture: (bytes: ByteArray) -> Unit = {}
)

