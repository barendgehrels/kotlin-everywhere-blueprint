package org.example.slippy.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TileFormatTest {
    @Test
    fun decimalsArePaddedAndRounded() {
        assertEquals("52.370200", formatDecimal(52.3702, 6))
        assertEquals("-0.500000", formatDecimal(-0.5, 6))
        assertEquals("0.000000", formatDecimal(-0.0000001, 6))   // no "-0"
        assertEquals("180.000000", formatDecimal(179.9999999, 6))
    }

    @Test
    fun coordinateFieldHasNoTrailingZeros() {
        assertEquals("52.3702", formatCoordinate(52.3702))
        assertEquals("5", formatCoordinate(5.0))
    }

    @Test
    fun decimalCommaIsAccepted() {
        assertEquals(52.5, parseDecimal(" 52,5 "))
        assertNull(parseDecimal("NaN"))
        assertNull(parseDecimal("north"))
    }
}
