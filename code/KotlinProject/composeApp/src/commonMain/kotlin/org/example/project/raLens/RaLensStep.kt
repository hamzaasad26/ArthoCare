package org.example.project.raLens

sealed class RaLensStep {
    data object JointSelection : RaLensStep()
    data object Capture : RaLensStep()
    data object Processing : RaLensStep()
    data object Results : RaLensStep()

    // PoseGuide intentionally removed from the active flow.
    // PoseGuidanceScene.kt + PoseGuide.kt remain on disk for optional future use.
}

enum class Joint { KNEE, WRIST, ELBOW, SHOULDER }

fun Joint.displayName(): String = when (this) {
    Joint.KNEE -> "Knee"
    Joint.WRIST -> "Wrist"
    Joint.ELBOW -> "Elbow"
    Joint.SHOULDER -> "Shoulder"
}
