package com.gallerysorter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReviewScreen(vm: MainViewModel) {
    val ui = vm.ui
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Review your changes", "Here's what you've sorted so far.", onBack = vm::back)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryRow(
                Icons.Default.Check, Green, GreenSoft, "Kept", plural(ui.kept, "photo"), ui.keptPreview, null,
            )
            SummaryRow(
                Icons.Default.Delete, Red, RedSoft, "Marked for deletion", plural(ui.marked, "photo"), ui.markedPreview,
                if (ui.marked > 0) vm::openMarked else null,
            )
            if (ui.remaining > 0) {
                SummaryRow(
                    Icons.Default.Refresh, Muted, Line, "Not reviewed yet", plural(ui.remaining, "photo"), emptyList(), null,
                )
            }
            Text(
                if (ui.marked > 0) {
                    "Marked photos go to Recently Deleted, where you can restore them before Android removes them for good."
                } else {
                    "Nothing is marked for deletion."
                },
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.marked > 0) {
                PrimaryButton("Move ${fmt(ui.marked)} to Recently Deleted", vm::requestTrash, color = Red)
            } else {
                PrimaryButton("Finish", vm::finish)
            }
            if (ui.remaining > 0) SoftButton("Keep sorting", vm::keepSorting)
            if (ui.marked > 0) {
                TextButton(onClick = vm::discardSession, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Leave without deleting", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    title: String,
    count: String,
    previews: List<Long>,
    onClick: (() -> Unit)?,
) {
    CardRow(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, background)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                Text(count, fontSize = 13.sp, color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                previews.take(1).forEach {
                    Thumb(
                        it,
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp)),
                        px = 120,
                    )
                }
            }
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
            }
        }
    }
}

@Composable
fun MarkedScreen(vm: MainViewModel) {
    val ids = vm.markedIds
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(
            "Marked for deletion",
            if (ids.isEmpty()) "No photos marked" else "${plural(ids.size, "photo")} · tap a photo to keep it",
            onBack = vm::back,
        )
        if (ids.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing marked for deletion.", color = Muted, fontSize = 15.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ids, key = { it }) { id ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.keepPhoto(id) }) {
                        Thumb(id, Modifier.fillMaxSize(), px = 300)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Red),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashScreen(vm: MainViewModel) {
    val items = vm.trashItems
    val selected = vm.selected
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(
            "Recently Deleted",
            "Erased after about 30 days.",
            onBack = vm::back,
            trailing = {
                if (items.isNotEmpty()) {
                    TextButton(onClick = vm::selectAll) {
                        Text(if (selected.size == items.size) "Clear" else "Select all", color = Navy)
                    }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                if (!vm.busy) Text("Nothing in Recently Deleted.", color = Muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.toggleSelected(item.id) }) {
                            Thumb(item.id, Modifier.fillMaxSize(), px = 300)
                            Text(
                                if (item.daysLeft == 1) "1 day left" else "${item.daysLeft} days left",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Navy else Color.White.copy(alpha = 0.6f))
                                    .border(1.5.dp, if (isSelected) Navy else Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        if (items.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    if (selected.isEmpty()) "Select photos to restore or delete" else "${fmt(selected.size)} selected",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftButton("Restore", vm::requestRestore, Modifier.weight(1f), enabled = selected.isNotEmpty())
                    PrimaryButton(
                        "Delete",
                        { confirm = true },
                        Modifier.weight(1f),
                        color = Red,
                        enabled = selected.isNotEmpty(),
                    )
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color.White,
            title = { Text("Delete permanently?", fontWeight = FontWeight.Bold, color = Navy) },
            text = {
                Text(
                    "${plural(selected.size, "photo")} will be permanently removed from your device storage and cannot be restored.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.requestDelete()
                }) { Text("Delete Permanently", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Navy) } },
        )
    }
}

@Composable
fun DoneScreen(vm: MainViewModel) {
    val ui = vm.ui
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(Icons.Default.Check, Color.White, Green, size = 96.dp)
        VSpace(20.dp)
        Text("All done!", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(
            "You've finished sorting ${vm.sessionTitle}.",
            fontSize = 15.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        VSpace(28.dp)
        CardRow {
            SpacedColumn(gap = 18.dp) {
                StatRow("Total reviewed", plural(ui.reviewed, "photo"), Icons.Default.Refresh, Indigo, IndigoSoft)
                StatRow("Kept", plural(ui.kept, "photo"), Icons.Default.Check, Green, GreenSoft)
                StatRow("Moved to Recently Deleted", plural(ui.trashed, "photo"), Icons.Default.Delete, Red, RedSoft)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Sort Another Album", vm::sortAnother)
        if (ui.trashed > 0) {
            TextButton(onClick = vm::openTrash) { Text("Open Recently Deleted", color = Navy, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, background, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Navy)
            Text(value, fontSize = 13.sp, color = Muted)
        }
    }
}
