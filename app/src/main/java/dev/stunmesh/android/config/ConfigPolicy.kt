package dev.stunmesh.android.config

import java.net.InetAddress
import java.net.URI
import java.util.Base64
import java.util.UUID

/** Typed routes feed BOTH Android and WireGuard. No DNS lookups occur here. */
data class Cidr(val address: InetAddress, val bits: Int) { val text:String get()="${address.hostAddress}/$bits" }
object ConfigPolicy {
    fun ip(text:String):InetAddress {
        require(text.length in 2..45 && '%' !in text && text.none { it.isWhitespace() }) {"Invalid numeric IP"}
        if (':' !in text) {
            val p=text.split('.');require(p.size==4 && p.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt()<=255 }) {"Invalid numeric IP"}
        } else require(text.all { it in "0123456789abcdefABCDEF:." }) {"Invalid numeric IP"}
        val ip=InetAddress.getByName(text)
        require(!ip.isAnyLocalAddress && !ip.isMulticastAddress && !ip.isLoopbackAddress && !ip.isLinkLocalAddress) {"IP outside service scope"}
        require(':' !in text || ip.address.size==16) {"Mapped IPv4 is unsupported"}
        return ip
    }
    fun cidr(text:String, service:Boolean):Cidr {
        require(text.length<=64 && text.count { it=='/' }==1) {"Invalid prefix"}
        val ip=ip(text.substringBefore('/'));val bits=text.substringAfter('/').toIntOrNull() ?: throw IllegalArgumentException("Invalid prefix")
        require(bits in 0..ip.address.size*8) {"Invalid prefix"}
        if(service)require(bits>=if(ip.address.size==4)24 else 64) {"Only narrow server routes are allowed"}
        if(!service)return Cidr(ip,bits)
        val bytes=ip.address
        for(i in bytes.indices){val remaining=(bits-i*8).coerceIn(0,8);bytes[i]=(bytes[i].toInt() and (0xff shl (8-remaining))).toByte()}
        return Cidr(InetAddress.getByAddress(bytes),bits)
    }
    fun key(text:String) {require(text.length==44 && !text.any { it.isWhitespace() }) {"Invalid WG key"};val b=try{Base64.getDecoder().decode(text)}catch(_:Exception){throw IllegalArgumentException("Invalid WG key")};require(b.size==32 && Base64.getEncoder().encodeToString(b)==text && b.any {it!=0.toByte()}) {"Invalid WG key"}}
    fun endpoint(text:String) {if(text.isEmpty())return;require(text.length<=256);val host=if(text.startsWith("["))text.substringAfter('[').substringBefore(']') else text.substringBeforeLast(':');ip(host);val port=text.substringAfterLast(':').toIntOrNull();require(port!=null && port in 1..65535);require(text==if(':' in host)"[$host]:$port" else "$host:$port")}
    fun origin(text:String){val u=try{URI(text)}catch(_:Exception){throw IllegalArgumentException("Invalid proxy origin")};require(text.length<=1024&&u.scheme=="https"&&!u.host.isNullOrEmpty()&&u.userInfo==null&&u.query==null&&u.fragment==null&&(u.path.isNullOrEmpty()||u.path=="/")&&(u.port==-1||u.port in 1..65535)){"OpenDHT requires an HTTPS origin"}}
    fun server(text:String){require(text.length<=256&&!text.any {it.isWhitespace()||it in "/?#@"});val u=try{URI("stun://$text")}catch(_:Exception){throw IllegalArgumentException("Invalid STUN server")};require(!u.host.isNullOrEmpty()&&u.port in 1..65535&&u.path.isNullOrEmpty())}
    fun validate(t:TunnelConfig,needsPrivateKey:Boolean=true){
        require(runCatching{UUID.fromString(t.id)}.isSuccess&&t.name.length in 1..128&&t.name.none{it.isISOControl()}) {"Invalid profile identity"}
        require(t.proposalId.length<=128&&t.proposalId.none{it.isISOControl()})
        if(needsPrivateKey)key(t.iface.privateKey) else require(t.iface.privateKey.isEmpty()) {"Private-key import is forbidden"}
        require(t.iface.addresses.size in 1..8);t.iface.addresses.forEach{cidr(it,false)}
        require(t.iface.dnsServers.isEmpty()) {"VPN DNS is disabled for destination split tunneling"}
        require(t.iface.listenPort in 0..65535&&t.iface.mtu in 1280..1500&&t.iface.protocol in setOf("ipv4","ipv6","dualstack"))
        require(t.refreshIntervalSeconds in 60..240&&t.logLevel in setOf("info","warn","error","silent"))
        require(t.peers.size in 1..32&&t.plugins.size<=8&&t.stunServers.size<=8)
        require(t.peers.map{it.publicKey}.distinct().size==t.peers.size&&t.plugins.map{it.instance}.distinct().size==t.plugins.size)
        val routes=mutableSetOf<String>()
        t.peers.forEach{p->key(p.publicKey);if(p.presharedKey.isNotEmpty())key(p.presharedKey);require(p.name.length<=128&&p.description.length<=512);require(p.allowedIps.size in 1..64);p.allowedIps.forEach{require(routes.add(cidr(it,true).text)){"Duplicate peer route"}};endpoint(p.endpoint);require(p.persistentKeepalive==0||p.persistentKeepalive in 15..65535);require(p.protocol in setOf("ipv4","ipv6","prefer_ipv4","prefer_ipv6"));require(if(p.plugin.isEmpty())p.endpoint.isNotEmpty() else t.plugins.any{it.instance==p.plugin})}
        t.stunServers.forEach(::server)
        t.plugins.forEach{p->require(p.type=="builtin"&&p.name=="opendht"&&p.instance.length in 1..128);val o=p.toJson();PluginDefinition.fromJson(o);val endpoints=mutableListOf<String>();(p.config["endpoint"] as? String)?.let{endpoints.add(it)};(p.config["endpoints"] as? List<*>)?.forEach{endpoints.add(it as? String?:throw IllegalArgumentException("Invalid proxy list"))};require(endpoints.size in 1..8);endpoints.forEach(::origin);p.config["timeout"]?.let{require(it is String&&it.matches(Regex("([1-9]|1[0-9]|20)s")))}}
        require(t.toJson().toByteArray().size<=StrictDocument.MAX_BYTES)
    }
}
