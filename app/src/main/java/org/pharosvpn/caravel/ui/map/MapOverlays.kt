// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.ui.theme.Control
import org.pharosvpn.caravel.ui.theme.Teal

private val PanelGlass = Color(0xCC0E141E)
private val Hairline = Color(0x14FFFFFF)

@Composable
fun Legend(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PanelGlass)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("LEGEND", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFF8A93A1))
        LegendRow(line = true, dashed = true, color = Teal, title = "Data path", sub = "to the exit")
        LegendRow(line = true, dashed = false, color = Control, title = "Control path", sub = "controller ↔ relays")
        LegendRow(line = false, dashed = false, color = Teal, title = "Node", sub = "active = filled")
        LegendRow(line = false, dashed = false, color = Color.Gray, title = "You", sub = "approx (offline)")
    }
}

@Composable
private fun LegendRow(line: Boolean, dashed: Boolean, color: Color, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
            if (line) {
                drawLine(
                    color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) else null,
                )
            } else {
                drawCircle(color, radius = 4f, center = Offset(size.width / 2, size.height / 2))
            }
        }
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(sub, fontSize = 9.sp, color = Color(0xFF8A93A1))
        }
    }
}

@Composable
fun ZoomControls(
    modifier: Modifier = Modifier,
    onIn: () -> Unit,
    onOut: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PanelGlass)
            .border(1.dp, Hairline, RoundedCornerShape(9.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onIn, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in", tint = Color.White)
        }
        Divider(Modifier.width(20.dp), color = Hairline)
        IconButton(onClick = onOut, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out", tint = Color.White)
        }
        Divider(Modifier.width(20.dp), color = Hairline)
        IconButton(onClick = onReset, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Reset view", tint = Color.White)
        }
    }
}
