// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The on-disk profile store: `<dir>/profiles/`, mirroring the cross-platform
 * file-store contract (docs/cloud-sync.md §2). A cloud profile is three files
 * sharing a base name: `<name>.pharos` (the bundle), `<name>.pharosid` (the
 * device identity / login credential), `<name>.synced` (the cloud marker). An
 * imported profile has only a `.pharos`. A `<name>.disabled` marker toggles a
 * cloud profile off (the only client action allowed on it).
 *
 * The store reimplements the conventions in Kotlin (per the contract); the
 * engine (.aar) owns sync/connect. Plaintext (`enc:none`) bundles are parsed
 * here so import + the map + the profile list work fully offline today.
 */
class ProfileStore(appFilesDir: File) {

    val dir: File = File(appFilesDir, "profiles").apply { mkdirs() }

    private val json = Json { ignoreUnknownKeys = true }

    fun bundleFile(name: String): File = File(dir, "$name.pharos")
    fun deviceFile(name: String): File = File(dir, "$name.pharosid")
    private fun marker(name: String, ext: String): File = File(dir, "$name.$ext")

    fun isCloudSynced(name: String): Boolean = marker(name, "synced").exists()
    fun isDisabled(name: String): Boolean = marker(name, "disabled").exists()

    /** The flattened, sorted list of every named profile across all bundles. */
    fun list(): List<ProfileInfo> =
        (dir.listFiles { f -> f.extension == "pharos" } ?: emptyArray())
            .flatMap { peek(it) }
            .sortedWith(compareBy({ it.bundle }, { it.name }))

    /** Remove a file-imported bundle and its disabled marker. Cloud bundles are
     *  protected (they'd re-sync) — disable them instead. */
    fun delete(name: String) {
        if (isCloudSynced(name)) return
        bundleFile(name).delete()
        marker(name, "disabled").delete()
    }

    fun setDisabled(name: String, disabled: Boolean) {
        val f = marker(name, "disabled")
        if (disabled) f.writeBytes(ByteArray(0)) else f.delete()
    }

    /** Copy an imported `.pharos` into the store (overwriting). Returns the base
     *  name. The engine validates the format on connect; we only need it on disk. */
    fun importBundle(bytes: ByteArray, fileName: String): String {
        val base = fileName.removeSuffix(".pharos")
        bundleFile(base).writeBytes(bytes)
        return base
    }

    /**
     * Sync is replace-all (docs/cloud-sync.md §5): delete every cloud-synced
     * bundle and its sidecars. Imported bundles are untouched. Called before
     * storing a freshly-fetched bundle, and by logout to purge.
     */
    fun purgeCloud() {
        val bundles = (dir.listFiles { f -> f.extension == "pharos" } ?: emptyArray())
            .map { it.nameWithoutExtension }
            .filter { isCloudSynced(it) }
        for (b in bundles) {
            bundleFile(b).delete()
            deviceFile(b).delete()
            marker(b, "synced").delete()
            marker(b, "disabled").delete()
        }
    }

    // ───────── parsing (the enc:none fast path) ─────────

    /** Expand one stored bundle into its named profiles. A plaintext bundle
     *  yields one ProfileInfo per named profile; an opaque/unreadable bundle a
     *  single placeholder whose details appear once the engine connects. */
    fun peek(file: File): List<ProfileInfo> {
        val bundle = file.nameWithoutExtension
        val synced = isCloudSynced(bundle)
        val off = isDisabled(bundle)
        fun opaque(enc: String) = listOf(
            ProfileInfo(bundle, "", enc, cloudSynced = synced, disabled = off),
        )

        val env = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
            ?: return opaque("?")
        if (env["fmt"]?.jsonPrimitive?.contentOrNull != "pharos-profile") return opaque("?")
        val enc = env["enc"]?.jsonPrimitive?.contentOrNull ?: "?"
        val payload = env["payload"] as? JsonObject
        val profs = payload?.get("profiles") as? JsonArray
        if (enc != "none" || payload == null || profs.isNullOrEmpty()) return opaque(enc)

        val control = parseControl(payload["control"] as? JsonObject)
        return profs.map { pe ->
            val pr = pe.jsonObject
            val nodesRaw = (pr["nodes"] as? JsonArray) ?: JsonArray(emptyList())
            val nodes = nodesRaw.map { ne ->
                val node = ne.jsonObject
                val ips = endpointIps(node)
                NodeInfo(
                    name = node["name"]?.jsonPrimitive?.contentOrNull ?: "node",
                    region = node["region"]?.jsonPrimitive?.contentOrNull,
                    ips = ips,
                    activeIp = ips.firstOrNull(),
                    proto = protoLabel(node),
                )
            }
            ProfileInfo(
                bundle = bundle,
                profileName = pr["name"]?.jsonPrimitive?.contentOrNull ?: "profile",
                enc = enc,
                proto = pr["protocol"]?.jsonPrimitive?.contentOrNull,
                nodes = nodes,
                path = parsePath(pr["path"] as? JsonObject),
                control = control,
                cloudSynced = synced,
                disabled = off,
            )
        }
    }

    private fun parseControl(c: JsonObject?): ControlInfo? {
        c ?: return null
        val lat = c["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val lon = c["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        if (lat == 0.0 && lon == 0.0) return null
        return ControlInfo(
            label = c["label"]?.jsonPrimitive?.contentOrNull ?: "Controller",
            city = c["city"]?.jsonPrimitive?.contentOrNull,
            lat = lat, lon = lon,
        )
    }

    private fun parsePath(p: JsonObject?): PathView? {
        p ?: return null
        val hopsj = (p["hops"] as? JsonArray)?.takeIf { it.isNotEmpty() } ?: return null
        val hops = hopsj.map { he ->
            val h = he.jsonObject
            PathHop(
                name = h["name"]?.jsonPrimitive?.contentOrNull ?: "node",
                region = h["region"]?.jsonPrimitive?.contentOrNull,
                role = h["role"]?.jsonPrimitive?.contentOrNull ?: "",
                ips = (h["ips"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            )
        }
        return PathView(p["name"]?.jsonPrimitive?.contentOrNull ?: "path", hops)
    }

    private fun protoLabel(node: JsonObject): String? {
        val protos = node["protocols"] as? JsonArray ?: return null
        val names = protos.mapNotNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull }.map {
            when (it) {
                "amneziawg" -> "AmneziaWG"
                "xray", "xray-reality" -> "XRay"
                else -> it
            }
        }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** A node's endpoint pool IPs (decision 17), falling back to its flat list. */
    private fun endpointIps(node: JsonObject): List<String> {
        (node["protocols"] as? JsonArray)?.forEach { pe ->
            val p = pe.jsonObject
            if (p["type"]?.jsonPrimitive?.contentOrNull == "amneziawg") {
                val eps = (p["params"] as? JsonObject)?.get("endpoints") as? JsonArray
                val ips = eps?.mapNotNull { it.jsonObject["ip"]?.jsonPrimitive?.contentOrNull }
                if (!ips.isNullOrEmpty()) return ips
            }
        }
        return (node["endpoints"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }
}
