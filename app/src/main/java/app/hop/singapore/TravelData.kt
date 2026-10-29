package app.hop.singapore

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TravelStore(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"travel.json"))
    fun read():List<TravelItem> {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return emptyList()
        val data=JSONObject(file.openRead().bufferedReader().use { it.readText() })
        check(data.getInt("version")==1)
        val items=data.getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it).let { p->
            TravelItem(p.getString("id"),p.getString("title"),p.optString("content"),p.optString("url"),runCatching { TravelKind.valueOf(p.optString("kind")) }.getOrDefault(TravelKind.NOTE),p.optString("date"),p.optString("time"),p.optString("note"),p.optBoolean("pinned"),p.optBoolean("done"),p.optString("placeId"),p.optLong("createdAt"))
        } }
    }
    fun write(items:List<TravelItem>) {
        val data=JSONObject().put("version",1).put("items",JSONArray().apply { items.forEach { p->
            put(JSONObject().put("id",p.id).put("title",p.title).put("content",p.content).put("url",p.url).put("kind",p.kind.name).put("date",p.date).put("time",p.time).put("note",p.note).put("pinned",p.pinned).put("done",p.done).put("placeId",p.placeId).put("createdAt",p.createdAt))
        } })
        val stream=file.startWrite()
        try { stream.write(data.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(e:Exception) { file.failWrite(stream); throw e }
    }
}
class TravelViewModel(app:Application):AndroidViewModel(app) {
    private val store=TravelStore(app); private val ready=CompletableDeferred<Unit>(); private val lock=Mutex()
    var items by mutableStateOf<List<TravelItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var failure by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var section by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var filter by mutableIntStateOf(0)
    var selectedId by mutableStateOf<String?>(null)
    var editing by mutableStateOf<TravelItem?>(null)
    var editError by mutableStateOf<String?>(null)
    var fromShare by mutableStateOf(false)
    var deleted by mutableStateOf<TravelItem?>(null)
    val selected get()=items.find { it.id==selectedId }
    init { viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { store.read() } }.onSuccess { items=it }.onFailure { failure="여행 보관함을 열지 못했어요. 기존 파일은 그대로 유지됩니다." }
        loading=false; ready.complete(Unit)
    } }
    private fun mutate(action:(List<TravelItem>)->List<TravelItem>,after:()->Unit={}) { viewModelScope.launch {
        ready.await(); if(failure!=null) { message=failure; return@launch }
        lock.withLock {
            saving=true
            try { val next=action(items); withContext(Dispatchers.IO) { store.write(next) }; items=next; after() }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { message="저장하지 못했어요. 다시 시도해 주세요."; if(editing!=null) editError=message }
            finally { saving=false }
        }
    } }
    fun capture(text:String,subject:String="") {
        if(text.isBlank() && subject.isBlank()) return
        val incoming=TravelShare.capture(text,subject).copy(id=UUID.randomUUID().toString())
        var target=incoming.id; var duplicate=false
        mutate({ old ->
            val existing=old.find { TravelShare.key(it)==TravelShare.key(incoming) }
            if(existing!=null) { target=existing.id; duplicate=true; old } else old+incoming
        }) { section=0; selectedId=target; editing=null; fromShare=true; message=if(duplicate) "이미 보관한 정보예요. 기존 일정과 메모를 유지했어요." else "공유함에 보관했어요." }
    }
    fun beginNew() { editing=TravelItem("",""); editError=null }
    fun plan(place:Place) {
        var target=TravelItem(UUID.randomUUID().toString(),place.name,place.name,mapPlaceUrl(place),TravelKind.PLACE,note=place.memo,placeId=place.id)
        mutate({ old->
            val existing=old.find { it.placeId==place.id }
            if(existing!=null) { target=existing; old } else old+target
        }) { section=1; selectedId=target.id; fromShare=false; edit(target) }
    }
    fun edit(item:TravelItem) { editing=item; editError=null }
    fun saveEdit() {
        val input=editing ?: return
        val value=input.copy(title=input.title.trim(),url=input.url.trim(),time=input.time.trim())
        editError=travelDateError(value); if(editError!=null || saving) return
        val item=if(value.id.isBlank()) value.copy(id=UUID.randomUUID().toString(),content=value.content.ifBlank { value.url.ifBlank { value.title } }) else value
        mutate({ old->old.filterNot { it.id==item.id }+item }) { editing=null; selectedId=item.id; message="여행 정보 저장 완료" }
    }
    fun togglePin(item:TravelItem)=mutate({ old->old.map { if(it.id==item.id) it.copy(pinned=!it.pinned) else it } })
    fun toggleDone(item:TravelItem)=mutate({ old->old.map { if(it.id==item.id) it.copy(done=!it.done) else it } })
    fun attach(id:String,place:Place)=mutate({ old->old.map { if(it.id==id) it.copy(placeId=place.id) else it } }) { selectedId=id; message="${place.name}와 연결했어요. MRT를 확인할 수 있어요." }
    fun remove(item:TravelItem)=mutate({ old->old.filterNot { it.id==item.id } }) { selectedId=null; deleted=item }
    fun restore(item:TravelItem)=mutate({ old->if(old.any { it.id==item.id }) old else old+item })
}
