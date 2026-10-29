package com.gallerysorter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.NumberFormat

val Navy = Color(0xFF111C3A)
val NavyButton = Color(0xFF1C2842)
val Muted = Color(0xFF6B7488)
val Soft = Color(0xFFF3F5F9)
val Line = Color(0xFFE6EAF1)
val Red = Color(0xFFE5484D)
val RedSoft = Color(0xFFFDEBEC)
val Green = Color(0xFF22A861)
val GreenSoft = Color(0xFFE5F6EC)
val Indigo = Color(0xFF4F5BD5)
val IndigoSoft = Color(0xFFEBEDFC)

fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

fun plural(n: Int, word: String): String = "${fmt(n)} $word" + if (n == 1) "" else "s"

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            background = Color.White,
            surface = Color.White,
            onSurface = Navy,
            error = Red,
        ),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NavyButton,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = Line,
            disabledContentColor = Muted,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Soft,
            contentColor = Navy,
            disabledContainerColor = Soft,
            disabledContentColor = Muted,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Navy)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Muted, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small square preview; Coil decodes it at [px] so full-resolution bitmaps are never held. */
@Composable
fun Thumb(id: Long, modifier: Modifier = Modifier, px: Int = 360) {
    val context = LocalContext.current
    val request = remember(id, px) {
        ImageRequest.Builder(context).data(photoUri(id)).size(px).crossfade(true).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Soft),
    )
}

@Composable
fun InfoRow(icon: ImageVector, tint: Color, background: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, background, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Navy)
            Text(body, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
fun CardRow(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color.White)
    Box(
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(Soft.copy(alpha = 0.6f))
            .padding(16.dp),
    ) { content() }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun SpacedColumn(modifier: Modifier = Modifier, gap: Dp = 12.dp, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { content() }
}
