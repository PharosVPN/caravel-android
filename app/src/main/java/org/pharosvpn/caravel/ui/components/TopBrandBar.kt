// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import org.pharosvpn.caravel.ui.theme.Teal

private val Glass = Color(0xB3070A11)

/** The brand chip floating over the top-left of the map. */
@Composable
fun TopBrandBar(
    engineVersion: String?,
    engineLoaded: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .statusBarsPadding()
            .padding(14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Glass)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
        Text("PharosVPN", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        engineVersion?.let {
            Text("· $it", color = Color(0xFF8A93A1), fontSize = 11.sp)
        }
    }
}
