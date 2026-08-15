package com.example.recoverx.ui.splash

import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val SplashBackground = Color(0xFF17181F)
private val BallColor = Color(0xFFC94F4F)
private val BarColor = Color(0xFF9A9CB0)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200) // no artificial multi-second delay
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BouncingBall()
            Spacer(modifier = Modifier.height(18.dp))
            MovingBars()
        }
    }
}

@Composable
private fun BouncingBall() {
    val transition = rememberInfiniteTransition(label = "ballBounce")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    // bounce: 0 = up position, 1 = down position (near the bars)
    val offsetY = (1f - kotlin.math.sin(bounce * Math.PI).toFloat()) * 0f // unused, kept simple below
    val translateY = (bounce * 22).dp

    Box(
        modifier = Modifier
            .height(30.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = translateY)
                .size(14.dp)
                .clip(CircleShape)
                .background(BallColor)
        )
    }
}

@Composable
private fun MovingBars() {
    val transition = rememberInfiniteTransition(label = "bars")
    val bars = listOf(0, 150, 300) // staggered start delays per bar

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { index, delayMs ->
            val heightFraction by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550, delayMillis = delayMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((14 * heightFraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BarColor)
            )
            if (index != bars.lastIndex) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}