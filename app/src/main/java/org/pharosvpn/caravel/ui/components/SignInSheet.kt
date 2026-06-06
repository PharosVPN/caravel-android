// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.ui.CaravelViewModel

private val Muted = Color(0xFF8A93A1)

/**
 * The sign-in (account sync) sheet — port of ContentView.syncSheetView. Shows the
 * picked `.pharosid` device file, an optional account email, and the account
 * passphrase. The passphrase is handed to the engine and stored in the Keystore;
 * the profile is decrypted on-device (the controller only stores ciphertext).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    vm: CaravelViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pending by vm.pendingDevice.collectAsState()
    val ui by vm.ui.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment ?: "device.pharosid"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) vm.stashDeviceFile(bytes, name)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sync from controller", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            if (pending != null) {
                Text(pending!!.name, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                OutlinedButton(onClick = { devicePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose your .pharosid device file")
                }
            }

            Text(
                "Sign in with your account passphrase. Your profile is decrypted on this device — the controller only stores ciphertext.",
                fontSize = 12.sp, color = Muted,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Account email (optional if in the bundle)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Account passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            ui.lastError?.let { Text(it, fontSize = 11.sp, color = Color(0xFFFF6B6B)) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val p = pending
                        if (p != null && password.isNotEmpty()) {
                            vm.syncFromController(p.bytes, p.name, email.trim(), password)
                            onDismiss()
                        }
                    },
                    enabled = pending != null && password.isNotEmpty() && !ui.syncing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (ui.syncing) "Syncing…" else "Sync") }
            }
        }
    }
}
