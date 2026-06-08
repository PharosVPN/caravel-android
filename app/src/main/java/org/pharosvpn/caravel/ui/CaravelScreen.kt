// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.pharosvpn.caravel.ui.components.ControllerCard
import org.pharosvpn.caravel.ui.components.DetailPanel
import org.pharosvpn.caravel.ui.components.ProfileList
import org.pharosvpn.caravel.ui.components.EnrollSheet
import org.pharosvpn.caravel.ui.components.SignInSheet
import org.pharosvpn.caravel.ui.components.TopBrandBar
import org.pharosvpn.caravel.ui.map.LandMap
import org.pharosvpn.caravel.ui.theme.Ocean
import org.pharosvpn.caravel.ui.theme.Panel
import org.pharosvpn.caravel.vpn.TunnelBus

/**
 * The app's single screen — the Android counterpart of caravel-mac's
 * ContentView. The map fills the top as the signature backdrop; a control panel
 * (brand, profiles, controller card, connect detail) sits below.
 */
@Composable
fun CaravelScreen(
    vm: CaravelViewModel,
    onDisconnect: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val tunnel by vm.tunnel.collectAsState()
    val pendingDevice by vm.pendingDevice.collectAsState()

    val connected = tunnel.status == TunnelBus.Status.Connected
    var showSignIn by remember { mutableStateOf(false) }
    var showEnroll by remember { mutableStateOf(false) }

    // A device file was opened/picked → open the sign-in sheet automatically.
    if (pendingDevice != null && !showSignIn) {
        showSignIn = true
    }

    Box(Modifier.fillMaxSize().background(Ocean)) {
        Column(Modifier.fillMaxSize()) {
            // The map — weighted to take the upper portion.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LandMap(
                    pins = vm.mapPins(),
                    arcs = vm.mapArcs(),
                    connected = connected,
                )
                TopBrandBar(
                    engineVersion = ui.engineVersion,
                    engineLoaded = ui.engineLoaded,
                    modifier = Modifier,
                )
            }

            // The control panel.
            Surface(
                color = Panel,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 460.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    ProfileList(
                        vm = vm,
                        onPickBundle = { showSignIn = false },
                        onSignIn = { showSignIn = true },
                        onEnroll = { showEnroll = true },
                    )

                    if (ui.controller != null || ui.loggedIn) {
                        ControllerCard(
                            vm = vm,
                            onNeedsLogin = { showSignIn = true },
                            onLogout = { onDisconnect(); vm.logout() },
                        )
                    }

                    DetailPanel(
                        vm = vm,
                        connected = connected,
                        tunnel = tunnel,
                        onConnect = { vm.requestConnect() },
                        onDisconnect = onDisconnect,
                    )
                }
            }
        }
    }

    if (showSignIn) {
        SignInSheet(
            vm = vm,
            onDismiss = {
                showSignIn = false
                vm.clearPendingDevice()
            },
        )
    }

    if (showEnroll) {
        EnrollSheet(
            vm = vm,
            onDismiss = { showEnroll = false },
        )
    }
}
