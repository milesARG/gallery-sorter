package com.gallerysorter.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ROTATION_DEG = 14f
private const val FLING_VELOCITY = 1200f

private fun cardRequest(context: android.content.Context, id: Long, w: Int, h: Int): ImageRequest =
    ImageRequest.Builder(context).data(photoUri(id)).size(Size(w, h)).build()

@Composable
fun SwipeScreen(vm: MainViewModel) {
    val ui = vm.ui
    val context = LocalContext.current
    var topProgress by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Navy)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.sessionTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
                Text(
                    "${fmt(minOf(ui.reviewed + 1, ui.total))} of ${fmt(ui.total)}",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            IconButton(onClick = vm::undo, enabled = ui.canUndo) {
                Icon(
                    painterResource(R.drawable.ic_undo),
                    contentDescription = "Undo",
                    tint = if (ui.canUndo) Navy else Navy.copy(alpha = 0.25f),
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (ui.total == 0) 0f else ui.reviewed.toFloat() / ui.total },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            color = Navy,
            trackColor = Line,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            val w = constraints.maxWidth
            val h = constraints.maxHeight

            // warm the cache for the cards that come next
            LaunchedEffect(ui.currentId, w, h) {
                ui.upNext.forEach { context.imageLoader.enqueue(cardRequest(context, it, w, h)) }
            }

            val stack = listOfNotNull(ui.upNext.firstOrNull(), ui.currentId)
            stack.forEach { id ->
                key(id) {
                    PhotoCard(
                        id = id,
                        isTop = id == ui.currentId,
                        widthPx = w,
                        heightPx = h,
                        underProgress = { topProgress },
                        onProgress = { topProgress = it },
                        onDecide = { keep -> vm.decide(id, keep) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    id: Long,
    isTop: Boolean,
    widthPx: Int,
    heightPx: Int,
    underProgress: () -> Float,
    onProgress: (Float) -> Unit,
    onDecide: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var decided by remember { mutableStateOf(false) }
    val threshold = widthPx * 0.3f
    val shape = RoundedCornerShape(28.dp)

    // A card that was flying out when Undo was tapped is promoted again: reset it.
    LaunchedEffect(isTop) {
        if (decided) {
            decided = false
            offset.snapTo(Offset.Zero)
        }
    }

    if (isTop) {
        LaunchedEffect(Unit) {
            snapshotFlow { offset.value.x }.collect { onProgress((abs(it) / threshold).coerceIn(0f, 1f)) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(isTop) {
                if (!isTop) return@pointerInput
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragCancel = {
                        if (!decided) scope.launch { offset.animateTo(Offset.Zero, spring()) }
                    },
                    onDragEnd = {
                        val vx = tracker.calculateVelocity().x
                        scope.launch {
                            if (decided) return@launch
                            val x = offset.value.x
                            val keep = when {
                                x > threshold || (vx > FLING_VELOCITY && x > 0f) -> true
                                x < -threshold || (vx < -FLING_VELOCITY && x < 0f) -> false
                                else -> null
                            }
                            if (keep == null) {
                                offset.animateTo(Offset.Zero, spring())
                            } else {
                                decided = true
                                val outX = if (keep) widthPx * 1.6f else -widthPx * 1.6f
                                offset.animateTo(Offset(outX, offset.value.y), tween(durationMillis = 200))
                                onDecide(keep)
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        if (decided) return@detectDragGestures
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offset.snapTo(offset.value + amount) }
                    },
                )
            }
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = offset.value.x / widthPx * ROTATION_DEG
                val s = if (isTop) 1f else 0.94f + 0.06f * underProgress()
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Soft)
            .border(BorderStroke(1.dp, Line), shape),
    ) {
        AsyncImage(
            model = remember(id, widthPx, heightPx) { cardRequest(context, id, widthPx, heightPx) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isTop) {
            SwipeOverlay(
                color = Red,
                label = "Marked for deletion",
                alpha = { (-offset.value.x / threshold).coerceIn(0f, 1f) },
                content = { Icon(Icons.Default.Delete, null, tint = Red, modifier = Modifier.size(36.dp)) },
            )
            SwipeOverlay(
                color = Green,
                label = "Kept",
                alpha = { (offset.value.x / threshold).coerceIn(0f, 1f) },
                content = { Icon(Icons.Default.Check, null, tint = Green, modifier = Modifier.size(40.dp)) },
            )
        }
    }
}

@Composable
private fun SwipeOverlay(
    color: Color,
    label: String,
    alpha: () -> Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(color.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) { content() }
            Text(
                label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
