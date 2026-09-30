package com.lumen.launcher.data

import org.junit.Assert.*
import org.junit.Test

class TravelAttachmentsTest {
    @Test fun oldCollectionsKeepTheirCategories() {
        val old = """[{"uri":"content://one","title":"Pass","mime":"application/pdf"},{"uri":"content://two","title":"Plan","itinerary":true}]"""
        assertEquals(listOf(TravelCategory.Flights, TravelCategory.Itinerary), TravelAttachments.decode(old).map { it.travelCategory })
    }
    @Test fun categoriesAndAppShortcutsSurviveReload() {
        val items = TravelCategory.entries.map { TravelAttachment("rail.app/MainActivity", "Rail tickets", "application/x-lumen-app", category = it) }
        assertEquals(items, TravelAttachments.decode(TravelAttachments.encode(items)))
        assertEquals(5, TravelAttachments.merge(items, items).size)
    }
    @Test fun legacyAndNewItineraryItemsDeduplicateTogether() {
        val old = TravelAttachment("content://plan", "Plan", "application/pdf", true)
        val newer = old.copy(itinerary = false, category = TravelCategory.Itinerary)
        assertEquals(1, TravelAttachments.merge(listOf(old), listOf(newer)).size)
    }
    @Test fun preservesLegacyTicket() { assertEquals("content://ticket/1", TravelAttachments.decode(null, "content://ticket/1").single().uri) }
    @Test fun removingLastItemDoesNotResurrectLegacyTicket() { assertTrue(TravelAttachments.decode("[]", "content://ticket/1").isEmpty()) }
    @Test fun roundTripPreservesBothCollectionsAndSpecialCharacters() {
        val items = listOf(TravelAttachment("content://file/1", "Ana's ticket | 1", "application/pdf"), TravelAttachment("https://example.com/?a=1&b=2", "Family plan", "text/uri-list", true))
        assertEquals(items, TravelAttachments.decode(TravelAttachments.encode(items)))
    }
    @Test fun deduplicatesWithinCollectionButAllowsSameFileInBoth() {
        val doc = TravelAttachment("content://file/1", "Ticket", "application/pdf")
        assertEquals(2, TravelAttachments.merge(listOf(doc), listOf(doc, doc.copy(itinerary = true))).size)
    }
    @Test fun validatesWebLinks() {
        assertEquals("https://example.com/plan", TravelAttachments.webLink("example.com/plan"))
        assertNull(TravelAttachments.webLink("javascript:alert(1)"))
        assertNull(TravelAttachments.webLink("file:///local/file"))
        assertNull(TravelAttachments.webLink("https://user:password@example.com"))
        assertNull(TravelAttachments.webLink(""))
    }
}
