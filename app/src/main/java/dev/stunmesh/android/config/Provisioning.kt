package dev.stunmesh.android.config

import org.json.JSONObject

class Enrollment(val publicConfig: TunnelConfig, val presharedKey: String = "") {
    override fun toString() = "Enrollment{credentials redacted}"
}

object Provisioning {
    const val SCHEMA = "stunmesh-enroll-v2"

    fun decode(text: String): Enrollment {
        if (WgQuickConf.looksLikeConf(text)) return Enrollment(WgQuickConf.decode(text, "Server"))
        val o = StrictDocument.parse(text)
        if (o.has("wireguard")) return Enrollment(TunnelYaml.decode(text))
        o.fields(
            "schema",
            "proposal_id",
            "name",
            "address",
            "server_public_key",
            "allowed_ips",
            "stun_servers",
            "opendht",
            "endpoint",
            "protocol",
            "preshared_key",
        )
        require(text.toByteArray(Charsets.UTF_8).size <= 2048) {
            "Enrollment exceeds QR limit"
        }
        require(o.text("schema") == SCHEMA) { "Unsupported enrollment" }
        val proposal = o.text("proposal_id")
        require(
            proposal.matches(
                Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            )
        ) {
            "Invalid proposal identifier"
        }
        val psk = o.text("preshared_key")
        if (o.has("preshared_key")) ConfigPolicy.key(psk)
        val protocol = o.text("protocol", "ipv4")
        val t =
            TunnelConfig(
                name = o.text("name", "Server"),
                proposalId = proposal,
                iface =
                    InterfaceConfig(
                        addresses = listOf(o.text("address")),
                        protocol = if (protocol.startsWith("prefer_")) "dualstack" else protocol,
                    ),
                peers =
                    listOf(
                        PeerConfig(
                            name = "server",
                            publicKey = o.text("server_public_key"),
                            allowedIps = o.strings("allowed_ips"),
                            endpoint = o.text("endpoint"),
                            plugin = "dht",
                            protocol = protocol,
                        )
                    ),
                plugins =
                    listOf(PluginDefinition(config = mapOf("endpoints" to o.strings("opendht")))),
                stunServers = o.strings("stun_servers"),
            )
        ConfigPolicy.validate(t, false)
        return Enrollment(t, psk)
    }

    /** Public response only. A proposal ID matches paperwork, never authorizes a peer. */
    fun response(t: PublicTunnel): String =
        JSONObject()
            .put("schema", "stunmesh-peer-v1")
            .put("proposal_id", t.proposalId)
            .put("public_key", t.publicKey)
            .put("addresses", org.json.JSONArray(t.addresses))
            .toString()
}
