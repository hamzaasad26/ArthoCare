package org.example.project

/**
 * Default **false** for demos: `/ralens/desktop/start` runs the full OpenCV scripts on the dev PC
 * (e.g. laptop webcam while you drive the flow from Android Studio / emulator).
 *
 * Capture UI still shows a normal phone-style live preview (emulator can use the host webcam).
 *
 * When **true**, a single JPEG goes to `/ralens/analyze-media` (placeholder ROM estimate today—not the scripts).
 */
actual fun raLensUsesInlineMobileCapture(): Boolean = false
