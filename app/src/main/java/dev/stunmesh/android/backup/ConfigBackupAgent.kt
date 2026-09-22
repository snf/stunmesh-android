package dev.stunmesh.android.backup

import dev.stunmesh.android.readBounded
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.StrictDocument
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** The sole intentional recovery boundary. Transport encryption is an OS trust
 * promise, not an app cryptographic protocol. No plaintext staging files. */
class ConfigBackupAgent:BackupAgent() {
    override fun onBackup(oldState:ParcelFileDescriptor?,data:BackupDataOutput,newState:ParcelFileDescriptor){
        if(data.transportFlags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED==0)throw IOException("Encrypted OS backup transport required")
        val bytes=try{ConfigRepository.get(this).backupSnapshot()}catch(_:Exception){throw IOException("Configuration unavailable; backup unchanged")}
        try{
            val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
            val previous=oldState?.let{runCatching{FileInputStream(it.fileDescriptor).use{input->input.readBounded(32)}}.getOrNull()}
            if(previous==null||!digest.contentEquals(previous)){data.writeEntityHeader(ENTITY,bytes.size);data.writeEntityData(bytes,bytes.size)}
            FileOutputStream(newState.fileDescriptor).use{it.write(digest)}
        }finally{bytes.fill(0)}
    }
    override fun onRestore(data:BackupDataInput,appVersionCode:Int,newState:ParcelFileDescriptor){
        var snapshot:ByteArray?=null
        try{
            while(data.readNextHeader()){
                if(data.key!=ENTITY||snapshot!=null||data.dataSize !in 1..StrictDocument.MAX_BYTES)throw IOException("Unsupported backup entity")
                val bytes=ByteArray(data.dataSize);snapshot=bytes;var offset=0
                while(offset<bytes.size){val n=data.readEntityData(bytes,offset,bytes.size-offset);if(n<=0)throw IOException("Truncated backup");offset+=n}
            }
            val bytes=snapshot?:throw IOException("Configuration backup missing")
            try{ConfigRepository.get(this).restoreSnapshot(bytes)}catch(_:Exception){throw IOException("Configuration restore rejected; prior state preserved")}
            // Empty revision forces the next encrypted backup to include the
            // now-inactive restored state; no tunnel starts from this callback.
            FileOutputStream(newState.fileDescriptor).use{it.write(ByteArray(0))}
        }finally{snapshot?.fill(0)}
    }
    override fun onFullBackup(data:FullBackupDataOutput) { /* no generic file/device-transfer fallback */ }
    private companion object {const val ENTITY="configuration.v1"}
}
