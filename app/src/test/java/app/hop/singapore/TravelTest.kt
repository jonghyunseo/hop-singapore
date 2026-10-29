package app.hop.singapore

import org.junit.Assert.*
import org.junit.Test

class TravelTest {
    @Test fun sharedListAndReservationRemainUsableWithoutCoordinates() {
        val list=TravelShare.capture("https://www.google.com/maps/placelists/list/example","싱가포르 맛집 모음")
        assertEquals("싱가포르 맛집 모음",list.title)
        assertEquals(TravelKind.GUIDE,list.kind)
        assertTrue(TravelShare.isList(list.url))
        assertNull(travelDateError(list))
        assertEquals(TravelKind.BOOKING,TravelShare.capture("https://www.booking.com/hotel/sg/example.html","숙소 예약 확인").kind)
        assertEquals("공항에서 유심 찾기",TravelShare.capture("공항에서 유심 찾기").title)
    }
    @Test fun deduplicationKeepsMeaningfulQueryAndRejectsUnsafeSchemes() {
        val a=TravelShare.capture("https://example.com/guide?id=17&utm_source=share")
        val b=TravelShare.capture("https://example.com/guide?id=17")
        assertEquals(TravelShare.key(a),TravelShare.key(b))
        assertNotEquals(TravelShare.key(a),TravelShare.key(TravelShare.capture("https://example.com/guide?id=18")))
        assertFalse(TravelShare.safeUrl("javascript:alert(1)"))
        assertFalse(TravelShare.safeUrl("https://user:pass@example.com"))
        assertFalse(TravelShare.safeUrl("file:///sdcard/private"))
        assertEquals("",TravelShare.capture("javascript:alert(1)").url)
    }
    @Test fun localScheduleValidatesAndPutsUndatedItemsLast() {
        val later=TravelItem("b","호텔",date="2026-10-30",time="15:00")
        val earlier=TravelItem("a","점심",date="2026-10-30",time="12:00")
        val undated=TravelItem("c","언젠가 가기")
        assertEquals(listOf("a","b","c"),scheduleItems(listOf(undated,later,earlier)).map { it.id })
        assertNotNull(travelDateError(later.copy(time="25:00")))
        assertNotNull(travelDateError(later.copy(date="")))
        assertNull(travelDateError(earlier))
    }
}
