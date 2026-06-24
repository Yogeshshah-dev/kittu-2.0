package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ZoyaState
import kotlin.math.*

@Composable
fun ZoyaVoiceOrb(
    zoyaState: ZoyaState,
    audioLevel: Float, // current active audio level (userLevel or zoyaLevel) from 0f to 1f
    modifier: Modifier = Modifier,
    orbSize: Dp = 140.dp
) {
    // Phase animations for wavy liquid outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "orb_loops")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    // Rotation angles for 3D sphere particles rotation
    val angleY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleY"
    )

    val angleX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleX"
    )

    val angleZ by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleZ"
    )

    // Soft beat/pulsing of background glow
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = SineTransitionSpec().easing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Base properties according to state
    val colorPrimary = when (zoyaState) {
        ZoyaState.IDLE -> Color(0xFFE2E2E6) // Pristine clean white (replicates the white video)
        ZoyaState.LISTENING -> Color(0xFF00E676) // Electric Neon Green
        ZoyaState.THINKING -> Color(0xFF00B0FF) // Cyber Radiant Blue
        ZoyaState.SPEAKING -> Color(0xFFD500F9) // Hyper Magenta Purple
    }

    // Secondary color for rich multi-color gradient style
    val colorSecondary = when (zoyaState) {
        ZoyaState.IDLE -> Color(0xFF7F8C8D)
        ZoyaState.LISTENING -> Color(0xFF1B5E20)
        ZoyaState.THINKING -> Color(0xFF0D47A1)
        ZoyaState.SPEAKING -> Color(0xFF4A148C)
    }

    // Number of particles on sphere grid
    val particleCount = 130
    val spherePoints = remember {
        List(particleCount) { idx ->
            // Uniform golden spiral distribution
            val y = 1f - (idx / (particleCount - 1f)) * 2f
            val radiusAtY = sqrt(1f - y * y)
            val goldenAngle = 2.399963f
            val theta = idx * goldenAngle
            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY
            Triple(x, y, z)
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sizeVal = size.width
            val center = Offset(sizeVal / 2f, sizeVal / 2f)
            val baseRadius = sizeVal / 2.8f

            // 1. Draw central core ambient back glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorPrimary.copy(alpha = 0.18f * glowPulse),
                        colorSecondary.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.6f
                ),
                center = center,
                radius = baseRadius * 1.6f
            )

            // 2. Draw 3D Spherical Particle Grid (Inside)
            val rotYRad = Math.toRadians(angleY.toDouble()).toFloat()
            val rotXRad = Math.toRadians(angleX.toDouble()).toFloat()
            val rotZRad = Math.toRadians(angleZ.toDouble()).toFloat()

            // Pre-calculate trig values for rotation efficiency
            val cosY = cos(rotYRad)
            val sinY = sin(rotYRad)
            val cosX = cos(rotXRad)
            val sinX = sin(rotXRad)
            val cosZ = cos(rotZRad)
            val sinZ = sin(rotZRad)

            val sphereRadius = baseRadius * 0.85f
            val projectedPoints = Array(particleCount) { Offset.Zero }
            val depthWeights = FloatArray(particleCount)

            for (i in 0 until particleCount) {
                val pt = spherePoints[i]
                val ox = pt.first
                val oy = pt.second
                val oz = pt.third

                // Apply 3D Y-axis Rotation
                val x1 = ox * cosY - oz * sinY
                val z1 = ox * sinY + oz * cosY
                val y1 = oy

                // Apply 3D X-axis Rotation
                val y2 = y1 * cosX - z1 * sinX
                val z2 = y1 * sinX + z1 * cosX
                val x2 = x1

                // Apply 3D Z-axis Rotation
                val x3 = x2 * cosZ - y2 * sinZ
                val y3 = x2 * sinZ + y2 * cosZ
                val z3 = z2 // Final Depth coordinate (-1f to 1f)

                // Perspective projection factor (distance = 2.4)
                val focalLength = 2.4f
                val scale = focalLength / (focalLength + z3)

                val projX = center.x + x3 * sphereRadius * scale
                val projY = center.y + y3 * sphereRadius * scale

                projectedPoints[i] = Offset(projX, projY)
                depthWeights[i] = z3 // Save raw depth for lighting/sorting
            }

            // Draw connecting web-mesh lines first so dots stay on top
            // To prevent chaotic webs, we link points sequentially in the golden spiral ordering
            for (i in 0 until particleCount - 2) {
                val depthAvg = (depthWeights[i] + depthWeights[i + 1]) / 2f
                val alphaBase = if (depthAvg > 0f) 0.35f else 0.12f
                val lineAlpha = alphaBase * (1.2f - (depthAvg + 1f)/2f) // closer = less transparent

                // Line opacity responds to audio volume pulse too
                val voicePulseCoeff = 1f + audioLevel * 0.4f
                val finalAlpha = (lineAlpha * voicePulseCoeff).coerceIn(0.01f, 0.6f)

                // Connect to next and spaced points (creates beautiful structural longitude threads)
                drawLine(
                    color = colorPrimary.copy(alpha = finalAlpha),
                    start = projectedPoints[i],
                    end = projectedPoints[i + 1],
                    strokeWidth = (if (depthAvg > 0f) 1.2.dp else 0.6.dp).toPx()
                )

                if (i + 4 < particleCount) {
                    drawLine(
                        color = colorSecondary.copy(alpha = finalAlpha * 0.6f),
                        start = projectedPoints[i],
                        end = projectedPoints[i + 4],
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
            }

            // Draw spherical dots with depth-based sizes & brightness
            for (i in 0 until particleCount) {
                val depth = depthWeights[i] // Depth -1f (deep back) to 1f (front-most)
                val projPt = projectedPoints[i]

                // Map depth to size & alpha
                // Front dots are larger & highly glowing, back dots are deep, tiny, and faint
                val mappedSize = if (depth > 0) {
                    (1.5f + depth * 2.2f).dp.toPx()
                } else {
                    (1.5f + (depth + 1f) * 0.5f).dp.toPx()
                }

                val mappedAlpha = if (depth > 0) {
                    0.25f + depth * 0.75f
                } else {
                    0.1f * (depth + 1f) + 0.15f
                }

                val dynamicAudioGlow = if (depth > 0) audioLevel * 0.3f else 0f
                val finalAlpha = (mappedAlpha + dynamicAudioGlow).coerceIn(0.1f, 1f)

                drawCircle(
                    color = colorPrimary.copy(alpha = finalAlpha),
                    center = projPt,
                    radius = mappedSize
                )
            }

            // 3. Draw outer organic fluid / wavy loops
            // Replicates the wavy aesthetic of high-end voice assistants
            val waveAmplitude = if (zoyaState == ZoyaState.IDLE) {
                baseRadius * 0.05f // quiet breathe ripples
            } else {
                baseRadius * (0.08f + audioLevel * 0.24f) // responsive waves
            }

            val steps = 100
            val path1 = Path()
            val path2 = Path()

            for (step in 0..steps) {
                val angle = (step.toFloat() / steps) * 2 * PI.toFloat()

                // Wave 1 formula with 5 fluid ripples
                val ripple1 = sin(5 * angle - phase1) * cos(2 * angle + phase1 * 0.5f)
                val r1 = baseRadius + waveAmplitude * ripple1
                val px1 = center.x + cos(angle) * r1
                val py1 = center.y + sin(angle) * r1

                // Wave 2 counter-rotating formula with 4 fluid ripples
                val ripple2 = cos(4 * angle - phase2) * sin(3 * angle - phase2 * 0.4f)
                val r2 = baseRadius + waveAmplitude * ripple2 * 0.8f
                val px2 = center.x + cos(angle) * r2
                val py2 = center.y + sin(angle) * r2

                if (step == 0) {
                    path1.moveTo(px1, py1)
                    path2.moveTo(px2, py2)
                } else {
                    path1.lineTo(px1, py1)
                    path2.lineTo(px2, py2)
                }
            }
            path1.close()
            path2.close()

            // Render morphing outlines
            drawPath(
                path = path1,
                brush = Brush.sweepGradient(
                    colors = listOf(colorPrimary, colorSecondary, colorPrimary)
                ),
                style = Stroke(width = 2.5.dp.toPx())
            )

            drawPath(
                path = path2,
                color = colorPrimary.copy(alpha = 0.45f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

class SineTransitionSpec : AnimationSpec<Float> {
    val easing: Easing = FastOutSlowInEasing
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Float, V>
    ): VectorizedAnimationSpec<V> {
        return TweenSpec<Float>(2200, easing = easing).vectorize(converter)
    }
}
