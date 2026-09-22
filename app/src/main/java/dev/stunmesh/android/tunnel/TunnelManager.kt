package dev.stunmesh.android.tunnel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.stunmesh.android.backend.BackendEvent
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.backend.EventListener
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.service.StunmeshVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Public event state only; secrets and the backend stay with their owners. */
object TunnelManager : EventListener {
    private val mutableState = MutableStateFlow(BackendState.DOWN)
    val state = mutableState.asStateFlow()
    private val mutableActive = MutableStateFlow("")
    val activeTunnelId = mutableActive.asStateFlow()
    private val mutableStatus = MutableStateFlow("Stopped")
    val status = mutableStatus.asStateFlow()
    private val mutableAuth = MutableStateFlow<Map<String, Long>>(emptyMap())
    val authenticated = mutableAuth.asStateFlow()
    private val mutableLog = MutableStateFlow<List<String>>(emptyList())
    val logLines = mutableLog.asStateFlow()

    fun active(id: String) {
        mutableActive.value = id
        mutableAuth.value = emptyMap()
    }

    fun start(context: Context, id: String) {
        ConfigRepository.get(context).select(id)
        ContextCompat.startForegroundService(
            context,
            Intent(context, StunmeshVpnService::class.java).setAction(StunmeshVpnService.ACTION_UP),
        )
    }

    fun stop(context: Context) {
        ConfigRepository.get(context).deselect()
        context.startService(
            Intent(context, StunmeshVpnService::class.java)
                .setAction(StunmeshVpnService.ACTION_DOWN)
        )
    }

    override fun onStateChanged(state: BackendState) {
        mutableState.value = state
        mutableStatus.value =
            when (state) {
                BackendState.DOWN -> "Stopped"
                BackendState.STARTING -> "Starting / discovering"
                BackendState.UP -> "Interface ready; awaiting authenticated traffic"
                BackendState.STOPPING -> "Stopping"
            }
        if (state == BackendState.DOWN) {
            mutableActive.value = ""
            mutableAuth.value = emptyMap()
        }
    }

    override fun onLog(level: String, message: String) {
        if (level == "warn" || level == "error")
            record("Discovery operation unavailable; bounded retry")
    }

    override fun onEvent(event: BackendEvent) {
        when (event.kind) {
            "peer_authenticated" ->
                event.peerPublicKey
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9+/]{43}=")) }
                    ?.let { key ->
                        mutableAuth.update {
                            (it + (key to System.currentTimeMillis())).entries.take(32).associate {
                                e ->
                                e.toPair()
                            }
                        }
                    }
            "peer_endpoint_updated" ->
                record("Peer endpoint hint applied; awaiting WireGuard authentication")
        }
    }

    fun underlay(available: Boolean) {
        mutableStatus.value =
            if (available) "Interface ready; discovery active"
            else "Interface ready; waiting for network"
    }

    fun failed() {
        onStateChanged(BackendState.DOWN)
        mutableStatus.value =
            "VPN could not start; check configuration, consent and hardware key storage"
        record("VPN operation failed; resources released")
    }

    private fun record(category: String) {
        mutableLog.update { if (it.lastOrNull() == category) it else (it + category).takeLast(64) }
    }

    fun diagnostics(): String =
        (listOf(
                "STUNMESH ${dev.stunmesh.android.BuildConfig.VERSION_NAME}",
                "Core SHA-256: ${dev.stunmesh.android.BuildConfig.CORE_SHA256}",
                "State: ${state.value}",
            ) + logLines.value)
            .joinToString("\n")
}
