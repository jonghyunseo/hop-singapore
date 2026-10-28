package app.hop.singapore

import org.junit.Assert.*
import org.junit.Test

class MapActionTest {
    @Test fun directionsDistinguishCurrentLocationFromChosenStation() {
        val destination=Place("test","Gallery","",GeoPoint(1.2902,103.8514))
        val station=GeoPoint(1.2839,103.8515)
        val current=directionUrl(destination,"walking")
        assertTrue(current.contains("destination=1.2902,103.8514"))
        assertFalse(current.contains("origin="))
        assertTrue(directionUrl(destination,"transit",station).endsWith("&origin=1.2839,103.8515"))
        assertTrue(mapPlaceUrl(destination).contains("query=1.2902,103.8514"))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidDirectionsModeIsRejected() {
        directionUrl(Place("test","Test","",GeoPoint(1.28,103.85)),"unknown")
    }
}
