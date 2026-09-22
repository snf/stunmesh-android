package dev.stunmesh.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import dev.stunmesh.android.MainActivity
import dev.stunmesh.android.R
import dev.stunmesh.android.backend.GoBackend
import dev.stunmesh.android.backend.SocketProtector
import dev.stunmesh.android.backend.TunProvider
import dev.stunmesh.android.config.ConfigPolicy
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.TunnelConfig
import dev.stunmesh.android.tunnel.TunnelManager
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * One serialized owner, one underlay callback, one Go discovery scheduler. A network change rebinds
 * protected outer sockets, never rebuilds the TUN.
 */
class StunmeshVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val backend = GoBackend()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val networks = mutableMapOf<Network, LinkProperties>()
    private val selected = AtomicReference<Network?>(null)
    private var lastUnderlay: Underlay? = null
    private var activeId = ""
    private var generation = 0
    private var foreground = false
    private val cm: ConnectivityManager
        get() = getSystemService(ConnectivityManager::class.java)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "Server VPN", NotificationManager.IMPORTANCE_LOW)
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DOWN) {
            submit {
                down()
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        try {
            promote()
        } catch (_: Throwable) {
            TunnelManager.failed()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        submit {
            try {
                up()
            } catch (_: Throwable) {
                down()
                TunnelManager.failed()
                stopSelf(startId)
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        submit {
            down()
            runCatching { ConfigRepository.get(this).deselect() }
            stopSelf()
        }
    }

    override fun onDestroy() {
        submit { down() }
        worker.shutdown()
        super.onDestroy()
    }

    private fun submit(work: () -> Unit) {
        if (!worker.isShutdown) runCatching { worker.execute(work) }
    }

    private fun up() {
        val config =
            ConfigRepository.get(this).activeTunnel()
                ?: run {
                    down()
                    stopSelf()
                    return
                }
        if (backend.isRunning && activeId == config.id) return
        down(removeNotification = false)
        ConfigPolicy.validate(config)
        registerUnderlay()
        updateUnderlay()
        activeId = config.id
        TunnelManager.active(config.id)
        val session = ++generation
        val listener =
            object : dev.stunmesh.android.backend.EventListener {
                override fun onStateChanged(state: dev.stunmesh.android.backend.BackendState) {
                    TunnelManager.onStateChanged(state)
                    if (state == dev.stunmesh.android.backend.BackendState.DOWN)
                        submit {
                            if (generation == session && activeId.isNotEmpty()) {
                                down()
                                stopSelf()
                            }
                        }
                }

                override fun onLog(level: String, message: String) {
                    TunnelManager.onLog(level, message)
                }

                override fun onEvent(event: dev.stunmesh.android.backend.BackendEvent) {
                    TunnelManager.onEvent(event)
                }
            }
        backend.start(
            config.toJson(),
            TunProvider { mtu -> establish(config, mtu) },
            SocketProtector { fd -> protectOuter(fd) },
            listener,
        )
        TunnelManager.underlay(selected.get() != null)
    }

    private fun down(removeNotification: Boolean = true) {
        generation++
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        networks.clear()
        selected.set(null)
        lastUnderlay = null
        activeId = ""
        try {
            backend.stop()
        } finally {
            TunnelManager.onStateChanged(dev.stunmesh.android.backend.BackendState.DOWN)
            if (removeNotification && foreground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
            }
        }
    }

    private fun establish(config: TunnelConfig, mtu: Int): Int {
        ConfigPolicy.validate(config)
        val b =
            Builder()
                .setSession("Server services")
                .setMtu(mtu)
                .allowFamily(OsConstants.AF_INET)
                .allowFamily(OsConstants.AF_INET6)
        config.iface.addresses
            .map { ConfigPolicy.cidr(it, false) }
            .forEach { b.addAddress(it.address, it.bits) }
        config.peers
            .flatMap { it.allowedIps }
            .map { ConfigPolicy.cidr(it, true) }
            .forEach { b.addRoute(it.address, it.bits) }
        if (Build.VERSION.SDK_INT >= 29)
            b.setMetered(false) // inherit underlying networks; does not force unmetered
        b.setUnderlyingNetworks(selected.get()?.let { arrayOf(it) })
        return b.establish()?.detachFd() ?: throw IllegalStateException("VPN consent unavailable")
    }

    private fun protectOuter(fd: Int): Boolean {
        return try {
            if (!protect(fd)) false
            else {
                val network = selected.get() ?: return false
                ParcelFileDescriptor.fromFd(fd).use { network.bindSocket(it.fileDescriptor) }
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun registerUnderlay() {
        lateinit var current: ConnectivityManager.NetworkCallback
        current =
            object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                    submit {
                        if (callback !== current) return@submit
                        networks[network] = properties
                        transition()
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    submit {
                        if (callback !== current) return@submit
                        cm.getLinkProperties(network)?.let { networks[network] = it }
                        transition()
                    }
                }

                override fun onLost(network: Network) {
                    submit {
                        if (callback !== current) return@submit
                        networks.remove(network)
                        transition()
                    }
                }
            }
        callback = current
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            current,
        )
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network)
            if (
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                cm.getLinkProperties(network)?.let { networks[network] = it }
            }
        }
    }

    private fun transition() {
        try {
            updateUnderlay()
        } catch (_: Throwable) {
            down()
            TunnelManager.failed()
            stopSelf()
        }
    }

    private fun updateUnderlay() {
        val eligible =
            networks.keys.filter { n ->
                cm.getNetworkCapabilities(n)?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        val network =
            cm.activeNetwork?.takeIf { it in eligible }
                ?: eligible
                    .sortedWith(
                        compareByDescending<Network> {
                                cm.getNetworkCapabilities(it)
                                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ==
                                    true
                            }
                            .thenByDescending {
                                cm.getNetworkCapabilities(it)
                                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                            }
                            .thenBy { it.networkHandle }
                    )
                    .firstOrNull()
        val lp = network?.let { networks[it] }
        val links = if (lp == null) emptyList() else listOf(lp)
        val addresses =
            links
                .flatMap { it.linkAddresses }
                .map { it.address }
                .filterNot { it.isLinkLocalAddress || it.isLoopbackAddress }
        val dns =
            lp?.dnsServers
                .orEmpty()
                .filterNot { it.isLinkLocalAddress }
                .mapNotNull { it.hostAddress }
                .sorted()
                .joinToString(",")
        val next =
            Underlay(
                network,
                dns,
                addresses.any { it is Inet4Address } ||
                    (Build.VERSION.SDK_INT >= 30 && lp?.nat64Prefix != null),
                addresses.any { it is Inet6Address },
            )
        if (next == lastUnderlay) return
        val rebind =
            lastUnderlay?.network != network ||
                lastUnderlay?.v4 != next.v4 ||
                lastUnderlay?.v6 != next.v6
        selected.set(network)
        setUnderlyingNetworks(network?.let { arrayOf(it) })
        lastUnderlay = next
        backend.underlay(network != null, next.v4, next.v6, dns, rebind)
        if (backend.isRunning) TunnelManager.underlay(network != null)
    }

    private fun promote() {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val n =
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Server VPN enabled")
                .setContentText("Selected server routes only · tap for status")
                .setOngoing(true)
                .setContentIntent(open)
                .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(NOTIFICATION, n)
        foreground = true
    }

    private data class Underlay(
        val network: Network?,
        val dns: String,
        val v4: Boolean,
        val v6: Boolean,
    )

    companion object {
        const val ACTION_UP = "dev.stunmesh.local.UP"
        const val ACTION_DOWN = "dev.stunmesh.local.DOWN"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION = 1
    }
}
