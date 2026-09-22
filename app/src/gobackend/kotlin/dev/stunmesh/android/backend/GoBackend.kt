package dev.stunmesh.android.backend

import mobile.Mobile
import mobile.Node

/** Concrete, mandatory local core. Owned and serialized by the VPN service. */
class GoBackend {
    private var node:Node?=null
    private var online=false
    private var ipv4=false
    private var ipv6=false
    private var dns=""
    val isRunning:Boolean get()=node?.isRunning?:false
    fun start(configJson:String,tunProvider:TunProvider,socketProtector:SocketProtector,eventListener:EventListener){
        check(node==null)
        val current=Mobile.newNode(configJson,{mtu->tunProvider.openTun(mtu)},{fd->socketProtector.protect(fd)},object:mobile.EventListener{
            override fun onStateChanged(state:String){eventListener.onStateChanged(when(state){"starting"->BackendState.STARTING;"up"->BackendState.UP;"stopping"->BackendState.STOPPING;else->BackendState.DOWN})}
            override fun onLog(level:String,message:String){eventListener.onLog(level,message)}
            override fun onEvent(kind:String,peerPublicKey:String,detail:String){eventListener.onEvent(BackendEvent(kind,peerPublicKey.ifEmpty{null},detail))}
        })
        node=current
        try{current.setDNSServers(dns);current.setUnderlay(online,ipv4,ipv6,false);current.start()}catch(t:Throwable){current.stop();node=null;throw t}
    }
    fun stop(){try{node?.stop()}finally{node=null}}
    fun underlay(available:Boolean,v4:Boolean,v6:Boolean,servers:String,rebind:Boolean){
        online=available;ipv4=v4;ipv6=v6;dns=servers
        node?.setDNSServers(servers);node?.setUnderlay(available,v4,v6,rebind)
    }
}
