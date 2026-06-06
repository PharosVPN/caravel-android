// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * TunnelBus is the in-process channel between [CaravelVpnService] (which owns the
 * tunnel) and the UI. The service runs in the same process, so a shared StateFlow
 * is enough — no IPC. The ViewModel collects [state].
 */
object TunnelBus {

    enum class Status { Disconnected, Connecting, Connected, Disconnecting, Failed }

    data class State(
        val status: Status = Status.Disconnected,
        val bundle: String? = null,
        val profile: String? = null,
        val proto: String? = null,
        val endpoint: String? = null,
        val rx: Long = 0,
        val tx: Long = 0,
        val liveProto: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun update(
        status: Status,
        bundle: String? = null,
        profile: String? = null,
        proto: String? = null,
        error: String? = null,
    ) {
        val prev = _state.value
        _state.value = if (status == Status.Connected) {
            prev.copy(status = status, bundle = bundle ?: prev.bundle, profile = profile ?: prev.profile, proto = proto ?: prev.proto, error = null)
        } else if (status == Status.Disconnected || status == Status.Failed) {
            State(status = status, error = error)
        } else {
            prev.copy(status = status, bundle = bundle ?: prev.bundle, profile = profile ?: prev.profile, proto = proto ?: prev.proto, error = error)
        }
    }

    /** Fold the engine's stats() JSON ({rx,tx,proto,endpoint}) into the state. */
    fun updateStats(statsJson: String) {
        val o = runCatching { json.parseToJsonElement(statsJson).jsonObject }.getOrNull() ?: return
        _state.value = _state.value.copy(
            rx = o["rx"]?.jsonPrimitive?.longOrNull ?: _state.value.rx,
            tx = o["tx"]?.jsonPrimitive?.longOrNull ?: _state.value.tx,
            liveProto = o["proto"]?.jsonPrimitive?.contentOrNull ?: _state.value.liveProto,
            endpoint = o["endpoint"]?.jsonPrimitive?.contentOrNull ?: _state.value.endpoint,
        )
    }
}
