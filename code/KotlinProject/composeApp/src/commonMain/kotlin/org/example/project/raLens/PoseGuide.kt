package org.example.project.raLens

data class PoseGuide(
    val joint: Joint,
    val movementName: String,
    val instructionText: String
)

val poseGuides: Map<Joint, PoseGuide> = mapOf(
    Joint.KNEE to PoseGuide(
        Joint.KNEE,
        "Knee Flexion/Extension",
        "Slowly bend and straighten your knee through full range"
    ),
    Joint.WRIST to PoseGuide(
        Joint.WRIST,
        "Wrist Flexion/Extension",
        "Flex your wrist up and down through full range"
    ),
    Joint.ELBOW to PoseGuide(
        Joint.ELBOW,
        "Elbow Flexion",
        "Bend and extend your elbow slowly"
    ),
    Joint.SHOULDER to PoseGuide(
        Joint.SHOULDER,
        "Shoulder Abduction",
        "Raise your arm out to the side and lower it"
    )
)
