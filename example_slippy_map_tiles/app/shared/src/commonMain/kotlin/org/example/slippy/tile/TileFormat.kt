package org.example.slippy.tile

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** The decimals of a coordinate as the screen shows it: about 0.1 m. */
const val COORDINATE_DECIMALS = 6

/** The decimals of the dot's place in the tile: below a pixel of the 512-pixel tile. */
const val FRACTION_DECIMALS = 3

fun roundTo(value: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return round(value * factor) / factor
}

/** [value] with exactly [decimals] decimals, without the platform's formatting (there is none in common code). */
fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = round(abs(value) * factor).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val unit = factor.toLong()
    val whole = scaled / unit
    if (decimals == 0) return "$sign$whole"
    val fraction = (scaled % unit).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}

/** As [formatDecimal], without trailing zeros: for a field the user may edit. */
fun formatCoordinate(value: Double): String =
    formatDecimal(value, COORDINATE_DECIMALS).trimEnd('0').trimEnd('.')

/** A number as typed: a decimal comma is accepted too. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun parseWholeNumber(text: String): Int? = text.trim().toIntOrNull()
