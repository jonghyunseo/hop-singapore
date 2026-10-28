package app.hop.singapore

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportTest {
    @Test fun googleDescriptionAndQuotedNotesKeepUnresolvedPlaces() {
        val csv = "\uFEFFSingapore trip\r\n\r\nTitle,Note,URL\r\n\"Coffee, stop\",\"try cake\nwith tea\",https://www.google.com/maps/place/Coffee/data=!4m2!3m1!1s123\r\nFlyer,evening,\"https://www.google.com/maps/search/?api=1&query=1.2894,103.8631\""
        val r = parseImport(csv)
        assertEquals(2, r.entries.size)
        assertEquals("Coffee, stop", r.entries[0].name)
        assertEquals("try cake\nwith tea", r.entries[0].memo)
        assertEquals(1, r.missing)
        assertEquals(listOf("Flyer"), r.selected.map { it.name })
    }
    @Test fun noCameraCoordinateOrForeignPlaceIsSelected() {
        val r = parseImport("title,item_content_url\nUnknown,\"https://www.google.com/maps/@1.28,103.85,16z\"\nTokyo,\"https://www.google.com/maps/?q=35.68,139.76\"")
        assertEquals(2, r.entries.size)
        assertEquals(1, r.missing)
        assertEquals(1, r.outside)
        assertTrue(r.selected.isEmpty())
    }
    @Test fun zipCombinesListsWithoutExtractingPaths() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            mapOf("Takeout/Saved/Favorites.csv" to "Title,Note,URL\nMerlion,note,\"https://www.google.com/maps/?q=1.2868,103.8545\"", "../Want to go.csv" to "Title,URL\nMaxwell,https://www.google.com/maps/place/Maxwell", "archive.html" to "ignored").forEach { (name,text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
        val r = readImport(out.toByteArray().inputStream())
        assertEquals(2, r.entries.size)
        assertEquals(1, r.ignoredFiles)
        assertEquals("Favorites", r.entries[0].collection)
        assertEquals("Want to go", r.entries[1].collection)
        assertEquals(1, r.selected.size)
    }
}
