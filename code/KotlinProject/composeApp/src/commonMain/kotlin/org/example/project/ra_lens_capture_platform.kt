package org.example.project

/** True when capture uses in-app CameraX preview; false triggers laptop/desktop webcam via backend. */
expect fun raLensUsesInlineMobileCapture(): Boolean
