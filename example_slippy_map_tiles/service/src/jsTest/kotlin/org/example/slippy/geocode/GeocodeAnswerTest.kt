package org.example.slippy.geocode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeocodeAnswerTest {
    @Test
    fun validAnswerPassesWithTrimmedName() =
        assertEquals(GeocodeResponse(48.8566, 2.3522, "Paris, France"),
            checkGeocodeAnswer(GeocodeAnswer(48.8566, 2.3522, "  Paris, France ")))

    @Test
    fun latitudeOutOfRangeIsUnusable() = assertNull(checkGeocodeAnswer(GeocodeAnswer(91.0, 0.0, "X")))

    @Test
    fun longitudeOutOfRangeIsUnusable() = assertNull(checkGeocodeAnswer(GeocodeAnswer(0.0, -180.5, "X")))

    @Test
    fun notANumberIsUnusable() = assertNull(checkGeocodeAnswer(GeocodeAnswer(Double.NaN, 0.0, "X")))

    @Test
    fun blankNameIsUnusable() = assertNull(checkGeocodeAnswer(GeocodeAnswer(0.0, 0.0, "  ")))

    @Test
    fun overlongNameIsUnusable() =
        assertNull(checkGeocodeAnswer(GeocodeAnswer(0.0, 0.0, "a".repeat(GEOCODE_NAME_MAX_LENGTH + 1))))

    @Test
    fun edgesOfTheRangesAreValid() =
        assertEquals(GeocodeResponse(-90.0, 180.0, "Pole"), checkGeocodeAnswer(GeocodeAnswer(-90.0, 180.0, "Pole")))

    @Test
    fun userTextIsOnlyInTheLabeledUserMessage() {
        assertEquals("Place: 'Utrecht'", geocodeUserPrompt(" Utrecht "))
        assertTrue(GEOCODE_SYSTEM_PROMPT.endsWith("Return ONLY a raw JSON object, no markdown."))
    }
}
