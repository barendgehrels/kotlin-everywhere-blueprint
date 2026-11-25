package org.example.slippy.claude

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiJsonTest {
    @Serializable
    data class Point(val lat: Double, val lon: Double)

    private fun decode(text: String) = decodeAiJsonOrNull(text, Point.serializer())

    @Test
    fun rawObjectDecodes() = assertEquals(Point(1.0, 2.0), decode("""{"lat": 1, "lon": 2}"""))

    @Test
    fun codeFenceAndRemarkAreIgnored() =
        assertEquals(Point(1.0, 2.0), decode("Sure!\n```json\n{\"lat\": 1, \"lon\": 2}\n```\nHope this helps."))

    @Test
    fun lastValidBlockWins() =
        assertEquals(Point(3.0, 4.0), decode("""{"lat": 1, "lon": 2} or rather {"lat": 3, "lon": 4}"""))

    @Test
    fun invalidLastBlockFallsBackToAnEarlierOne() =
        assertEquals(Point(1.0, 2.0), decode("""{"lat": 1, "lon": 2} and {"note": "not a point"}"""))

    @Test
    fun bracesInsideStringsDoNotSplitTheBlock() =
        assertEquals(listOf("""{"a": "}{", "b": {"c": 1}}"""), jsonObjectBlocks("""x {"a": "}{", "b": {"c": 1}} y"""))

    @Test
    fun cutOffAnswerIsNull() = assertNull(decode("""{"lat": 1, "lon": """))

    @Test
    fun noJsonIsNull() = assertNull(decode("I don't know that place."))
}
