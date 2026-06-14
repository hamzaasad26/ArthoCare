package org.example.project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
actual fun RaLensCameraSection(
    modifier: Modifier,
    showCaptureButton: Boolean,
    captureTrigger: Long,
    onCapture: (bytes: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    val previewView = remember { PreviewView(context) }
    var cameraMessage by remember { mutableStateOf("Preparing camera...") }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(captureTrigger) {
        if (captureTrigger == 0L) return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        delay(120)
        // If this effect was cancelled (navigate away / leave capture step), do not call onCapture —
        // parent state would update after composition left and can surface as composition-scope errors.
        if (!isActive) return@LaunchedEffect
        val bitmap = previewView.bitmap
        if (bitmap == null) {
            cameraMessage = "Capture failed: no frame available."
        } else {
            val buffer = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, buffer)
            onCapture(buffer.toByteArray())
            cameraMessage = "Frame captured from live camera."
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Live camera", style = MaterialTheme.typography.titleMedium)
            if (!hasCameraPermission) {
                Text(
                    "Camera permission is required for RA Lens capture.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )

                DisposableEffect(previewView, lifecycleOwner) {
                    // Async camera init: listener can run after this composable leaves the tree; guard
                    // state writes so we never touch snapshot state from a stale provider callback.
                    var disposed = false
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    val executor = ContextCompat.getMainExecutor(context)
                    val listener = Runnable {
                        if (disposed) return@Runnable
                        val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                            ?: return@Runnable
                        if (disposed) return@Runnable
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.getSurfaceProvider())
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            if (disposed) return@Runnable
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            if (disposed) return@Runnable
                            cameraMessage = "Camera active. Capture-to-ROM wiring is next."
                        } catch (_: Exception) {
                            if (!disposed) cameraMessage = "Failed to start camera preview."
                        }
                    }
                    cameraProviderFuture.addListener(listener, executor)

                    onDispose {
                        disposed = true
                        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                    }
                }
                Text(cameraMessage, style = MaterialTheme.typography.bodySmall)
                if (showCaptureButton) {
                    Button(
                        onClick = {
                            val bitmap = previewView.bitmap
                            if (bitmap == null) {
                                cameraMessage = "Capture failed: no frame available."
                            } else {
                                val buffer = ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, buffer)
                                onCapture(buffer.toByteArray())
                                cameraMessage = "Frame captured from live camera."
                            }
                        }
                    ) {
                        Text("Capture frame")
                    }
                }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

