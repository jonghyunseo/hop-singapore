package app.hop.singapore

import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TravelKind(val label:String) {
    PLACE("장소"), FOOD("맛집"), STAY("숙소"), BOOKING("예약"), GUIDE("가이드·목록"), NOTE("메모")
}
data class TravelItem(
    val id:String, val title:String, val content:String="", val url:String="", val kind:TravelKind=TravelKind.NOTE,
    val date:String="", val time:String="", val note:String="", val pinned:Boolean=false, val done:Boolean=false,
    val placeId:String="", val createdAt:Long=System.currentTimeMillis()
)
object TravelShare {
    fun url(text:String):String = Regex("https?://[^\\s<>]+",RegexOption.IGNORE_CASE).findAll(text)
        .map { it.value.trimEnd('.',',',')',']',';', '"') }.firstOrNull(::safeUrl).orEmpty()
    fun safeUrl(value:String):Boolean = runCatching {
        val u=URI(value); u.scheme.lowercase() in setOf("https","http") && !u.host.isNullOrBlank() && u.userInfo==null && u.port in listOf(-1,80,443)
    }.getOrDefault(false)
    fun host(value:String):String = runCatching { URI(value).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    fun isMaps(value:String)=SharedLocation.allowed(value) && (host(value).startsWith("maps.") || value.contains("/maps"))
    fun isList(value:String)=isMaps(value) && (value.contains("/placelists") || value.contains("/maps/d/") || value.contains("/maps/@") && value.contains("!2s"))
    fun source(value:String)=when {
        value.isBlank()->"직접 메모"
        isMaps(value)->"Google 지도"
        host(value).endsWith("instagram.com")->"Instagram"
        host(value).endsWith("naver.com")->"네이버"
        else->host(value)
    }
    fun key(item:TravelItem):String {
        if(item.url.isBlank()) return "text:"+item.content.trim().ifBlank { item.title.trim() }
        val u=URI(item.url)
        val query=u.rawQuery?.split('&')?.filterNot { it.substringBefore('=').lowercase().let { k->k.startsWith("utm_") || k in setOf("gclid","fbclid") } }?.joinToString("&").orEmpty()
        return "url:${u.scheme.lowercase()}://${u.rawAuthority.lowercase()}${u.rawPath.orEmpty()}"+(if(query.isNotBlank()) "?$query" else "")+(u.rawFragment?.let { "#$it" } ?: "")
    }
    fun capture(text:String,subject:String=""):TravelItem {
        val body=text.trim().take(16000); val link=url(body)
        val plain=body.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("http",true) }.orEmpty()
        val name=subject.trim().ifBlank { if(link.contains("/maps/place/")) SharedLocation.name(link) else plain }.take(160)
        val kind=when { isList(link)->TravelKind.GUIDE; isMaps(link)->TravelKind.PLACE; listOf("booking.com","agoda.com","airbnb.","klook.com","trip.com").any { host(link).contains(it) }->TravelKind.BOOKING; link.isNotBlank()->TravelKind.GUIDE; else->TravelKind.NOTE }
        return TravelItem("",name.ifBlank { if(isList(link)) "Google 지도 목록" else if(link.isNotBlank()) "${source(link)} 링크" else "여행 메모" },body,link,kind)
    }
}
fun travelDateError(item:TravelItem):String? = when {
    item.title.isBlank()->"제목을 입력해 주세요."
    item.url.isNotBlank() && !TravelShare.safeUrl(item.url)->"http 또는 https 웹 링크를 입력해 주세요."
    item.date.isNotBlank() && runCatching { LocalDate.parse(item.date) }.isFailure->"날짜를 다시 선택해 주세요."
    item.time.isNotBlank() && (item.date.isBlank() || !Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(item.time))->"날짜를 선택하고 시간을 09:30 형식으로 입력해 주세요."
    else->null
}
fun travelDayLabel(date:String):String = if(date.isBlank()) "날짜 미정" else runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN)) }.getOrDefault(date)
fun singaporeToday()=LocalDate.now(ZoneId.of("Asia/Singapore")).toString()
fun scheduleItems(items:List<TravelItem>)=items.sortedWith(compareBy<TravelItem> { it.date.ifBlank { "9999-12-31" } }.thenBy { it.time.ifBlank { "99:99" } }.thenBy { it.createdAt })
