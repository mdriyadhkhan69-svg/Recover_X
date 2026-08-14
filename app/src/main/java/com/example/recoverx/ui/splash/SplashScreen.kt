package com.example.recoverx.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val SplashBackground = Color(0xFF0A0B14)
private val GlowPrimary = Color(0xFF7B61FF)
private val GlowSecondary = Color(0xFF5B8DEF)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splashMorph")
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        delay(1300)
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize().background(SplashBackground)) {
        drawMorphingBlob(phase = morphPhase, scale = pulse)
    }
}

private fun DrawScope.drawMorphingBlob(phase: Float, scale: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = size.minDimension * 0.16f * scale
    val points = 8
    val path = Path()

    for (i in 0..points) {
        val angle = (2 * Math.PI * i / points).toFloat()
        val wobble = sin(angle * 3 + phase) * 0.18f + cos(angle * 2 - phase) * 0.12f
        val r = baseRadius * (1f + wobble)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GlowPrimary.copy(alpha = 0.35f), Color.Transparent),
            center = center,
            radius = baseRadius * 3.2f
        ),
        radius = baseRadius * 3.2f,
        center = center
    )

    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(GlowPrimary, GlowSecondary),
            start = Offset(center.x - baseRadius, center.y - baseRadius),
            end = Offset(center.x + baseRadius, center.y + baseRadius)
        )
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
            center = center,
            radius = baseRadius * 0.6f
        ),
        radius = baseRadius * 0.6f,
        center = center
    )
}