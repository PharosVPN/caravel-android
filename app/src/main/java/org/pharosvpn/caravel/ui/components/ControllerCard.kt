// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.ui.CaravelViewModel
import org.pharosvpn.caravel.ui.theme.Teal
import java.time.Duration
import java.time.Instant

private val Muted = Color(0xFF8A93A1)
private val CardBg = Color(0x0AFFFFFF)

/**
 * The controller card — reachability dot (informational), "Last synced … · via
 * relay", and the Sync-now / Log-out actions. Port of ContentView.controllerCard.
 */
@Composable
fun ControllerCard(
    vm: CaravelViewModel,
    onNeedsLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val c = ui.controller
    val reachable = c?.reachable ?: false
    var showLogout by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null, tint = Teal, modifier = Modifier.size(15.dp))
            Text("Controller", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Dot(if (reachable) Color(0xFF49D17F) else Color.Gray)
            Text(if (reachable) "reachable" else "offline", fontSize = 11.sp, color = Muted)
        }

        val ago = lastSyncedAgo(c?.lastSyncedAt)
        if (ago != null) {
            val via = c?.relay?.let { " · via $it" } ?: ""
            Text("Last synced $ago$via", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("Not synced yet", fontSize = 11.sp, color = Muted)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { vm.syncNow(onNeedsLogin) },
                enabled = !ui.syncing,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (ui.syncing) "Syncing…" else "Sync now", color = Teal, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showLogout = true }) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Log out", color = Muted, fontSize = 13.sp)
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Log out of this controller?") },
            text = { Text("Removes all cloud-synced profiles from this device and forgets your passphrase. Imported profiles stay — you can sync again anytime.") },
            confirmButton = {
                TextButton(onClick = { showLogout = false; onLogout() }) { Text("Log out", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Spacer(Modifier.size(6.dp).clip(CircleShape).background(color))
}

/** Render an ISO-8601 timestamp compactly (e.g. "3m ago"). */
private fun lastSyncedAgo(iso: String?): String? {
    iso ?: return null
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val d = Duration.between(t, Instant.now()).seconds
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60}m ago"
        d < 86_400 -> "${d / 3600}h ago"
        else -> "${d / 86_400}d ago"
    }
}
