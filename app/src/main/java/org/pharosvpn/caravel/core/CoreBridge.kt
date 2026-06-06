// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.core

import android.util.Log
import java.lang.reflect.Method

/**
 * CoreBridge is the single seam between the app and the gomobile-built Go engine
 * (`core.Core`, shipped as caravel.aar). It declares the FULL intended engine
 * surface and binds to whatever the .aar currently exports via reflection — so
 * the UI/service build and run before the complete engine lands, and drop in the
 * real one with zero call-site changes (NOTES.md tracks the gap).
 *
 * The intended surface (from the engine contract):
 *
 *   Core.initStore(dir)
 *   Core.importBundle(path) -> name
 *   Core.syncAndStore(pharosidBytes, email, pass) -> name   // login/sync; replace-all
 *   Core.listProfiles() -> JSON
 *   Core.controllerStatus(bundleName) -> JSON
 *   Core.reachable(pharosidBytes, timeoutMs) -> bool
 *   Core.logout() -> count
 *   Core.connect(bundleName, profileName, protoPref, tunFd) -> Session
 *   Session.stats() -> JSON ; Session.stop()
 *
 * Each method probes the static `core.Core` class for a matching binding and
 * throws [EngineUnavailable] when the running .aar predates it. Callers catch
 * that and surface a clear "engine not available yet" state rather than crashing.
 */
object CoreBridge {

    private const val TAG = "CoreBridge"

    /** Thrown when the loaded .aar does not (yet) export a method. */
    class EngineUnavailable(method: String) :
        Exception("the bundled engine does not provide \"$method\" yet — rebuild caravel.aar")

    private val coreClass: Class<*>? by lazy {
        runCatching { Class.forName("core.Core") }.getOrNull()
    }

    /** Whether the Go engine .aar is present at all (any binding). */
    val engineLoaded: Boolean get() = coreClass != null

    /** The engine version string if available (the stub already exports this). */
    fun version(): String? =
        runCatching { method("version")?.invoke(null) as? String }.getOrNull()

    private fun method(name: String, vararg params: Class<*>): Method? =
        coreClass?.let { c -> runCatching { c.getMethod(name, *params) }.getOrNull() }

    private fun require(name: String, vararg params: Class<*>): Method =
        method(name, *params) ?: throw EngineUnavailable(name)

    // ───────── intended engine surface (reflective) ─────────

    /** Point the engine's store at the app filesDir. No-op if absent. */
    fun initStore(dir: String) {
        method("initStore", String::class.java)?.let {
            runCatching { it.invoke(null, dir) }
                .onFailure { e -> Log.w(TAG, "initStore failed", e) }
        }
    }

    /** Import a `.pharos` at [path]; returns the stored base name. */
    @Throws(EngineUnavailable::class)
    fun importBundle(path: String): String =
        require("importBundle", String::class.java).invoke(null, path) as String

    /** Login/sync from the controller; replace-all. Returns the stored name. */
    @Throws(EngineUnavailable::class)
    fun syncAndStore(pharosId: ByteArray, email: String, password: String): String =
        require("syncAndStore", ByteArray::class.java, String::class.java, String::class.java)
            .invoke(null, pharosId, email, password) as String

    /** Engine's view of the profile list (JSON). */
    @Throws(EngineUnavailable::class)
    fun listProfiles(): String =
        require("listProfiles").invoke(null) as String

    /** Controller status for a bundle (JSON). */
    @Throws(EngineUnavailable::class)
    fun controllerStatus(bundleName: String): String =
        require("controllerStatus", String::class.java).invoke(null, bundleName) as String

    /** A short TLS dial to the relay — informational liveness. */
    @Throws(EngineUnavailable::class)
    fun reachable(pharosId: ByteArray, timeoutMs: Long): Boolean =
        require("reachable", ByteArray::class.java, Long::class.javaPrimitiveType!!)
            .invoke(null, pharosId, timeoutMs) as Boolean

    /** Remove all cloud profiles; returns the count removed. */
    @Throws(EngineUnavailable::class)
    fun logout(): Long {
        val r = require("logout").invoke(null)
        return (r as? Number)?.toLong() ?: 0L
    }

    /**
     * Bring up the tunnel over [tunFd] and return an opaque Session handle. The
     * Session is reflected too (stats()/stop()). protoPref is auto|amneziawg|xray.
     */
    @Throws(EngineUnavailable::class)
    fun connect(bundleName: String, profileName: String, protoPref: String, tunFd: Int): Session {
        val m = require(
            "connect",
            String::class.java, String::class.java, String::class.java,
            Int::class.javaPrimitiveType!!,
        )
        val handle = m.invoke(null, bundleName, profileName, protoPref, tunFd)
            ?: throw EngineUnavailable("connect")
        return Session(handle)
    }

    /** A live tunnel session — a reflective wrapper over the engine's Session. */
    class Session(private val handle: Any) {
        fun stats(): String? =
            runCatching { handle.javaClass.getMethod("stats").invoke(handle) as? String }.getOrNull()

        fun stop() {
            runCatching { handle.javaClass.getMethod("stop").invoke(handle) }
                .onFailure { Log.w(TAG, "session stop failed", it) }
        }
    }
}
