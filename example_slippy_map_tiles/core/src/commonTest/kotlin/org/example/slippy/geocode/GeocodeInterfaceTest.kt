package org.example.slippy.geocode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeocodeInterfaceTest {
    @Test
    fun placeNameNotBlankAndNotTooLong() {
        assertTrue(isPlaceNameValid("Eiffel Tower"))
        assertFalse(isPlaceNameValid("   "))
        assertFalse(isPlaceNameValid("x".repeat(PLACE_NAME_MAX_LENGTH + 1)))
    }
}
