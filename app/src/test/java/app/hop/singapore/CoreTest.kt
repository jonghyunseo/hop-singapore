package app.hop.singapore

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private val merlion=GeoPoint(1.2867834,103.85450295)
    @Test fun distanceAndStationRanking() {
        assertEquals(0.0,distanceMetres(merlion,merlion),0.001)
        val raffles=Station("NS26-EW14","Raffles Place",GeoPoint(1.28393,103.85152),listOf("NS26","EW14"))
        val esplanade=Station("CC3","Esplanade",GeoPoint(1.293258,103.855974),listOf("CC3"))
        val result=nearest(merlion,listOf(esplanade,raffles,raffles))
        assertEquals(2,result.size); assertEquals(raffles,result.first().station)
        assertTrue(result.first().metres in 420.0..480.0)
        assertTrue(walkingUrl(raffles,starterPlaces.first()).contains("origin=1.28393,103.85152"))
    }
    @Test fun groupRadiusUsesOnlySavedPlaces() {
        val maxwell=Station("TE18","Maxwell",GeoPoint(1.28055,103.844),listOf("TE18"))
        assertEquals(2,within(maxwell,starterPlaces,400).size)
        assertEquals(3,within(maxwell,starterPlaces,800).size)
        assertEquals(0,within(maxwell,emptyList(),800).size)
    }
    @Test fun sharedCoordinatesDoNotUseMapCameraPosition() {
        assertEquals(GeoPoint(1.2894095,103.8631218),SharedLocation.point("https://www.google.com/maps/place/Flyer/@1.2,103.7,15z/data=!3d1.2894095!4d103.8631218"))
        assertNull(SharedLocation.point("https://www.google.com/maps/place/Flyer/@1.2,103.7,15z"))
        assertEquals(GeoPoint(1.28,103.85),SharedLocation.point("https://maps.google.com/?q=1.28%2C103.85"))
        assertEquals(GeoPoint(1.28,103.85),SharedLocation.point("1.28, 103.85"))
    }
    @Test fun sharedHostsAndSingaporeBounds() {
        assertTrue(SharedLocation.allowed("https://maps.app.goo.gl/abc"))
        assertFalse(SharedLocation.allowed("https://maps.app.goo.gl.evil.example/abc"))
        assertFalse(SharedLocation.allowed("http://maps.google.com/?q=1,2"))
        assertFalse(SharedLocation.allowed("https://127.0.0.1/maps"))
        assertFalse(GeoPoint(Double.NaN,103.8).inSingapore)
        assertFalse(GeoPoint(37.5,127.0).inSingapore)
        assertTrue(merlion.inSingapore)
    }
}
