package dev.stunmesh.android.config

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in device probe through the separately installed release VPN; no identity/store access. */
class LiveSplitTunnelTest {
    @Test
    fun selectedServerTransferAndOrdinaryInternet() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live_split_tunnel") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".debug"))
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val vpn =
            cm.allNetworks.single {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ==
                    true
            }
        val links = requireNotNull(cm.getLinkProperties(vpn))
        assertTrue(links.routes.any { it.destination.toString() == "10.77.0.1/32" })
        assertTrue(links.routes.none { it.destination.prefixLength == 0 })
        assertTrue(links.dnsServers.isEmpty())

        // Ordinary sockets: no bindToNetwork/protect/bypass or access to the release's key
        // material.
        val sent = ByteArray(32 * 1024) { (it % 251).toByte() }
        val received = ByteArray(sent.size)
        Socket().use { socket ->
            socket.soTimeout = 10_000
            socket.connect(InetSocketAddress("10.77.0.1", 18080), 5_000)
            socket.getOutputStream().write(sent)
            socket.shutdownOutput()
            DataInputStream(socket.getInputStream()).readFully(received)
        }
        assertArrayEquals(sent, received)

        val internet = URL("https://example.com/").openConnection() as HttpsURLConnection
        try {
            internet.connectTimeout = 10_000
            internet.readTimeout = 10_000
            internet.instanceFollowRedirects = false
            assertEquals(200, internet.responseCode)
            internet.inputStream.use { assertTrue(it.read() >= 0) }
        } finally {
            internet.disconnect()
        }
        println("PASS: 32768-byte server echo, narrow routes, no VPN DNS, ordinary HTTPS")
    }
}
