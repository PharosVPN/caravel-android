// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.ui.CaravelViewModel

private val MutedEnroll = Color(0xFF8A93A1)

/**
 * The enrollment sheet — redeem a `pharosvpn://enroll` join link. Paste the link
 * (or scan its QR elsewhere and copy it) and optionally name the device. No
 * passphrase: the engine generates this device's key on-device and the controller
 * seals the profile to it. Mirrors SignInSheet (the account-sync flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollSheet(
    vm: CaravelViewModel,
    onDismiss: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var link by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    val valid = link.trim().startsWith("pharosvpn://enroll")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Enroll a device", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Text(
                "Paste the join link from your admin (or scan its QR and copy the link). No passphrase — your device key is generated here and your profile is sealed to it.",
                fontSize = 12.sp, color = MutedEnroll,
            )

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("pharosvpn://enroll?…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ui.lastError?.let { Text(it, fontSize = 11.sp, color = Color(0xFFFF6B6B)) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        if (valid) {
                            vm.enrollFromLink(link.trim(), deviceName.trim())
                            onDismiss()
                        }
                    },
                    enabled = valid && !ui.syncing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (ui.syncing) "Enrolling…" else "Enroll") }
            }
        }
    }
}
