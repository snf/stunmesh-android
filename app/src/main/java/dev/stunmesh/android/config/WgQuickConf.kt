package dev.stunmesh.android.config

/** Strict public-only wg-quick subset. Scripting, DNS override, private keys and
 * PSKs are rejected. Repeated lists are deliberate; singletons cannot repeat. */
object WgQuickConf {
    fun looksLikeConf(text:String)=text.lineSequence().any{it.trim().equals("[Interface]",true)}
    fun decode(text:String,name:String):TunnelConfig {
        require(text.toByteArray().size<=StrictDocument.MAX_BYTES){"Oversized configuration"}
        var iface=InterfaceConfig();val peers=mutableListOf<PeerConfig>();var section="";var interfaceSeen=false;var singleton=mutableSetOf<String>()
        for(raw in text.lineSequence()){
            require(raw.length<=4096){"Oversized configuration line"};val line=raw.substringBefore('#').trim();if(line.isEmpty())continue
            if(line.equals("[Interface]",true)){require(!interfaceSeen&&peers.isEmpty());interfaceSeen=true;section="interface";singleton=mutableSetOf();continue}
            if(line.equals("[Peer]",true)){require(interfaceSeen&&peers.size<32);peers.add(PeerConfig());section="peer";singleton=mutableSetOf();continue}
            require('=' in line&&section.isNotEmpty()){"Invalid configuration line"}
            val key=line.substringBefore('=').trim().lowercase();val value=line.substringAfter('=').trim()
            if(key !in setOf("address","allowedips"))require(singleton.add(key)){"Repeated singleton field"}
            if(section=="interface")iface=when(key){
                "address"->iface.copy(addresses=iface.addresses+list(value));"listenport"->iface.copy(listenPort=integer(value));"mtu"->iface.copy(mtu=integer(value));else->throw IllegalArgumentException("Unsupported or secret interface field")
            }else {val p=peers.last();peers[peers.lastIndex]=when(key){
                "publickey"->p.copy(publicKey=value);"allowedips"->p.copy(allowedIps=p.allowedIps+list(value));"endpoint"->p.copy(endpoint=value);"persistentkeepalive"->p.copy(persistentKeepalive=integer(value));else->throw IllegalArgumentException("Unsupported or secret peer field")
            }}
        }
        require(interfaceSeen);return TunnelConfig(name=name,iface=iface,peers=peers).also{ConfigPolicy.validate(it,false)}
    }
    private fun list(value:String)=value.split(',').map{it.trim().also{v->require(v.isNotEmpty())}}
    private fun integer(value:String)=value.toIntOrNull()?:throw IllegalArgumentException("Invalid integer")
}
