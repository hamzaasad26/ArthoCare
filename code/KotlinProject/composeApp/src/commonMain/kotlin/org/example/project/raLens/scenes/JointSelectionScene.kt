package org.example.project.raLens.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import org.example.project.raLens.Joint
import org.example.project.raLens.displayName

private enum class Side { LEFT, RIGHT }

private data class JointHotspot(
    val joint: Joint,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val side: Side
)

private val hotspots = listOf(
    JointHotspot(Joint.SHOULDER, 0.35f, 0.22f, Side.LEFT),
    JointHotspot(Joint.SHOULDER, 0.65f, 0.22f, Side.RIGHT),
    JointHotspot(Joint.ELBOW, 0.28f, 0.38f, Side.LEFT),
    JointHotspot(Joint.ELBOW, 0.72f, 0.38f, Side.RIGHT),
    JointHotspot(Joint.WRIST, 0.22f, 0.50f, Side.LEFT),
    JointHotspot(Joint.WRIST, 0.78f, 0.50f, Side.RIGHT),
    JointHotspot(Joint.KNEE, 0.40f, 0.68f, Side.LEFT),
    JointHotspot(Joint.KNEE, 0.60f, 0.68f, Side.RIGHT),
)

@Composable
fun JointSelectionScene(
    selectedJoint: Joint,
    onJointSelected: (Joint) -> Unit
) {
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
                Joint.entries.forEach { joint ->
                    FilterChip(
                        selected = selectedJoint == joint,
                        onClick = { onJointSelected(joint) },
                        label = { Text(joint.displayName()) }
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
                    onJointSelected = onJointSelected,
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
                val nearest = hotspots.minByOrNull { hotspot ->
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

        hotspots.forEach { hotspot ->
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
