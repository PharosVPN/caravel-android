// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.model.NodeInfo
import org.pharosvpn.caravel.model.PathView
import org.pharosvpn.caravel.ui.CaravelViewModel
import org.pharosvpn.caravel.ui.theme.Teal
import org.pharosvpn.caravel.vpn.TunnelBus

private val Muted = Color(0xFF8A93A1)
private val Green = Color(0xFF49D17F)
private val Yellow = Color(0xFFE2C541)
private val CardBg = Color(0x0AFFFFFF)
private val Mono = FontFamily.Monospace

@Composable
fun DetailPanel(
    vm: CaravelViewModel,
    connected: Boolean,
    tunnel: TunnelBus.State,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val info = vm.selectedInfo
    val status = tunnel.status
    val busy = status == TunnelBus.Status.Connecting || status == TunnelBus.Status.Disconnecting || ui.syncing
    val disconnecting = status == TunnelBus.Status.Disconnecting

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        // status + label
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dotColor = when {
                connected -> Green
                busy -> Yellow
                status == TunnelBus.Status.Failed -> Color(0xFFFF6B6B)
                else -> Color.Gray
            }
            Spacer(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Text(statusLabel(status), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        // protocol: "both" → picker; else a label.
        if (info != null) {
            if (info.isBoth && !connected && status != TunnelBus.Status.Disconnecting) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val opts = listOf("auto" to "Auto", "amneziawg" to "AmneziaWG", "xray" to "XRay")
                    opts.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = ui.proto == value,
                            onClick = { vm.setProto(value) },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            enabled = !busy,
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            } else if (info.protoBadge != null) {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val xray = info.protoBadge == "XRay"
                    Icon(if (xray) Icons.Filled.VisibilityOff else Icons.Filled.Bolt, contentDescription = null, tint = Teal, modifier = Modifier.size(14.dp))
                    Text(
                        if (xray) "${info.protoBadge} · VLESS+REALITY (stealth)" else info.protoBadge!!,
                        fontSize = 12.sp, color = Muted,
                    )
                }
            }
        }

        // connect / disconnect
        val isRed = connected || disconnecting
        Button(
            onClick = { if (isRed) onDisconnect() else onConnect() },
            enabled = !busy && info != null && !info.disabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRed) Color(0xFFCC4B4B) else Teal,
                contentColor = if (isRed) Color.White else Color(0xFF06201D),
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(if (isRed) "Disconnect" else "Connect", fontWeight = FontWeight.SemiBold)
        }

        // live stats
        if (connected) {
            tunnel.endpoint?.let {
                Text(it, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            protoLabel(tunnel.liveProto ?: tunnel.proto)?.let { proto ->
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (proto.startsWith("XRay")) Icons.Filled.VisibilityOff else Icons.Filled.Bolt, contentDescription = null, tint = Teal, modifier = Modifier.size(13.dp))
                    Text("via $proto", fontSize = 12.sp, color = Teal)
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Green, modifier = Modifier.size(13.dp))
                    Text(humanBytes(tunnel.rx), fontSize = 12.sp, fontFamily = Mono, color = Green)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Teal, modifier = Modifier.size(13.dp))
                    Text(humanBytes(tunnel.tx), fontSize = 12.sp, fontFamily = Mono, color = Teal)
                }
            }
        }

        // egress path (entry → [mid] → exit)
        info?.path?.let { RouteCard(it) }

        // nodes
        if (info != null) {
            if (info.nodes.isEmpty() && info.path == null) {
                Text(
                    if (info.readable) "no nodes in this profile" else "encrypted profile — details appear once connected",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp),
                )
            } else if (info.nodes.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (n in info.nodes) NodeCard(n)
                }
            }
        }

        tunnel.error?.let {
            Text(it, fontSize = 11.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 6.dp))
        }
        ui.lastError?.let {
            Text(it, fontSize = 11.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RouteCard(path: PathView) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Teal.copy(alpha = 0.07f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Egress path · ${path.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        path.hops.forEachIndexed { i, h ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.size(7.dp).clip(CircleShape).background(if (h.role == "exit") Green else Teal))
                Text(h.city ?: h.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(h.role, fontSize = 10.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                h.ips.firstOrNull()?.let { Text(it, fontSize = 10.sp, fontFamily = Mono, color = Muted) }
            }
            if (i < path.hops.size - 1) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Muted, modifier = Modifier.size(10.dp).padding(start = 2.dp))
            }
        }
    }
}

@Composable
private fun NodeCard(node: NodeInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Dns, contentDescription = null, tint = Teal, modifier = Modifier.size(15.dp))
            Text(node.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            node.city?.let { Text("· $it", fontSize = 12.sp, color = Muted) }
            Spacer(Modifier.weight(1f))
            node.proto?.let {
                Text(
                    it, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Teal,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Teal.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        for (ip in node.ips) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val active = ip == node.activeIp
                Spacer(Modifier.size(6.dp).clip(CircleShape).background(if (active) Teal else Color.Gray.copy(alpha = 0.5f)))
                Text(ip, fontSize = 12.sp, fontFamily = Mono, color = if (active) Color.White else Muted)
                if (active) Text("active", fontSize = 10.sp, color = Teal)
            }
        }
    }
}

private fun statusLabel(s: TunnelBus.Status): String = when (s) {
    TunnelBus.Status.Disconnected -> "Disconnected"
    TunnelBus.Status.Connecting -> "Connecting…"
    TunnelBus.Status.Connected -> "Connected"
    TunnelBus.Status.Disconnecting -> "Disconnecting…"
    TunnelBus.Status.Failed -> "Disconnected"
}

private fun protoLabel(proto: String?): String? = when (proto) {
    "amneziawg" -> "AmneziaWG"
    "xray-reality", "xray" -> "XRay/REALITY"
    else -> null
}

/** Format a byte count compactly (e.g. "1.2 MB"). */
fun humanBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var x = n.toDouble(); var i = 0
    do { x /= 1024.0; i++ } while (x >= 1024.0 && i < units.size)
    return String.format("%.1f %s", x, units[i - 1])
}
