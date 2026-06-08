// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.model.ProfileInfo
import org.pharosvpn.caravel.ui.CaravelViewModel
import org.pharosvpn.caravel.ui.theme.Teal

private val Hairline = Color(0x14FFFFFF)
private val Muted = Color(0xFF8A93A1)
private val RowSelected = Color(0x1A4FD1C4)

@Composable
fun ProfileList(
    vm: CaravelViewModel,
    onPickBundle: () -> Unit,
    onSignIn: () -> Unit,
    onEnroll: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "profile.pharos"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) {
            onPickBundle()
            vm.importBundle(bytes, name)
        }
    }

    val devicePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "device.pharosid"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) {
            vm.stashDeviceFile(bytes, name)
            onSignIn()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PROFILES", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted)
            Spacer(Modifier.width(1.dp).weight(1f))
            IconButton(onClick = { importPicker.launch(arrayOf("*/*")) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add a .pharos file", tint = Teal)
            }
            IconButton(onClick = { devicePicker.launch(arrayOf("*/*")) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Get from controller", tint = Teal)
            }
            IconButton(onClick = onEnroll, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Link, contentDescription = "Enroll with a join link", tint = Teal)
            }
        }

        if (ui.profiles.isEmpty()) {
            Text(
                "No profiles yet. Add a .pharos file, or sign in to sync from your controller.",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (p in ui.profiles) {
                    ProfileRow(
                        p = p,
                        selected = p.id == ui.selectedId,
                        onClick = { vm.select(p.id) },
                        onToggleDisabled = { vm.setDisabled(p.bundle, !p.disabled) },
                        onDelete = { pendingDelete = p.bundle },
                    )
                }
            }
        }
    }

    pendingDelete?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “$bundle”?") },
            text = { Text("Removes this imported profile from this device. You can re-import it from its .pharos file.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(bundle); pendingDelete = null }) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileRow(
    p: ProfileInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) RowSelected else Color.Transparent)
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (p.cloudSynced) Icons.Filled.Cloud else Icons.Filled.Public,
                contentDescription = null,
                tint = Teal.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                p.name,
                color = if (p.disabled) Muted else Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (p.disabled) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            when {
                p.disabled -> Text("off", fontSize = 10.sp, color = Muted)
                p.protoBadge != null -> Badge(p.protoBadge!!)
                else -> Text(p.enc, fontSize = 10.sp, color = Muted)
            }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Text("⋮", color = Muted, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (p.cloudSynced) {
                DropdownMenuItem(
                    text = { Text(if (p.disabled) "Enable" else "Disable") },
                    leadingIcon = {
                        Icon(if (p.disabled) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    },
                    onClick = { onToggleDisabled(); menuOpen = false },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Delete…") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { onDelete(); menuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = Teal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Teal.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
