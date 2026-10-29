package com.gallerysorter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(200.dp))
        VSpace(8.dp)
        Text("Gallery Sorter", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Sort your photos faster.", fontSize = 16.sp, color = Muted)
        Spacer(Modifier.weight(1f))
        SpacedColumn(gap = 20.dp) {
            InfoRow(Icons.Default.Refresh, Color(0xFFF59E0B), Color(0xFFFEF3C7), "Swipe to keep or delete", "Clean up in seconds")
            InfoRow(Icons.Default.Delete, Red, RedSoft, "Free up storage space", "Remove what you don't need")
            InfoRow(Icons.Default.Lock, Green, GreenSoft, "Your privacy matters", "Everything happens on your device")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Get Started", onStart)
    }
}

@Composable
fun PermissionScreen(denied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(Icons.Default.Lock, Navy, Line, size = 88.dp)
        VSpace(24.dp)
        Text("Allow access to\nyour photos", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy, textAlign = TextAlign.Center)
        VSpace(12.dp)
        Text(
            "Gallery Sorter needs access to your photos to help you find and remove unwanted images. " +
                "Your photos stay on your device and never leave your phone.",
            fontSize = 15.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        VSpace(24.dp)
        CardRow {
            SpacedColumn(gap = 16.dp) {
                InfoRow(Icons.Default.Lock, Navy, Line, "100% private", "No photos are uploaded")
                InfoRow(Icons.Default.Check, Navy, Line, "On-device only", "All processing happens locally")
                InfoRow(Icons.Default.Refresh, Navy, Line, "You're in control", "You can revoke access anytime")
            }
        }
        if (denied) {
            VSpace(16.dp)
            Text(
                "Photo access was not granted. You can allow it from the app's settings.",
                fontSize = 14.sp,
                color = Red,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        if (denied) {
            PrimaryButton("Open Settings", onOpenSettings)
            VSpace(10.dp)
            SoftButton("Try Again", onAllow)
        } else {
            PrimaryButton("Allow Gallery Access", onAllow)
        }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel) {
    val albums = vm.albums
    val total = albums.sumOf { it.count }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gallery Sorter", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text("What would you like to sort?", fontSize = 15.sp, color = Muted)
                }
                IconButton(onClick = vm::openTrash) {
                    Icon(Icons.Default.Delete, contentDescription = "Recently Deleted", tint = Navy)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            CardRow(onClick = { if (total > 0) vm.startSession(null, "Entire Gallery") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cover = albums.firstOrNull()?.coverId
                    if (cover != null) {
                        Thumb(cover, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), px = 200)
                    } else {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Line))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Entire Gallery", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                        Text(
                            if (total > 0) "${plural(total, "photo")} on your device" else "No photos found",
                            fontSize = 13.sp,
                            color = Muted,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                }
            }
        }
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Albums", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 6.dp))
            }
            items(albums, key = { it.bucketId }) { album ->
                Column(Modifier.clickable { vm.startSession(album.bucketId, album.name) }) {
                    Thumb(
                        album.coverId,
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                        px = 480,
                    )
                    Text(
                        album.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Navy,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(plural(album.count, "photo"), fontSize = 12.sp, color = Muted)
                }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (vm.loadingAlbums) {
                        CircularProgressIndicator(color = Navy)
                    } else {
                        Text("No photos found on this device.", color = Muted, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
