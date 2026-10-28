package app.hop.singapore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class PlaceCategory(val title:String,val tag:String) {
    FOOD("음식점","amenity:restaurant"), CAFE("카페","amenity:cafe"), ATTRACTION("관광지","tourism:attraction"), SHOP("쇼핑","shop:mall"), HOTEL("숙소","tourism:hotel")
}
/** Only explicit search/tap actions use the public Photon service. No background autocomplete. */
class PlaceSearch(context:Context) {
    private val directory=File(context.cacheDir,"place-search").apply { mkdirs() }
    private val client=OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build()
    private val lock=Mutex(); private var lastRequest=0L
    suspend fun search(query:String,center:GeoPoint)=request("api",mapOf("q" to query,"bbox" to "103.59,1.15,104.12,1.49","lat" to center.lat.toString(),"lon" to center.lon.toString(),"limit" to "15","lang" to "en"))
    suspend fun nearby(category:PlaceCategory,center:GeoPoint)=request("reverse",mapOf("lat" to center.lat.toString(),"lon" to center.lon.toString(),"radius" to "2.5","osm_tag" to category.tag,"limit" to "20","lang" to "en"))
        .filter { distanceMetres(it.point,center)<=2500 }.sortedBy { distanceMetres(it.point,center) }
    suspend fun reverse(point:GeoPoint)=request("reverse",mapOf("lat" to point.lat.toString(),"lon" to point.lon.toString(),"radius" to "0.1","limit" to "3","lang" to "en"))
    private suspend fun request(path:String,params:Map<String,String>):List<Place> = withContext(Dispatchers.IO) { lock.withLock {
        val url=HttpUrl.Builder().scheme("https").host("photon.komoot.io").addPathSegment(path).addPathSegment("").apply { params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val key=MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val cache=File(directory,key)
        if(cache.exists() && System.currentTimeMillis()-cache.lastModified()<24*3600_000L) return@withLock parseSearchPlaces(cache.readText())
        delay((1000-(android.os.SystemClock.elapsedRealtime()-lastRequest)).coerceAtLeast(0))
        lastRequest=android.os.SystemClock.elapsedRealtime()
        val req=Request.Builder().url(url).header("User-Agent","hop-singapore/0.3 (+https://github.com/jonghyunseo/hop-singapore)").build()
        client.newCall(req).execute().use { response ->
            check(response.isSuccessful) { "장소 검색 연결이 원활하지 않아요. 잠시 뒤 다시 검색해 주세요." }
            val body=response.body?.string() ?: error("검색 결과를 받지 못했어요.")
            val results=parseSearchPlaces(body)
            cache.writeText(body)
            directory.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(100).forEach { it.delete() }
            results
        }
    } }
}
fun parseSearchPlaces(text:String):List<Place> {
    val features=JSONObject(text).getJSONArray("features")
    return (0 until features.length()).mapNotNull { i ->
        val f=features.getJSONObject(i); val p=f.optJSONObject("properties") ?: return@mapNotNull null
        val c=f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
        if(c.length()<2) return@mapNotNull null
        val point=GeoPoint(c.optDouble(1),c.optDouble(0)); if(!point.inSingapore) return@mapNotNull null
        val name=p.optString("name").ifBlank { p.optString("street") }; if(name.isBlank()) return@mapNotNull null
        val address=listOf(p.optString("housenumber"),p.optString("street"),p.optString("district"),p.optString("city"),p.optString("postcode")).filter { it.isNotBlank() }.distinct().joinToString(" ")
        Place("osm-${p.optString("osm_type")}-${p.optString("osm_id",i.toString())}",name,address,point)
    }.distinctBy { it.id }
}
fun mapPlaceUrl(place:Place)="https://www.google.com/maps/search/?api=1&query=${place.point.lat},${place.point.lon}"
fun directionUrl(place:Place,mode:String,origin:GeoPoint?=null):String {
    require(mode in listOf("walking","transit","driving"))
    return "https://www.google.com/maps/dir/?api=1&destination=${place.point.lat},${place.point.lon}&travelmode=$mode"+(origin?.let { "&origin=${it.lat},${it.lon}" } ?: "")
}
