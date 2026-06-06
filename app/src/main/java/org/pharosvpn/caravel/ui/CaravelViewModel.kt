// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.pharosvpn.caravel.core.CoreBridge
import org.pharosvpn.caravel.core.SecureStore
import org.pharosvpn.caravel.model.ControllerStatus
import org.pharosvpn.caravel.model.GeoCoord
import org.pharosvpn.caravel.model.ProfileInfo
import org.pharosvpn.caravel.model.ProfileStore
import org.pharosvpn.caravel.ui.map.ArcStyle
import org.pharosvpn.caravel.ui.map.MapArc
import org.pharosvpn.caravel.ui.map.MapPin
import org.pharosvpn.caravel.ui.map.PinKind
import org.pharosvpn.caravel.ui.map.greatCircle
import org.pharosvpn.caravel.vpn.TunnelBus
import java.util.TimeZone

/**
 * The app's view-model — the Android counterpart of caravel-mac's
 * TunnelController. Lists stored profiles, computes the map (You + nodes +
 * controller, dashed data-plane + solid control-plane arcs), and drives
 * connect/disconnect/sync/logout through the engine ([CoreBridge]) and the
 * [org.pharosvpn.caravel.vpn.CaravelVpnService].
 */
class CaravelViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProfileStore(app.filesDir)
    private val secure = SecureStore(app)

    data class UiState(
        val profiles: List<ProfileInfo> = emptyList(),
        val selectedId: String = "",
        val proto: String = "auto",
        val controller: ControllerStatus? = null,
        val loggedIn: Boolean = false,
        val syncing: Boolean = false,
        val lastError: String? = null,
        val engineLoaded: Boolean = false,
        val engineVersion: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** The live tunnel state (from the VpnService). */
    val tunnel: StateFlow<TunnelBus.State> = TunnelBus.state

    init {
        CoreBridge.initStore(app.filesDir.absolutePath)
        _ui.update {
            it.copy(
                engineLoaded = CoreBridge.engineLoaded,
                engineVersion = CoreBridge.version(),
                loggedIn = secure.hasCredential,
            )
        }
        reloadProfiles()
    }

    val selectedInfo: ProfileInfo? get() = _ui.value.profiles.firstOrNull { it.id == _ui.value.selectedId }

    /** The cloud-synced bundle to act on — the selected one if cloud, else the
     *  first cloud profile in the list. */
    val cloudInfo: ProfileInfo?
        get() = selectedInfo?.takeIf { it.cloudSynced } ?: _ui.value.profiles.firstOrNull { it.cloudSynced }

    fun reloadProfiles() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { store.list() }
        _ui.update { s ->
            val keep = if (list.any { it.id == s.selectedId }) s.selectedId else list.firstOrNull()?.id.orEmpty()
            s.copy(profiles = list, selectedId = keep, loggedIn = secure.hasCredential)
        }
        refreshController()
    }

    fun select(id: String) = _ui.update { it.copy(selectedId = id) }
    fun setProto(p: String) = _ui.update { it.copy(proto = p) }
    fun clearError() = _ui.update { it.copy(lastError = null) }

    // ───────── import ─────────

    /** Import a `.pharos` (already read into memory by the picker). */
    fun importBundle(bytes: ByteArray, fileName: String) = viewModelScope.launch {
        try {
            val name = withContext(Dispatchers.IO) { store.importBundle(bytes, fileName) }
            reloadProfiles()
            _ui.update { s ->
                val first = s.profiles.firstOrNull { it.bundle == name }
                s.copy(selectedId = first?.id ?: s.selectedId, lastError = null)
            }
        } catch (e: Throwable) {
            _ui.update { it.copy(lastError = "import failed: ${e.message}") }
        }
    }

    // ───────── device file (.pharosid) for the login sheet ─────────

    data class PendingDevice(val bytes: ByteArray, val name: String)

    private val _pendingDevice = MutableStateFlow<PendingDevice?>(null)
    val pendingDevice: StateFlow<PendingDevice?> = _pendingDevice.asStateFlow()

    /** Hold a picked/opened `.pharosid` so the login sheet can sync with it. */
    fun stashDeviceFile(bytes: ByteArray, name: String) {
        _pendingDevice.value = PendingDevice(bytes, name)
    }

    fun clearPendingDevice() { _pendingDevice.value = null }

    // ───────── sync (login + sync-now) ─────────

    /** Login/sync: store the `.pharosid`, fetch + decrypt the bundle (engine),
     *  replace-all, and persist the passphrase. */
    fun syncFromController(pharosIdBytes: ByteArray, pharosIdName: String, email: String, password: String) =
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true, lastError = null) }
            try {
                val name = withContext(Dispatchers.IO) {
                    // Persist the device file as a sidecar so re-sync needs no re-pick.
                    val base = pharosIdName.removeSuffix(".pharosid")
                    store.deviceFile(base).writeBytes(pharosIdBytes)
                    store.purgeCloud()
                    CoreBridge.syncAndStore(pharosIdBytes, email, password)
                }
                secure.storePassphrase(password)
                _ui.update { it.copy(syncing = false, loggedIn = true) }
                reloadProfiles()
                _ui.update { s ->
                    val first = s.profiles.firstOrNull { it.bundle == name }
                    s.copy(selectedId = first?.id ?: s.selectedId)
                }
            } catch (e: CoreBridge.EngineUnavailable) {
                _ui.update { it.copy(syncing = false, lastError = "Cloud sync needs the full engine — rebuild caravel.aar.") }
            } catch (e: Throwable) {
                _ui.update { it.copy(syncing = false, lastError = "sync failed: ${e.message}") }
            }
        }

    /** One-tap re-sync using the stored passphrase. Returns false if no
     *  passphrase is stored (the UI then opens the login sheet). */
    fun syncNow(onNeedsLogin: () -> Unit) {
        val info = cloudInfo ?: return
        val pass = secure.readPassphrase()
        if (pass == null) { onNeedsLogin(); return }
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true, lastError = null) }
            try {
                val bytes = withContext(Dispatchers.IO) { store.deviceFile(info.bundle).readBytes() }
                withContext(Dispatchers.IO) {
                    store.purgeCloud()
                    CoreBridge.syncAndStore(bytes, "", pass)
                }
                _ui.update { it.copy(syncing = false) }
                reloadProfiles()
            } catch (e: CoreBridge.EngineUnavailable) {
                _ui.update { it.copy(syncing = false, lastError = "Cloud sync needs the full engine — rebuild caravel.aar.") }
            } catch (e: Throwable) {
                _ui.update { it.copy(syncing = false, lastError = "sync failed: ${e.message}") }
            }
        }
    }

    fun logout() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching { CoreBridge.logout() } // engine purge if available
            store.purgeCloud()                  // local purge always (the contract)
        }
        secure.clearPassphrase()
        _ui.update { it.copy(controller = null, selectedId = "", loggedIn = false, lastError = null) }
        reloadProfiles()
    }

    // ───────── profile actions ─────────

    fun deleteProfile(bundle: String) = viewModelScope.launch {
        if (_ui.value.profiles.firstOrNull { it.bundle == bundle }?.cloudSynced == true) return@launch
        withContext(Dispatchers.IO) { store.delete(bundle) }
        reloadProfiles()
    }

    fun setDisabled(bundle: String, disabled: Boolean) = viewModelScope.launch {
        withContext(Dispatchers.IO) { store.setDisabled(bundle, disabled) }
        reloadProfiles()
    }

    // ───────── controller status ─────────

    fun refreshController() = viewModelScope.launch {
        val info = cloudInfo
        if (info == null) { _ui.update { it.copy(controller = null) }; return@launch }
        val status = withContext(Dispatchers.IO) {
            runCatching { parseControllerStatus(CoreBridge.controllerStatus(info.bundle)) }.getOrNull()
        }
        _ui.update { it.copy(controller = status) }
    }

    private fun parseControllerStatus(jsonText: String): ControllerStatus? {
        val o = Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonText) as? JsonObject ?: return null
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
        fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
        val ctl = (o["controller"] as? JsonObject)?.let {
            ControllerStatus.Endpoint(
                label = it.str("label") ?: "Controller",
                city = it.str("city"),
                lat = it.dbl("lat"),
                lon = it.dbl("lon"),
            )
        }
        return ControllerStatus(
            reachable = o.bool("reachable"),
            lastSyncedAt = o.str("last_synced_at"),
            relay = o.str("relay"),
            controller = ctl,
        )
    }

    // ───────── connect / disconnect (signals the Activity to drive the service) ─────────

    private val _connectRequest = MutableStateFlow<ConnectRequest?>(null)
    val connectRequest: StateFlow<ConnectRequest?> = _connectRequest.asStateFlow()

    data class ConnectRequest(val bundle: String, val profile: String, val proto: String)

    fun requestConnect() {
        val info = selectedInfo ?: run { _ui.update { it.copy(lastError = "no profile selected") }; return }
        if (info.disabled) return
        _connectRequest.value = ConnectRequest(info.bundle, info.profileName, _ui.value.proto)
    }

    fun consumeConnectRequest() { _connectRequest.value = null }

    // ───────── the map (port of TunnelController.mapPins / mapArcs) ─────────

    private val connected: Boolean get() = tunnel.value.status == TunnelBus.Status.Connected

    /** Offline "you": longitude from the timezone offset, no geolocation. */
    private val clientCoord: GeoCoord
        get() {
            val lon = TimeZone.getDefault().rawOffset / 3_600_000.0 * 15.0
            return GeoCoord(30.0, lon.coerceIn(-179.0, 179.0))
        }

    val controllerReachable: Boolean get() = _ui.value.controller?.reachable ?: false

    fun mapPins(): List<MapPin> {
        val info = selectedInfo ?: return emptyList()
        val nodes: List<MapPin> = info.path?.let { path ->
            path.hops.mapNotNull { h ->
                val c = h.coord ?: return@mapNotNull null
                MapPin(c, h.city ?: h.name, h.role.replaceFirstChar { it.uppercase() }, h.role == "exit", PinKind.Node)
            }
        } ?: info.nodes.mapNotNull { n ->
            val c = n.coord ?: return@mapNotNull null
            MapPin(c, n.city ?: n.name, n.activeIp, n.activeIp != null, PinKind.Node)
        }
        val ctlPins = info.control?.let {
            listOf(MapPin(it.coord, it.city ?: it.label, "Controller", controllerReachable, PinKind.Controller))
        } ?: emptyList()
        if (nodes.isEmpty() && ctlPins.isEmpty()) return emptyList()
        return listOf(MapPin(clientCoord, "You", null, connected, PinKind.Client)) + ctlPins + nodes
    }

    fun mapArcs(): List<MapArc> {
        val info = selectedInfo ?: return emptyList()
        val arcs = ArrayList<MapArc>()
        val coords = info.path?.hops?.mapNotNull { it.coord } ?: info.nodes.mapNotNull { it.coord }
        if (coords.isNotEmpty()) {
            val chain = listOf(clientCoord) + coords
            for (i in 0 until chain.size - 1) {
                arcs.add(MapArc(greatCircle(chain[i], chain[i + 1]), ArcStyle.DataPlane))
            }
        }
        info.control?.let { arcs.add(MapArc(greatCircle(clientCoord, it.coord), ArcStyle.ControlPlane)) }
        return arcs
    }
}
