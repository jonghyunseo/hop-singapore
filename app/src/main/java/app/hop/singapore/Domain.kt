package app.hop.singapore

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.*

data class GeoPoint(val lat: Double, val lon: Double) {
    val inSingapore: Boolean get() = lat.isFinite() && lon.isFinite() && lat in 1.15..1.49 && lon in 103.59..104.12
}
data class Station(val id: String, val name: String, val point: GeoPoint, val codes: List<String>)
data class Place(val id: String, val name: String, val subtitle: String, val point: GeoPoint, val memo: String = "", val visited: Boolean = false)
data class Candidate(val station: Station, val metres: Double)
data class MapFeature(val kind: String, val name: String, val paths: List<List<GeoPoint>>, val polygon: Boolean)
data class RailData(val stations: List<Station>, val features: List<MapFeature>)

fun distanceMetres(a: GeoPoint, b: GeoPoint): Double {
    val r = Math.PI / 180
    val h = (sin((b.lat - a.lat) * r / 2).pow(2) + cos(a.lat * r) * cos(b.lat * r) * sin((b.lon - a.lon) * r / 2).pow(2)).coerceIn(0.0, 1.0)
    return 12_742_017.6 * atan2(sqrt(h), sqrt(1 - h))
}
fun nearest(point: GeoPoint, stations: List<Station>) = stations.distinctBy { it.id }.map { Candidate(it, distanceMetres(point, it.point)) }.sortedBy { it.metres }.take(3)
fun within(station: Station, places: List<Place>, radius: Int) = places.map { it to distanceMetres(station.point, it.point) }.filter { it.second <= radius }.sortedBy { it.second }
fun formatDistance(m: Double) = if (m >= 1000) String.format(Locale.US, "%.2fkm", m / 1000) else "${(m / 10).roundToInt() * 10}m"
fun walkingUrl(station: Station, place: Place) = "https://www.google.com/maps/dir/?api=1&origin=${station.point.lat},${station.point.lon}&destination=${place.point.lat},${place.point.lon}&travelmode=walking"

object SharedLocation {
    private val coordinate = "(-?\\d{1,3}(?:\\.\\d+)?)"
    fun decode(value: String): String = runCatching { URLDecoder.decode(value.replace("+", "%20"), "UTF-8") }.getOrDefault(value)
    fun point(text: String): GeoPoint? {
        val value = decode(text)
        // Camera coordinates (@lat,lon) in a place URL are not necessarily the place itself.
        val patterns = listOf("!3d$coordinate!4d$coordinate", "[?&](?:q|query|ll)=$coordinate,\\s*$coordinate", "^geo:$coordinate,\\s*$coordinate", "^\\s*$coordinate,\\s*$coordinate\\s*$")
        for (pattern in patterns) {
            val m = Regex(pattern, RegexOption.IGNORE_CASE).find(value) ?: continue
            val p = GeoPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
            if (p.lat in -90.0..90.0 && p.lon in -180.0..180.0) return p
        }
        return null
    }
    fun url(text: String) = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')')
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url); val h = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (h in setOf("maps.app.goo.gl", "maps.google.com", "www.google.com", "google.com", "www.google.com.sg", "maps.google.com.sg") || (h == "goo.gl" && uri.path.startsWith("/maps")))
    }.getOrDefault(false)
    fun name(text: String): String {
        val decoded = decode(text)
        return Regex("/maps/place/([^/]+)").find(decoded)?.groupValues?.get(1)?.replace('+', ' ')
            ?: text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("https://") }?.take(150).orEmpty()
    }
}

val starterPlaces = listOf(
    Place("merlion", "멀라이언", "Merlion Fountain", GeoPoint(1.2867834, 103.85450295)),
    Place("supertree", "가든스 · 슈퍼트리", "Supertree Grove", GeoPoint(1.282394, 103.8634541)),
    Place("haji", "하지 레인", "Haji Lane", GeoPoint(1.3012265, 103.858734)),
    Place("maxwell", "맥스웰 푸드센터", "Maxwell Food Centre", GeoPoint(1.28048093, 103.84463229)),
    Place("buddha", "불아사", "Buddha Tooth Relic Temple", GeoPoint(1.28144536, 103.84434064)),
    Place("lau", "라우파삿", "Lau Pa Sat", GeoPoint(1.28068077, 103.85046946))
)
