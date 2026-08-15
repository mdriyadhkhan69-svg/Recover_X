package com.example.recoverx.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Invisible touch zone on the right edge. Tapping/pressing it reveals the thumb, dragging moves
 * the list, releasing fades it back out. The touch zone itself always occupies the width so the
 * gesture area is stable, but nothing is drawn until the user actually touches it.
 */
@Composable
fun FastScrollbar(
    totalItems: Int,
    firstVisibleIndex: Int,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalItems <= 1) return

    var trackHeightPx by remember { mutableStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val thumbHeightDp = 44.dp

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "scrollbarAlpha"
    )

    LaunchedEffect(isTouching) {
        if (!isTouching && visible) {
            delay(500)
            visible = false
        }
    }

    fun updateFromY(y: Float) {
        val ratio = (y / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val targetIndex = (ratio * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
        onScrollToIndex(targetIndex)
    }

    val progress = (firstVisibleIndex.toFloat() / (totalItems - 1)).coerceIn(0f, 1f)

    // Narrow hit strip at the very edge only — content is padded to leave this clear (see
    // ResultsScreen), so this box never sits on top of a tappable grid/list item. requireUnconsumed
    // = true also means if something below somehow already consumed the down event, we back off.
    Box(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(totalItems, trackHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    isTouching = true
                    visible = true
                    updateFromY(down.position.y)
                    drag(down.id) { change ->
                        change.consume()
                        updateFromY(change.position.y)
                    }
                    isTouching = false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .alpha(alpha)
                .offset {
                    val thumbPx = thumbHeightDp.toPx()
                    val y = (progress * (trackHeightPx - thumbPx)).coerceAtLeast(0f)
                    IntOffset(0, y.roundToInt())
                }
                .width(5.dp)
                .height(thumbHeightDp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}