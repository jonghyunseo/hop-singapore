package app.hop.singapore

import android.app.Application
import android.content.Context
import android.location.Geocoder
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
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.UUID

private fun JSONArray.geo() = GeoPoint(getDouble(1), getDouble(0))
private fun JSONArray.points() = (0 until length()).map { getJSONArray(it).geo() }
fun loadRail(context: Context): RailData {
    val root = JSONObject(context.assets.open("singapore.json").bufferedReader().use { it.readText() })
    val array = root.getJSONArray("stations")
    val stations = (0 until array.length()).map { i -> array.getJSONObject(i).let { s ->
        val codes = s.getJSONArray("codes")
        Station(s.getString("id"), s.getString("name"), s.getJSONArray("coords").geo(), (0 until codes.length()).map { codes.getString(it) })
    }}
    val features = mutableListOf<MapFeature>()
    for (key in listOf("base", "lines")) {
        val fs = root.getJSONObject(key).getJSONArray("features")
        for (i in 0 until fs.length()) {
            val f = fs.getJSONObject(i); val g = f.getJSONObject("geometry"); val p = f.getJSONObject("properties")
            val c = g.getJSONArray("coordinates"); val type = g.getString("type")
            val paths = when(type) {
                "LineString" -> listOf(c.points())
                "Polygon", "MultiLineString" -> (0 until c.length()).map { c.getJSONArray(it).points() }
                else -> emptyList()
            }
            features += MapFeature(if(key == "lines") "rail" else p.optString("kind"), p.optString("name"), paths, type == "Polygon")
        }
    }
    return RailData(stations, features)
}

class PlaceStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "places.json"))
    fun read(): List<Place> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return starterPlaces.also(::write)
        val items = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        return (0 until items.length()).map { i -> items.getJSONObject(i).let {
            Place(it.getString("id"), it.getString("name"), it.optString("subtitle"), GeoPoint(it.getDouble("lat"), it.getDouble("lon")), it.optString("memo"), it.optBoolean("visited"))
        }}
    }
    fun write(places: List<Place>) {
        val json = JSONArray().apply { places.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("subtitle", p.subtitle).put("lat", p.point.lat).put("lon", p.point.lon).put("memo", p.memo).put("visited", p.visited)) }}
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}

data class SearchHit(val name: String, val address: String, val point: GeoPoint)
data class PlaceDraft(val name: String = "", val subtitle: String = "", val latitude: String = "", val longitude: String = "", val memo: String = "", val link: String = "", val editingId: String? = null) {
    val point: GeoPoint? get() = latitude.toDoubleOrNull()?.let { lat -> longitude.toDoubleOrNull()?.let { GeoPoint(lat, it) } }
}

class HopViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PlaceStore(app)
    private val prefs = app.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val writeLock = Mutex()
    private val searchService = PlaceSearch(app)
    var rail by mutableStateOf<RailData?>(null); private set
    var places by mutableStateOf<List<Place>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var failure by mutableStateOf<String?>(null); private set
    var tab by mutableStateOf(prefs.getInt("tab", 0)); private set
    var selectedId by mutableStateOf(prefs.getString("selected", "merlion")); private set
    var candidateId by mutableStateOf<String?>(null)
    var stationId by mutableStateOf(prefs.getString("station", "TE18").orEmpty()); private set
    var radius by mutableIntStateOf(prefs.getInt("radius", 800)); private set
    var theme by mutableStateOf(prefs.getString("theme", "system").orEmpty()); private set
    var detailedMap by mutableStateOf(prefs.getBoolean("streetMapV3", true)); private set
    var showRail by mutableStateOf(prefs.getBoolean("showRail", true)); private set
    var showSaved by mutableStateOf(prefs.getBoolean("showSaved", true)); private set
    var preview by mutableStateOf<Place?>(null); private set
    var stationPreview by mutableStateOf<Station?>(null); private set
    var mapQuery by mutableStateOf("")
    var mapResults by mutableStateOf<List<Place>>(emptyList()); private set
    var mapSearching by mutableStateOf(false); private set
    var mapSearchError by mutableStateOf<String?>(null); private set
    var mapCategory by mutableStateOf<PlaceCategory?>(null); private set
    var mapResultsVersion by mutableIntStateOf(0); private set
    var searchHistory by mutableStateOf(prefs.getString("mapSearchHistory", "").orEmpty().split('\n').filter { it.isNotBlank() }); private set
    private var mapSearchJob: Job? = null
    private var reverseJob: Job? = null
    var expanded by mutableStateOf(false)
    var query by mutableStateOf("")
    var visitFilter by mutableIntStateOf(0)
    var draft by mutableStateOf<PlaceDraft?>(null)
    var draftError by mutableStateOf<String?>(null)
    var travelLinkId by mutableStateOf<String?>(null)
    var linkedTravelResult by mutableStateOf<Pair<String,Place>?>(null)
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
    var busy by mutableStateOf(false); private set
    var picking by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var importReview by mutableStateOf<ImportReview?>(null); private set
    var importLoading by mutableStateOf(false); private set
    var importSaving by mutableStateOf(false); private set
    var importEditingId by mutableStateOf<String?>(null); private set
    var importNotice by mutableStateOf<String?>(null); private set
    private var resolveJob: Job? = null
    val selected: Place? get() = preview ?: places.find { it.id == selectedId }
    val groupStation: Station? get() = rail?.stations?.find { it.id == stationId } ?: rail?.stations?.firstOrNull { it.name == "Maxwell" }
    init { reload() }
    fun reload() { viewModelScope.launch {
        loading = true; failure = null
        runCatching { withContext(Dispatchers.IO) { loadRail(getApplication()) to store.read() } }
            .onSuccess { (r, p) -> rail = r; places = p }
            .onFailure { failure = "저장 데이터를 열지 못했어요. 기존 파일은 유지됩니다." }
        loading = false
    }}
    fun changeTab(value: Int) { tab = value; prefs.edit().putInt("tab", value).apply() }
    fun select(place: Place) { reverseJob?.cancel(); stationPreview=null; preview=place.takeUnless { p -> places.any { it.id==p.id } }; selectedId = place.id; candidateId = null; expanded = false; changeTab(0); prefs.edit().putString("selected", if(preview==null) place.id else "").apply() }
    fun clearSelection() { reverseJob?.cancel(); preview=null; stationPreview=null; selectedId = null; candidateId = null; expanded = false; prefs.edit().putString("selected", "").apply() }
    fun inspectStation(station:Station) { clearSelection(); stationPreview=station }
    fun chooseStation(station: Station) { stationId = station.id; prefs.edit().putString("station", station.id).apply(); changeTab(2) }
    fun changeRadius(value: Int) { radius = value; prefs.edit().putInt("radius", value).apply() }
    fun changeTheme(value: String) { theme = value; prefs.edit().putString("theme", value).apply() }
    fun changeMap() { detailedMap = !detailedMap; prefs.edit().putBoolean("streetMapV3", detailedMap).apply() }
    fun toggleRail() { showRail=!showRail; prefs.edit().putBoolean("showRail",showRail).apply() }
    fun toggleSaved() { showSaved=!showSaved; prefs.edit().putBoolean("showSaved",showSaved).apply() }
    private fun mutate(action: (List<Place>) -> List<Place>, done: () -> Unit = {}) { viewModelScope.launch { writeLock.withLock {
        val next = action(places)
        runCatching { withContext(Dispatchers.IO) { store.write(next) } }
            .onSuccess { places = next; done() }
            .onFailure { busy = false; message = "저장하지 못했어요. 다시 시도해 주세요." }
    }}}
    fun toggleVisited(place: Place) = mutate({ list -> list.map { if(it.id == place.id) it.copy(visited = !it.visited) else it } })
    fun delete(place: Place, done: () -> Unit) = mutate({ it.filterNot { p -> p.id == place.id } }) { if(selectedId == place.id) clearSelection(); done() }
    fun restore(place: Place) = mutate({ if(it.any { p -> p.id == place.id }) it else it + place })
    fun beginAdd(shared: String = "", point: GeoPoint? = null) {
        travelLinkId=null
        importEditingId = null
        resolveJob?.cancel(); busy = false; hits = emptyList(); draftError = null; picking = false
        draft = PlaceDraft(link = shared, latitude = point?.lat?.let { "%.6f".format(Locale.US, it) }.orEmpty(), longitude = point?.lon?.let { "%.6f".format(Locale.US, it) }.orEmpty())
        if(shared.isNotBlank()) resolveShared()
    }
    fun edit(place: Place) { closeDraft(); draft = PlaceDraft(place.name, place.subtitle, place.point.lat.toString(), place.point.lon.toString(), place.memo, editingId = place.id.takeIf { id -> places.any { it.id==id } }) }
    fun closeDraft() { resolveJob?.cancel(); busy = false; draft = null; picking = false; hits = emptyList(); draftError = null; importEditingId = null; travelLinkId=null }
    fun picked(point: GeoPoint) { draft = (draft ?: PlaceDraft()).copy(latitude = "%.6f".format(Locale.US, point.lat), longitude = "%.6f".format(Locale.US, point.lon)); picking = false }
    fun chooseHit(hit: SearchHit) { draft = (draft ?: PlaceDraft()).copy(name = if(importEditingId != null) draft?.name.orEmpty().ifBlank { hit.name } else hit.name, subtitle = hit.address, latitude = hit.point.lat.toString(), longitude = hit.point.lon.toString()); hits = emptyList(); draftError = null }
    fun searchName(name: String) {
        if(name.isBlank()) { draftError = "찾을 장소 이름을 입력해 주세요."; return }
        resolveJob?.cancel(); resolveJob = viewModelScope.launch {
            busy = true; draftError = null; hits = emptyList()
            try {
                hits = geocode(name)
                if(hits.isEmpty()) draftError = "검색 결과가 없어요. 영문 이름으로 검색하거나 지도에서 핀을 지정해 주세요."
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { draftError = "온라인 검색을 사용할 수 없어요. 좌표 입력이나 지도 핀은 계속 사용할 수 있어요." }
            finally { busy = false }
        }
    }
    @Suppress("DEPRECATION")
    private suspend fun geocode(name: String): List<SearchHit> = withContext(Dispatchers.IO) {
        if(!Geocoder.isPresent()) return@withContext emptyList()
        Geocoder(getApplication(), Locale.ENGLISH).getFromLocationName(name, 6, 1.15, 103.59, 1.49, 104.12).orEmpty()
            .filter { GeoPoint(it.latitude, it.longitude).inSingapore }
            .map { SearchHit(it.featureName ?: name, it.getAddressLine(0).orEmpty(), GeoPoint(it.latitude, it.longitude)) }
    }
    fun resolveShared() {
        val text = draft?.link.orEmpty()
        if(text.isBlank()) { draftError = "Google 지도 링크 또는 좌표를 붙여넣어 주세요."; return }
        resolveJob?.cancel(); resolveJob = viewModelScope.launch {
            busy = true; draftError = null; hits = emptyList()
            try {
                val sharedUrl = SharedLocation.url(text)
                if(sharedUrl != null && !SharedLocation.allowed(sharedUrl)) { draftError = "Google 지도 링크를 사용해 주세요. 다른 링크는 열지 않았어요."; return@launch }
                if(text.contains("/maps/placelists") || text.contains("/maps/d/")) { draftError = "목록 전체는 ‘Google 지도 즐겨찾기 가져오기’에서 Takeout ZIP·CSV 파일로 추가해 주세요."; return@launch }
                var expandedUrl = text
                if(SharedLocation.point(text) == null && sharedUrl != null) expandedUrl = expandMapsUrl(sharedUrl)
                val point = SharedLocation.point(expandedUrl)
                val name = (if(importEditingId != null) draft?.name.orEmpty() else "").ifBlank { SharedLocation.name(expandedUrl) }.ifBlank { SharedLocation.name(text) }
                draft = draft?.copy(name = name.ifBlank { draft?.name.orEmpty() })
                if(point != null) {
                    if(!point.inSingapore) { draftError = "싱가포르 범위 밖의 위치예요."; return@launch }
                    draft = draft?.copy(latitude = point.lat.toString(), longitude = point.lon.toString())
                } else if(name.isNotBlank() && !name.startsWith("http")) {
                    hits = geocode(name)
                    if(hits.isEmpty()) draftError = "좌표를 확인하지 못했어요. 지도에서 핀을 지정해 주세요."
                } else draftError = "이 링크에서 장소 좌표를 확인하지 못했어요. 장소명 검색이나 지도 핀으로 확인해 주세요."
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { draftError = "링크를 확인하지 못했어요. 인터넷 연결을 확인하거나 지도에서 위치를 지정해 주세요." }
            finally { busy = false }
        }
    }
    private suspend fun expandMapsUrl(start: String): String = withContext(Dispatchers.IO) {
        var current = start
        repeat(6) {
            check(SharedLocation.allowed(current))
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false; connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "hop-android/0.1 (Singapore travel planner)")
            try {
                val code = connection.responseCode
                if(code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return@withContext current
                    val next = URI(current).resolve(location).toString()
                    check(SharedLocation.allowed(next)); current = next
                } else return@withContext current
            } finally { connection.disconnect() }
        }
        current
    }
    fun saveDraft() {
        if(busy) return
        val d = draft ?: return; val p = d.point
        if(d.name.isBlank()) { draftError = "장소 이름을 입력해 주세요."; return }
        if(p == null || !p.inSingapore) { draftError = "싱가포르 안의 위치를 확인해 주세요."; return }
        importEditingId?.let { id ->
            importReview = importReview?.let { review -> review.copy(entries = review.entries.map { if(it.id == id) it.copy(name = d.name.trim(), point = p, memo = d.memo, selected = true) else it }) }
            closeDraft(); return
        }
        val duplicate = places.find { it.id != d.editingId && distanceMetres(it.point, p) < 15 && it.name.equals(d.name.trim(), true) }
        val linkId=travelLinkId
        if(duplicate != null) { closeDraft(); select(duplicate); if(linkId!=null) linkedTravelResult=linkId to duplicate; message = "이미 저장한 장소예요."; return }
        val place = Place(d.editingId ?: UUID.randomUUID().toString(), d.name.trim(), d.subtitle, p, d.memo, places.find { it.id == d.editingId }?.visited ?: false)
        busy = true
        mutate({ list -> if(d.editingId != null) list.map { if(it.id == d.editingId) place else it } else list + place }) { closeDraft(); select(place); if(linkId!=null) linkedTravelResult=linkId to place; message = "${place.name} 저장 완료" }
    }
    fun importPlaces(items: List<Place>, done: (Int, Int) -> Unit) {
        var added = 0; var duplicates = 0
        mutate({ old -> old.toMutableList().also { list -> items.forEach { item ->
            if(list.any { distanceMetres(it.point, item.point) < 15 && it.name.equals(item.name, true) }) duplicates++ else { list += item; added++ }
        } } }) { done(added, duplicates) }
    }
    fun searchMap(center:GeoPoint) {
        val query=mapQuery.trim(); if(query.isBlank()) return
        mapSearchJob?.cancel(); clearSelection(); mapCategory=null; mapResults=emptyList()
        searchHistory=(listOf(query)+searchHistory).distinct().take(8); prefs.edit().putString("mapSearchHistory",searchHistory.joinToString("\n")).apply()
        mapSearchJob=viewModelScope.launch {
            mapSearching=true; mapSearchError=null
            try {
                mapResults=try { searchService.search(query,center) } catch(e:CancellationException) { throw e } catch(_:Exception) { geocode(query).mapIndexed { i,h -> Place("geocoder-$i",h.name,h.address,h.point) } }
                if(mapResults.isEmpty()) mapSearchError="온라인 결과가 없어요. 영문 이름·주소로 검색하거나 지도를 길게 눌러 위치를 선택해 주세요."
                else mapResultsVersion++
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mapSearchError="온라인 검색에 연결하지 못했어요. 저장한 장소와 MRT 역 검색은 계속 사용할 수 있어요." }
            finally { if(currentCoroutineContext().isActive) mapSearching=false }
        }
    }
    fun searchNearby(category:PlaceCategory,center:GeoPoint) {
        mapSearchJob?.cancel(); clearSelection(); mapCategory=category; mapResults=emptyList()
        mapSearchJob=viewModelScope.launch {
            mapSearching=true; mapSearchError=null
            try {
                mapResults=searchService.nearby(category,center)
                if(mapResults.isEmpty()) mapSearchError="이 지역 2.5km 안에 표시할 ${category.title} 결과가 없어요. 지도를 옮겨 다시 찾아보세요."
                else mapResultsVersion++
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mapSearchError="주변 장소를 불러오지 못했어요. 인터넷 연결을 확인하고 다시 찾아보세요." }
            finally { if(currentCoroutineContext().isActive) mapSearching=false }
        }
    }
    fun clearMapResults() { mapSearchJob?.cancel(); mapSearching=false; mapResults=emptyList(); mapCategory=null; mapSearchError=null }
    fun clearSearchHistory() { searchHistory=emptyList(); prefs.edit().remove("mapSearchHistory").apply() }
    fun previewPoint(point:GeoPoint) {
        val p=Place("pin-${point.lat}-${point.lon}","지도에서 선택한 위치","%.5f, %.5f".format(Locale.US,point.lat,point.lon),point)
        select(p)
        reverseJob=viewModelScope.launch {
            try {
                val hit=searchService.reverse(point).firstOrNull()
                if(preview?.id==p.id && hit!=null) preview=p.copy(name=if(distanceMetres(hit.point,point)<30) hit.name else p.name,subtitle=hit.subtitle.ifBlank { p.subtitle })
            } catch(e:CancellationException) { throw e } catch(_:Exception) { /* Pin coordinates remain usable offline. */ }
        }
    }
    fun loadImport(uri: android.net.Uri) {
        if(importLoading || importSaving) return
        viewModelScope.launch {
            importLoading = true; importNotice = null
            try {
                importReview = withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)?.use(::readImport) ?: error("파일을 열지 못했어요.") }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message = e.message ?: "파일 형식을 확인해 주세요." }
            finally { importLoading = false }
        }
    }
    fun toggleImport(id: String) { importReview = importReview?.let { r -> r.copy(entries = r.entries.map { if(it.id == id && it.ready) it.copy(selected = !it.selected) else it }) } }
    fun closeImport() { if(!importSaving) importReview = null }
    fun editImport(entry: ImportEntry) {
        closeDraft(); importEditingId = entry.id
        draft = PlaceDraft(name = entry.name, subtitle = entry.collection, latitude = entry.point?.lat?.toString().orEmpty(), longitude = entry.point?.lon?.toString().orEmpty(), memo = entry.memo, link = entry.url)
        if(!entry.ready) {
            if(entry.url.isNotBlank() && SharedLocation.allowed(entry.url)) resolveShared()
            else if(entry.name.isNotBlank()) searchName(entry.name)
        }
    }
    fun commitImport() {
        val selected = importReview?.selected.orEmpty(); if(selected.isEmpty() || importSaving) return
        importSaving = true
        viewModelScope.launch { writeLock.withLock {
            val next = places.toMutableList(); var added = 0
            selected.forEach { entry -> val item = entry.place()
                if(next.none { distanceMetres(it.point, item.point) < 15 && it.name.equals(item.name, true) }) { next += item; added++ }
            }
            try {
                withContext(Dispatchers.IO) { store.write(next) }; places = next
                // Unresolved and deselected entries remain available for correction.
                val remaining = importReview?.entries.orEmpty().filterNot { entry -> selected.any { it.id == entry.id } }
                importReview = if(remaining.isEmpty()) null else importReview?.copy(entries = remaining)
                changeTab(1); importNotice = "${added}곳 저장 · 중복 ${selected.size - added}곳 제외"
                if(importReview == null) message = importNotice
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { importNotice = "저장하지 못했어요. 다시 시도해 주세요." }
            finally { importSaving = false }
        } }
    }
}
