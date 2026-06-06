// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.model

/**
 * One node in a profile: its region (→ an offline map coordinate) and its
 * endpoint IP pool, with the one the client dials marked active.
 */
data class NodeInfo(
    val name: String,
    val region: String?,
    val ips: List<String>,
    val activeIp: String?,
    val proto: String?,
) {
    val coord: GeoCoord? get() = Regions.locate(region)?.coord
    val city: String? get() = Regions.locate(region)?.city
}

/** One node in a device's egress chain (entry → [mid] → exit). */
data class PathHop(
    val name: String,
    val region: String?,
    val role: String, // "entry", "mid", or "exit"
    val ips: List<String>,
) {
    val coord: GeoCoord? get() = Regions.locate(region)?.coord
    val city: String? get() = Regions.locate(region)?.city
}

/** The ordered egress chain a cascade profile carries. */
data class PathView(val name: String, val hops: List<PathHop>)

/**
 * The bundle's control-plane endpoint (the controller, via its relay) for the
 * map — coordinates embedded by coxswain so it places offline.
 */
data class ControlInfo(
    val label: String,
    val city: String?,
    val lat: Double,
    val lon: Double,
) {
    val coord: GeoCoord get() = GeoCoord(lat, lon)
}

/**
 * One named profile the UI can connect with — the rendered form of one entry in
 * a bundle's profiles[]. A `.pharos` bundle holds several; the list flattens
 * them so each is independently selectable. [bundle] is the store file;
 * [profileName] is the entry within it. For plaintext (`none`) we read the nodes
 * for the map + IP list; for password/account we only know the bundle + mode
 * until the engine connects.
 */
data class ProfileInfo(
    val bundle: String,
    val profileName: String,
    val enc: String,
    val proto: String? = null,
    val nodes: List<NodeInfo> = emptyList(),
    val path: PathView? = null,
    val control: ControlInfo? = null,
    val cloudSynced: Boolean = false,
    val disabled: Boolean = false,
) {
    val id: String get() = "$bundle/$profileName"
    val name: String get() = profileName.ifEmpty { bundle }
    val readable: Boolean get() = enc == "none"

    /** The profile offers both protocols; the client picks at connect. */
    val isBoth: Boolean get() = proto == "both"

    /** The short protocol label for the row/detail, or null. */
    val protoBadge: String?
        get() = when (proto) {
            "amneziawg" -> "AmneziaWG"
            "xray-reality", "xray" -> "XRay"
            "both" -> "Both"
            null, "" -> null
            else -> proto
        }
}

/** The cloud session's liveness (mirrors `core.controllerStatus`). */
data class ControllerStatus(
    val reachable: Boolean,
    val lastSyncedAt: String?,
    val relay: String?,
    val controller: Endpoint?,
) {
    data class Endpoint(
        val label: String,
        val city: String?,
        val lat: Double,
        val lon: Double,
    )
}
