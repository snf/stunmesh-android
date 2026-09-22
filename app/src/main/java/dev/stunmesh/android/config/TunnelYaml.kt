package dev.stunmesh.android.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Public-only compatibility syntax. Secret export was deleted; restoration uses the OS agent and
 * the versioned internal store, never this importer.
 */
object TunnelYaml {
    fun decode(text: String): TunnelConfig {
        val root = StrictDocument.parse(text)
        root.fields("schema", "name", "wireguard", "stunmesh")
        require(root.number("schema", 1) == 1) { "Unsupported public profile schema" }
        val wg = root.obj("wireguard")
        wg.fields("addresses", "dns_servers", "listen_port", "mtu", "peers")
        val sm = root.obj("stunmesh")
        sm.fields("protocol", "stun", "plugins", "refresh_interval_seconds", "log", "peers")
        val overlays = sm.array("peers")
        val byKey = mutableMapOf<String, JSONObject>()
        for (i in 0 until overlays.length()) {
            val peer = overlays.getJSONObject(i)
            peer.fields("public_key", "name", "description", "plugin", "protocol")
            require(byKey.put(peer.text("public_key"), peer) == null) { "Duplicate peer overlay" }
        }
        val peers = wg.array("peers")
        val used = mutableSetOf<String>()
        val out =
            JSONObject()
                .put("name", root.text("name", "Server"))
                .put(
                    "interface",
                    JSONObject()
                        .put("addresses", wg.array("addresses"))
                        .put("dns_servers", wg.array("dns_servers"))
                        .put("listen_port", wg.number("listen_port", 0))
                        .put("mtu", wg.number("mtu", 1420))
                        .put("protocol", sm.text("protocol", "ipv4")),
                )
                .put("plugins", sm.array("plugins"))
                .put("stun", sm.obj("stun"))
                .put("refresh_interval_seconds", sm.number("refresh_interval_seconds", 180))
                .put("log", sm.obj("log"))
        out.put(
            "peers",
            JSONArray().apply {
                for (i in 0 until peers.length()) {
                    val p = peers.getJSONObject(i)
                    p.fields("public_key", "allowed_ips", "endpoint", "persistent_keepalive")
                    val key = p.text("public_key")
                    used.add(key)
                    val overlay = byKey[key] ?: JSONObject()
                    put(
                        JSONObject()
                            .put("public_key", key)
                            .put("allowed_ips", p.array("allowed_ips"))
                            .put("endpoint", p.text("endpoint"))
                            .put("persistent_keepalive", p.number("persistent_keepalive", 25))
                            .put("name", overlay.text("name"))
                            .put("description", overlay.text("description"))
                            .put("plugin", overlay.text("plugin"))
                            .put("protocol", overlay.text("protocol", "ipv4"))
                    )
                }
            },
        )
        require(used.containsAll(byKey.keys)) { "Overlay references unknown peer" }
        return TunnelConfig.fromJsonObject(out).also { ConfigPolicy.validate(it, false) }
    }
}
