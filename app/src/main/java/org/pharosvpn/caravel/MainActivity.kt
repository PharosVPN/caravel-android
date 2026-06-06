// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.pharosvpn.caravel.ui.CaravelViewModel
import org.pharosvpn.caravel.ui.CaravelScreen
import org.pharosvpn.caravel.ui.theme.CaravelTheme
import org.pharosvpn.caravel.vpn.CaravelVpnService

class MainActivity : ComponentActivity() {

    private val vm: CaravelViewModel by viewModels()

    // The pending connect request, held while the OS VPN-consent dialog is shown.
    private var pendingConnect: CaravelViewModel.ConnectRequest? = null

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val req = pendingConnect
        pendingConnect = null
        if (result.resultCode == Activity.RESULT_OK && req != null) {
            startVpn(req)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If launched by opening a .pharos / .pharosid, import it.
        handleViewIntent(intent)

        setContent {
            CaravelTheme {
                val context = LocalContext.current
                val connectReq by vm.connectRequest.collectAsState()

                // A connect request from the VM → ask for VPN consent, then start.
                LaunchedEffect(connectReq) {
                    val req = connectReq ?: return@LaunchedEffect
                    vm.consumeConnectRequest()
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        pendingConnect = req
                        vpnConsent.launch(prepare)
                    } else {
                        startVpn(req)
                    }
                }

                CaravelScreen(
                    vm = vm,
                    onDisconnect = { stopVpn() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh on foreground (the cloud-sync polling contract: no timer).
        vm.reloadProfiles()
        vm.refreshController()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        importFromUri(uri)
    }

    private fun importFromUri(uri: Uri) {
        val name = queryName(uri) ?: uri.lastPathSegment ?: "profile.pharos"
        val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return
        if (name.endsWith(".pharosid")) {
            // A device file by itself: stash it; the user signs in from the sheet.
            vm.stashDeviceFile(bytes, name)
        } else {
            vm.importBundle(bytes, name)
        }
    }

    private fun queryName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()

    private fun startVpn(req: CaravelViewModel.ConnectRequest) {
        val intent = CaravelVpnService.connectIntent(this, req.bundle, req.profile, req.proto)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVpn() {
        ContextCompat.startForegroundService(this, CaravelVpnService.disconnectIntent(this))
    }
}
