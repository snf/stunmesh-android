package dev.stunmesh.android.config

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Private in-process model. It is serialized only to the core, protected local
 * storage or the OS backup agent. UI/sharing uses PublicTunnel, never this model. */
data class TunnelConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Server",
    val iface: InterfaceConfig = InterfaceConfig(),
    val peers: List<PeerConfig> = emptyList(),
    val plugins: List<PluginDefinition> = emptyList(),
    val stunServers: List<String> = emptyList(),
    val refreshIntervalSeconds: Int = 180,
    val logLevel: String = "info",
    val proposalId: String = "",
) {
    fun toJson(): String = toJsonObject().toString()
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("interface", iface.toJson())
        put("peers", JSONArray().apply { peers.forEach { put(it.toJson()) } })
        put("plugins", JSONArray().apply { plugins.forEach { put(it.toJson()) } })
        put("stun", JSONObject().put("addresses", JSONArray(stunServers)))
        put("refresh_interval_seconds", refreshIntervalSeconds)
        put("log", JSONObject().put("level", logLevel))
        // Local enrollment metadata is deliberately absent from the core schema.
    }
    fun storedJson(): JSONObject = toJsonObject().put("proposal_id", proposalId)
    override fun toString(): String = "TunnelConfig(private)"
    companion object {
        fun fromJson(json: String): TunnelConfig = fromJsonObject(StrictDocument.parse(json))
        fun fromJsonObject(o: JSONObject): TunnelConfig {
            o.fields("id","name","interface","peers","plugins","stun","refresh_interval_seconds","log","proposal_id")
            val stun=o.obj("stun").apply { fields("addresses") }
            val log=o.obj("log").apply { fields("level") }
            return TunnelConfig(
                id=o.text("id").ifEmpty { UUID.randomUUID().toString() },name=o.text("name","Server"),
                iface=InterfaceConfig.fromJson(o.obj("interface")),
                peers=o.array("peers").let { a -> (0 until a.length()).map { PeerConfig.fromJson(a.getJSONObject(it)) } },
                plugins=o.array("plugins").let { a -> (0 until a.length()).map { PluginDefinition.fromJson(a.getJSONObject(it)) } },
                stunServers=stun.strings("addresses"),refreshIntervalSeconds=o.number("refresh_interval_seconds",180),
                logLevel=log.text("level","info"),proposalId=o.text("proposal_id"),
            )
        }
    }
}
data class InterfaceConfig(val privateKey:String="",val addresses:List<String> = emptyList(),val dnsServers:List<String> = emptyList(),val listenPort:Int=0,val mtu:Int=1420,val protocol:String="ipv4") {
    fun toJson():JSONObject=JSONObject().apply {put("private_key",privateKey);put("addresses",JSONArray(addresses));put("dns_servers",JSONArray(dnsServers));put("listen_port",listenPort);put("mtu",mtu);put("protocol",protocol)}
    override fun toString()="InterfaceConfig(private)"
    companion object {fun fromJson(o:JSONObject):InterfaceConfig {o.fields("private_key","addresses","dns_servers","listen_port","mtu","protocol");return InterfaceConfig(o.text("private_key"),o.strings("addresses"),o.strings("dns_servers"),o.number("listen_port",0),o.number("mtu",1420),o.text("protocol","ipv4"))}}
}
data class PeerConfig(val name:String="",val description:String="",val publicKey:String="",val presharedKey:String="",val allowedIps:List<String> = emptyList(),val endpoint:String="",val plugin:String="",val protocol:String="ipv4",val persistentKeepalive:Int=25) {
    fun toJson():JSONObject=JSONObject().apply {put("name",name);put("description",description);put("public_key",publicKey);put("preshared_key",presharedKey);put("allowed_ips",JSONArray(allowedIps));put("endpoint",endpoint);put("plugin",plugin);put("protocol",protocol);put("persistent_keepalive",persistentKeepalive)}
    override fun toString()="PeerConfig(private)"
    companion object {fun fromJson(o:JSONObject):PeerConfig {o.fields("name","description","public_key","preshared_key","allowed_ips","endpoint","plugin","protocol","persistent_keepalive");return PeerConfig(o.text("name"),o.text("description"),o.text("public_key"),o.text("preshared_key"),o.strings("allowed_ips"),o.text("endpoint"),o.text("plugin"),o.text("protocol","ipv4"),o.number("persistent_keepalive",25))}}
}
data class PluginDefinition(val instance:String="dht",val type:String="builtin",val name:String="opendht",val config:Map<String,Any> = emptyMap()) {
    fun toJson():JSONObject=JSONObject().put("instance",instance).put("type",type).put("name",name).put("config",JSONObject(config))
    companion object {fun fromJson(o:JSONObject):PluginDefinition {
        o.fields("instance","type","name","config");val c=o.obj("config");c.fields("endpoint","endpoints","timeout","dedup")
        val values=c.keys().asSequence().associateWith { key -> when(key) {"endpoints"->c.strings(key);"dedup"->c.get(key).also { require(it==false) };else->c.text(key)} }
        return PluginDefinition(o.text("instance","dht"),o.text("type","builtin"),o.text("name","opendht"),values)
    }}
}

data class PublicTunnel(val id:String,val name:String,val publicKey:String,val serverKeys:List<String>,val addresses:List<String>,val routes:List<String>,val proposalId:String)
