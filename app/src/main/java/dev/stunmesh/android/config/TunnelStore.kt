package dev.stunmesh.android.config
import org.json.JSONArray
import org.json.JSONObject

data class TunnelStore(val tunnels:List<TunnelConfig> = emptyList(),val activeId:String="") {
    val active:TunnelConfig? get()=tunnels.firstOrNull{it.id==activeId}
    fun toJson():String=JSONObject().put("schema",1).put("active_id",activeId).put("tunnels",JSONArray().apply{tunnels.forEach{put(it.storedJson())}}).toString()
    override fun toString()="TunnelStore(private)"
    companion object {
        fun fromJson(text:String):TunnelStore {
            val o=StrictDocument.parse(text);o.fields("schema","active_id","tunnels");require(o.number("schema",0)==1){"Unsupported store version"}
            val a=o.array("tunnels");require(a.length()<=16){"Too many profiles"}
            val tunnels=(0 until a.length()).map{TunnelConfig.fromJsonObject(a.getJSONObject(it)).also{t->ConfigPolicy.validate(t)}}
            require(tunnels.map{it.id}.distinct().size==tunnels.size){"Duplicate profile identity"}
            val active=o.text("active_id");require(active.isEmpty()||tunnels.any{it.id==active}){"Unknown active profile"}
            return TunnelStore(tunnels,active)
        }
    }
}

/** Serialize read-modify-write; publish state only after a durable replacement.
 * The Android adapter uses AtomicFile. Failing writes retain the old snapshot. */
internal class StoreCoordinator(private val read:()->TunnelStore,private val write:(TunnelStore)->Unit) {
    private var cached:TunnelStore?=null
    @Synchronized fun snapshot():TunnelStore=cached?:read().also{cached=it}
    @Synchronized fun update(change:(TunnelStore)->TunnelStore):Pair<TunnelStore,Boolean>{
        val previous=snapshot();val next=change(previous)
        if(next==previous)return previous to false
        require(next.toJson().toByteArray().size<=StrictDocument.MAX_BYTES)
        write(next);cached=next;return next to true
    }
}
