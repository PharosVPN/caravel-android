// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.pharosvpn.caravel.MainActivity
import org.pharosvpn.caravel.R
import org.pharosvpn.caravel.core.CoreBridge
import org.pharosvpn.caravel.model.ProfileInfo
import org.pharosvpn.caravel.model.ProfileStore

/**
 * CaravelVpnService owns the Android TUN and runs the Go engine over it. On
 * [ACTION_CONNECT] it builds the TUN with [VpnService.Builder], passes the raw fd
 * to [CoreBridge.connect], and runs as a foreground service while up. State is
 * broadcast to the UI via the in-process [TunnelBus].
 *
 * The engine drives the actual AmneziaWG/XRay over the fd. Until the .aar exports
 * `connect`, the service reports a clear error to the bus rather than faking a
 * tunnel — the device plane is the engine's job (do NOT modify caravel/go).
 */
class CaravelVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var session: CoreBridge.Session? = null
    private var statsJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                teardown(TunnelBus.Status.Disconnected)
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val bundle = intent.getStringExtra(EXTRA_BUNDLE).orEmpty()
                val profile = intent.getStringExtra(EXTRA_PROFILE).orEmpty()
                val proto = intent.getStringExtra(EXTRA_PROTO) ?: "auto"
                startTunnel(bundle, profile, proto)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(bundle: String, profileName: String, proto: String) {
        TunnelBus.update(TunnelBus.Status.Connecting)
        startForeground(NOTIF_ID, buildNotification("Connecting…"))

        scope.launch {
            try {
                val store = ProfileStore(filesDir)
                val info = store.list().firstOrNull { it.bundle == bundle && it.profileName == profileName }
                    ?: store.list().firstOrNull { it.bundle == bundle }

                val pfd = buildTun(info)
                tun = pfd

                // Hand the fd to the Go engine. It runs the userspace AmneziaWG /
                // XRay over the TUN. CoreBridge throws EngineUnavailable if the
                // loaded .aar predates connect().
                val s = CoreBridge.connect(bundle, profileName, proto, pfd.fd)
                session = s

                TunnelBus.update(TunnelBus.Status.Connected, bundle = bundle, profile = profileName, proto = proto)
                updateNotification("Connected")
                pollStats(s)
            } catch (e: CoreBridge.EngineUnavailable) {
                Log.w(TAG, "engine unavailable", e)
                teardown(TunnelBus.Status.Failed, "Engine not available yet — rebuild caravel.aar with the full core surface.")
            } catch (e: Throwable) {
                Log.e(TAG, "connect failed", e)
                teardown(TunnelBus.Status.Failed, e.message ?: "connect failed")
            }
        }
    }

    /** Build the TUN from the selected profile (sane defaults; full route). */
    private fun buildTun(info: ProfileInfo?): ParcelFileDescriptor {
        val b = Builder()
            .setSession("PharosVPN")
            .setMtu(1420)
            .addAddress("10.86.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9")
            .setBlocking(true)
        // Keep our own traffic out of the tunnel so the engine's outer packets to
        // the node reach the network (otherwise they'd route into the TUN).
        runCatching { b.addDisallowedApplication(packageName) }
        val configureIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        b.setConfigureIntent(configureIntent)
        return b.establish() ?: error("could not establish the TUN (permission revoked?)")
    }

    private fun pollStats(s: CoreBridge.Session) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                s.stats()?.let { TunnelBus.updateStats(it) }
                delay(2000)
            }
        }
    }

    private fun teardown(status: TunnelBus.Status, error: String? = null) {
        statsJob?.cancel(); statsJob = null
        runCatching { session?.stop() }; session = null
        runCatching { tun?.close() }; tun = null
        TunnelBus.update(status, error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        teardown(TunnelBus.Status.Disconnected)
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ───────── notification ─────────

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, CaravelVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PharosVPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", disconnect).build())
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.vpn_channel_desc) }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val TAG = "CaravelVpn"
        private const val CHANNEL_ID = "pharos_vpn"
        private const val NOTIF_ID = 0x1505

        const val ACTION_CONNECT = "org.pharosvpn.caravel.CONNECT"
        const val ACTION_DISCONNECT = "org.pharosvpn.caravel.DISCONNECT"
        const val EXTRA_BUNDLE = "bundle"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_PROTO = "proto"

        fun connectIntent(ctx: Context, bundle: String, profile: String, proto: String) =
            Intent(ctx, CaravelVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_BUNDLE, bundle)
                putExtra(EXTRA_PROFILE, profile)
                putExtra(EXTRA_PROTO, proto)
            }

        fun disconnectIntent(ctx: Context) =
            Intent(ctx, CaravelVpnService::class.java).setAction(ACTION_DISCONNECT)
    }
}
