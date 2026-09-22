package dev.stunmesh.android.backend

fun interface TunProvider {
    fun openTun(mtu: Int): Int
}

fun interface SocketProtector {
    fun protect(fd: Int): Boolean
}

interface EventListener {
    fun onStateChanged(state: BackendState)

    fun onLog(level: String, message: String)

    fun onEvent(event: BackendEvent)
}

enum class BackendState {
    DOWN,
    STARTING,
    UP,
    STOPPING,
}

data class BackendEvent(val kind: String, val peerPublicKey: String?, val detail: String)
