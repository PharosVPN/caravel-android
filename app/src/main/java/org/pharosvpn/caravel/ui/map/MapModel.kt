// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.map

import org.pharosvpn.caravel.model.GeoCoord

enum class PinKind { Client, Node, Relay, Controller }

data class MapPin(
    val coord: GeoCoord,
    val label: String,
    val sub: String?,
    val active: Boolean,
    val kind: PinKind,
)

/** The data plane is dashed, the control plane is solid (DESIGN §3). */
enum class ArcStyle { DataPlane, ControlPlane }

data class MapArc(val points: List<GeoCoord>, val style: ArcStyle)
