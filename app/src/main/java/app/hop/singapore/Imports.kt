package app.hop.singapore

import org.json.JSONObject
import java.util.UUID

data class ImportReview(val places: List<Place>, val missing: Int, val outside: Int, val total: Int)
fun parseImport(text: String): ImportReview {
    require(text.length <= 2_000_000) { "2MB 이하 파일을 선택해 주세요." }
    val places = mutableListOf<Place>(); var missing = 0; var outside = 0; var total = 0
    fun add(name: String, point: GeoPoint?, memo: String = "") {
        total++
        if(point == null || name.isBlank()) missing++ else if(!point.inSingapore) outside++
        else places += Place(UUID.randomUUID().toString(),name.trim(),"가져온 장소",point,memo)
    }
    val trimmed = text.trimStart('\uFEFF',' ','\n','\r','\t')
    if(trimmed.startsWith("{")) {
        val features = JSONObject(trimmed).getJSONArray("features")
        require(features.length() <= 1000) { "한 번에 1,000곳까지 가져올 수 있어요." }
        for(i in 0 until features.length()) {
            val f = features.getJSONObject(i); val p = f.optJSONObject("properties") ?: JSONObject(); val g = f.optJSONObject("geometry")
            val location = p.optJSONObject("Location")
            val name = p.optString("name").ifBlank { p.optString("Title") }.ifBlank { location?.optString("Business Name").orEmpty() }
            val c = if(g?.optString("type")=="Point") g.optJSONArray("coordinates") else null
            val point = if(c != null && c.length()>=2) runCatching { GeoPoint(c.getDouble(1),c.getDouble(0)) }.getOrNull() else SharedLocation.point(p.optString("Google Maps URL"))
            add(name,point,p.optString("memo").ifBlank { p.optString("Comment") })
        }
    } else {
        val rows = csvRows(trimmed); require(rows.isNotEmpty()) { "파일이 비어 있어요." }; require(rows.size <= 1001) { "한 번에 1,000곳까지 가져올 수 있어요." }
        val headers=rows.first().map { it.trim().lowercase() }
        fun List<String>.field(vararg keys: String): String { val index = headers.indexOfFirst { it in keys }; return getOrNull(index).orEmpty() }
        rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.forEach { row ->
            val lat = row.field("latitude","lat","위도").toDoubleOrNull(); val lon = row.field("longitude","lon","lng","경도").toDoubleOrNull()
            val point = if(lat != null && lon != null) GeoPoint(lat,lon) else SharedLocation.point(row.field("url","google maps url","link"))
            add(row.field("name","title","장소명"),point,row.field("memo","note","comment","메모"))
        }
    }
    return ImportReview(places,missing,outside,total)
}
private fun csvRows(text: String): List<List<String>> {
    val rows=mutableListOf<List<String>>(); var row=mutableListOf<String>(); val cell=StringBuilder(); var quote=false; var i=0
    while(i<text.length) {
        val c=text[i]
        when {
            c=='"' && quote && text.getOrNull(i+1)=='"' -> { cell.append('"'); i++ }
            c=='"' -> quote=!quote
            c==',' && !quote -> { row.add(cell.toString()); cell.clear() }
            c=='\n' && !quote -> { row.add(cell.toString().trimEnd('\r')); rows.add(row); row=mutableListOf(); cell.clear() }
            else -> cell.append(c)
        }; i++
    }
    require(!quote) { "CSV의 따옴표 형식을 확인해 주세요." }
    if(cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString().trimEnd('\r')); rows.add(row) }
    return rows
}
