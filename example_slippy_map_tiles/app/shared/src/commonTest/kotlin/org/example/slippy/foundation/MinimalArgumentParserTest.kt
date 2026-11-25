package org.example.slippy.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MinimalArgumentParserTest {
    @Test
    fun singleAndDoubleDashBothWork() {
        assertEquals("dark", MinimalArgumentParser(listOf("--theme", "dark")).value("theme"))
        assertEquals("light", MinimalArgumentParser(listOf("/path/app", "-theme", "light")).value("theme"))
    }

    @Test
    fun missingOptionIsNull() {
        assertNull(MinimalArgumentParser(listOf("-theme")).value("theme"))
        assertNull(darkThemeOf(MinimalArgumentParser(emptyList()).value("theme")))
    }

    @Test
    fun themeNamesMapToDarkOrLight() {
        assertEquals(true, darkThemeOf("dark"))
        assertEquals(false, darkThemeOf("light"))
        assertNull(darkThemeOf("system"))
    }
}
