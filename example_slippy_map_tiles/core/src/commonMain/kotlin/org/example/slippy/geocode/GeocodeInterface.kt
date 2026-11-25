package org.example.slippy.geocode

import kotlinx.serialization.Serializable

const val PLACE_NAME_MAX_LENGTH = 100

/** POST /geocode: a place name to its most probable location (AI, verified users only). */
@Serializable
data class GeocodeRequest(val placeName: String)

/** [name] is the place that was found, such as "Paris, France". */
@Serializable
data class GeocodeResponse(val lat: Double, val lon: Double, val name: String)

fun isPlaceNameValid(placeName: String): Boolean =
    placeName.isNotBlank() && placeName.length <= PLACE_NAME_MAX_LENGTH
