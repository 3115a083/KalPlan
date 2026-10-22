package cc.stkmn.kalplan.extraction

import org.junit.Assert.*
import org.junit.Test

class LocationHeuristicExtractorTest {
    private val extractor = LocationHeuristicExtractor()

    @Test fun completeAddressHasNoNestedPostalOnlyDuplicate() {
        val results = extractor.candidates("Bitte kommen Sie zur Musterstraße 12, 44135 Dortmund.")
        assertEquals(1, results.size)
        assertTrue(results.single().value.contains("Musterstraße 12"))
        assertTrue(results.single().value.contains("44135 Dortmund"))
        assertEquals(1, extractor.candidates("Musterstraße 12, 44135 Dortmund\nOrt: Dortmund\nMusterstraße 12").size)
    }

    @Test fun adjacentStreetAndPostalCityRemainOneAddress() {
        val results = extractor.candidates("Ort: Musterstraße 12\n44135 Dortmund")
        assertEquals(1, results.size)
        assertTrue(results.single().value.contains("Musterstraße 12"))
        assertTrue(results.single().value.contains("44135 Dortmund"))
        assertEquals("Ort: Musterstraße 12\n44135 Dortmund", results.single().evidence)
    }

    @Test fun farApartFragmentsAreNeverJoinedIntoAnInventedAddress() {
        val results = extractor.candidates("Treffpunkt: Musterstraße 12\nBitte melden.\n\nRechnung: 10115 Berlin")
        assertEquals(2, results.size)
        assertFalse(results.any { it.value.contains("Musterstraße") && it.value.contains("Berlin") })
    }

    @Test fun separateCompleteAddressesRemainSeparateCandidates() {
        val results = extractor.candidates("Musterstraße 12, 44135 Dortmund\n\nTestweg 8, 10115 Berlin")
        assertEquals(2, results.size)
        assertTrue(results.any { it.value.contains("Dortmund") && !it.value.contains("Berlin") })
        assertTrue(results.any { it.value.contains("Berlin") && !it.value.contains("Dortmund") })
    }

    @Test fun billingHeaderPreventsAdjacentFragmentsBeingCrossPaired() {
        val results = extractor.candidates("Termin: Musterstraße 12\nRechnungsadresse: 10115 Berlin")
        assertEquals(2, results.size)
        assertFalse(results.any { it.value.contains("Musterstraße") && it.value.contains("Berlin") })
    }

    @Test fun explicitCityAndWeakCityHintRemainLocalAndEvidenceIsBounded() {
        assertEquals("Mönchengladbach", extractor.extract("Ort: Mönchengladbach")?.value)
        assertEquals("Dortmund", extractor.extract("Wir treffen uns in Dortmund.")?.value?.trimEnd('.'))
        assertTrue(extractor.candidates("Musterstraße 12, 44135 Dortmund " + "x".repeat(1000)).all { it.evidence.length <= 240 })
    }
}
