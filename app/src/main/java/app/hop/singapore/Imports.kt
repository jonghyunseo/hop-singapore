package app.hop.singapore

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportEntry(
    val id: String = UUID.randomUUID().toString(), val name: String, val point: GeoPoint?,
    val memo: String = "", val url: String = "", val collection: String = "",
    val selected: Boolean = point?.inSingapore == true && name.isNotBlank()
) {
    val ready get() = point?.inSingapore == true && name.isNotBlank()
    fun place() = Place(id, name.trim(), collection.ifBlank { "Google 지도에서 가져옴" }, requireNotNull(point), memo)
}
data class ImportReview(val entries: List<ImportEntry>, val ignoredFiles: Int = 0) {
    val selected get() = entries.filter { it.ready && it.selected }
    val missing get() = entries.count { it.point == null || it.name.isBlank() }
    val outside get() = entries.count { it.point != null && !it.point.inSingapore }
}
private const val MAX_TEXT = 2_000_000
private const val MAX_ARCHIVE = 20_000_000
private const val MAX_ROWS = 1000
internal fun readLimited(input: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while(true) {
        val n = input.read(buffer); if(n < 0) break
        require(out.size() + n <= limit) { "파일이 너무 커요. Saved(저장됨)만 내보내 주세요. 단일 파일 2MB · ZIP 20MB까지 지원해요." }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
/** ZIP members stay in memory: no archive paths are ever written to disk. */
fun readImport(input: InputStream): ImportReview {
    val bytes = readLimited(input, MAX_ARCHIVE)
    if(bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) {
        require(bytes.size <= MAX_TEXT) { "CSV·GeoJSON 파일은 2MB 이하로 선택해 주세요." }
        return parseImport(bytes.toString(Charsets.UTF_8))
    }
    val entries = mutableListOf<ImportEntry>(); var ignored = 0; var expanded = 0; var files = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while(true) {
            val member = zip.nextEntry ?: break
            require(++files <= 2000) { "파일이 너무 많아요. Saved(저장됨)만 내보내 주세요." }
            if(member.isDirectory) continue
            val content = readLimited(zip, MAX_ARCHIVE - expanded); expanded += content.size
            val filename = member.name.substringAfterLast('/').substringAfterLast('\\')
            val ext = filename.substringAfterLast('.', "").lowercase()
            if(ext !in listOf("csv", "json", "geojson")) { ignored++; continue }
            require(content.size <= MAX_TEXT) { "${filename.take(80)}: 2MB 이하 파일만 가져올 수 있어요." }
            val parsed = try { parseImport(content.toString(Charsets.UTF_8), filename.substringBeforeLast('.')) }
                catch(_: UnsupportedImport) { ignored++; continue }
            entries += parsed.entries
            require(entries.size <= MAX_ROWS) { "한 번에 1,000곳까지 가져올 수 있어요. 목록별 CSV를 선택해 주세요." }
        }
    }
    require(entries.isNotEmpty()) { "장소 목록을 찾지 못했어요. Takeout에서 Saved(저장됨)를 선택해 주세요." }
    return ImportReview(entries, ignored)
}
private class UnsupportedImport : IllegalArgumentException("장소 목록 형식이 아니에요. Google Takeout의 Saved CSV 또는 장소 GeoJSON을 선택해 주세요.")
fun parseImport(text: String, collection: String = ""): ImportReview {
    require(text.length <= MAX_TEXT) { "2MB 이하 파일을 선택해 주세요." }
    val entries = mutableListOf<ImportEntry>()
    fun add(name: String, point: GeoPoint?, memo: String, url: String) {
        entries += ImportEntry(name = name.trim(), point = point, memo = memo, url = url, collection = collection)
        require(entries.size <= MAX_ROWS) { "한 번에 1,000곳까지 가져올 수 있어요." }
    }
    val trimmed = text.trimStart('\uFEFF',' ','\n','\r','\t')
    if(trimmed.startsWith("{")) {
        val features = JSONObject(trimmed).optJSONArray("features") ?: throw UnsupportedImport()
        require(features.length() <= MAX_ROWS) { "한 번에 1,000곳까지 가져올 수 있어요." }
        for(i in 0 until features.length()) {
            val f = features.getJSONObject(i); val p = f.optJSONObject("properties") ?: JSONObject(); val g = f.optJSONObject("geometry")
            val location = p.optJSONObject("Location")
            val name = p.optString("name").ifBlank { p.optString("Title") }.ifBlank { location?.optString("Business Name").orEmpty() }
            val url = p.optString("Google Maps URL").ifBlank { p.optString("url") }
            val c = if(g?.optString("type") == "Point") g.optJSONArray("coordinates") else null
            val point = if(c != null && c.length() >= 2) runCatching { GeoPoint(c.getDouble(1), c.getDouble(0)) }.getOrNull() else SharedLocation.point(url)
            add(name, point, p.optString("memo").ifBlank { p.optString("Comment") }.ifBlank { p.optString("Note") }, url)
        }
    } else {
        val rows = csvRows(trimmed)
        val names = setOf("name", "title", "장소명", "제목")
        val locations = setOf("url", "google maps url", "link", "item_content_url", "latitude", "lat", "위도")
        // Google CSVs can start with a collection description and a blank line.
        val headerAt = rows.indexOfFirst { row -> row.map { it.trim().lowercase() }.let { h -> h.any { it in names } && h.any { it in locations } } }
        if(headerAt < 0) throw UnsupportedImport()
        val headers = rows[headerAt].map { it.trim().lowercase() }
        fun List<String>.field(vararg keys: String): String = getOrNull(headers.indexOfFirst { it in keys }).orEmpty().trim()
        rows.drop(headerAt + 1).filter { row -> row.any { it.isNotBlank() } }.forEach { row ->
            val lat = row.field("latitude", "lat", "위도").toDoubleOrNull(); val lon = row.field("longitude", "lon", "lng", "경도").toDoubleOrNull()
            val url = row.field("url", "google maps url", "link", "item_content_url")
            val point = if(lat != null && lon != null) GeoPoint(lat, lon) else SharedLocation.point(url)
            add(row.field("name", "title", "장소명", "제목"), point, row.field("memo", "note", "comment", "메모"), url)
        }
    }
    require(entries.isNotEmpty()) { "목록에 저장된 장소가 없어요." }
    return ImportReview(entries)
}
private fun csvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quote = false; var i = 0
    while(i < text.length) {
        val c = text[i]
        when {
            c == '"' && quote && text.getOrNull(i+1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quote = !quote
            c == ',' && !quote -> { row.add(cell.toString()); cell.clear() }
            c == '\n' && !quote -> { row.add(cell.toString().trimEnd('\r')); rows.add(row); row = mutableListOf(); cell.clear() }
            else -> cell.append(c)
        }; i++
    }
    require(!quote) { "CSV의 따옴표 형식을 확인해 주세요." }
    if(cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString().trimEnd('\r')); rows.add(row) }
    return rows
}
