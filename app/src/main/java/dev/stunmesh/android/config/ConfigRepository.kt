package dev.stunmesh.android.config

import dev.stunmesh.android.readBounded
import android.app.backup.BackupManager
import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mobile.Mobile
import java.io.File
import java.io.IOException

sealed interface RepositoryState {
    data object Loading:RepositoryState
    data class Ready(val profiles:List<PublicTunnel>,val selectedId:String):RepositoryState
    data object Unavailable:RepositoryState
}

/** Single process-wide authority. No caller can save an old UI snapshot over a
 * newer selection. Errors preserve the file; only a missing file means empty. */
class ConfigRepository private constructor(context:Context) {
    private val app=context.applicationContext
    private val atomic=AtomicFile(File(app.noBackupFilesDir,"configuration.v1.bin"))
    private val crypto=HardwareCipher(app)
    private val coordinator=StoreCoordinator(::read,::write)
    private val mutableState=MutableStateFlow<RepositoryState>(RepositoryState.Loading)
    val state:StateFlow<RepositoryState> = mutableState.asStateFlow()

    @Synchronized fun load(){try{publish(coordinator.snapshot())}catch(_:Exception){mutableState.value=RepositoryState.Unavailable}}
    @Synchronized fun activeTunnel():TunnelConfig?=coordinator.snapshot().active
    @Synchronized fun select(id:String)=change{old->require(old.tunnels.any{it.id==id});old.copy(activeId=id)}
    @Synchronized fun deselect()=change{it.copy(activeId="")}
    @Synchronized fun remove(id:String)=change{old->old.copy(tunnels=old.tunnels.filterNot{it.id==id},activeId=old.activeId.takeUnless{it==id}?:"")}
    @Synchronized fun rename(id:String,name:String)=change{old->old.copy(tunnels=old.tunnels.map{if(it.id==id)it.copy(name=name).also{t->validate(t)}else it})}
    @Synchronized fun enroll(public: TunnelConfig, psk:String=""):PublicTunnel {
        ConfigPolicy.validate(public,needsPrivateKey=false)
        require(public.peers.all{it.presharedKey.isEmpty()})
        if(psk.isNotEmpty()){ConfigPolicy.key(psk);require(public.peers.size==1)}
        val identity=Mobile.generatePrivateKey()
        val enrolled=public.copy(iface=public.iface.copy(privateKey=identity),peers=public.peers.map{it.copy(presharedKey=psk)})
        validate(enrolled)
        change{old->require(old.tunnels.size<16);require(old.tunnels.none{it.id==enrolled.id || enrolled.proposalId.isNotEmpty()&&it.proposalId==enrolled.proposalId}){"Proposal already enrolled"};old.copy(tunnels=old.tunnels+enrolled)}
        return summary(enrolled)
    }
    /** Called only by the OS-bound BackupAgent; never exposed through IPC/UI. */
    @Synchronized internal fun backupSnapshot():ByteArray=coordinator.snapshot().toJson().toByteArray(Charsets.UTF_8).also{require(it.size<=StrictDocument.MAX_BYTES)}
    @Synchronized internal fun restoreSnapshot(bytes:ByteArray){
        require(bytes.size<=StrictDocument.MAX_BYTES)
        val restored=TunnelStore.fromJson(bytes.toString(Charsets.UTF_8)).copy(activeId="")
        restored.tunnels.forEach(::validate)
        // snapshot() must succeed: unreadable existing data is never replaced.
        change(notifyBackup=false){restored}
    }
    private fun validate(t:TunnelConfig){ConfigPolicy.validate(t);Mobile.validateConfig(t.toJson())}
    private fun read():TunnelStore {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists())return TunnelStore()
        val bytes=atomic.openRead().use{it.readBounded(StrictDocument.MAX_BYTES+64)}
        require(bytes.size<=StrictDocument.MAX_BYTES+32)
        val plain=crypto.decrypt(bytes)
        return try{TunnelStore.fromJson(plain.toString(Charsets.UTF_8)).also{it.tunnels.forEach(::validate)}}finally{plain.fill(0);bytes.fill(0)}
    }
    private fun write(store:TunnelStore){
        val plain=store.toJson().toByteArray(Charsets.UTF_8)
        val encrypted=try{crypto.encrypt(plain)}finally{plain.fill(0)}
        var stream:java.io.FileOutputStream?=null
        try{stream=atomic.startWrite();stream.write(encrypted);atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw IOException("Configuration could not be saved",e)}finally{encrypted.fill(0)}
    }
    private fun change(notifyBackup:Boolean=true, transform:(TunnelStore)->TunnelStore){
        val (next,changed)=coordinator.update(transform);publish(next)
        if(changed&&notifyBackup)BackupManager(app).dataChanged() // OS coalesces; no app timer/job
    }
    private fun summary(t:TunnelConfig)=PublicTunnel(t.id,t.name,Mobile.publicKey(t.iface.privateKey),t.peers.map{it.publicKey},t.iface.addresses,t.peers.flatMap{it.allowedIps}.map{ConfigPolicy.cidr(it,true).text},t.proposalId)
    private fun publish(store:TunnelStore){mutableState.value=RepositoryState.Ready(store.tunnels.map(::summary),store.activeId)}
    companion object {
        @Volatile private var instance:ConfigRepository?=null
        fun get(context:Context):ConfigRepository=instance?:synchronized(this){instance?:ConfigRepository(context).also{instance=it}}
    }
}
