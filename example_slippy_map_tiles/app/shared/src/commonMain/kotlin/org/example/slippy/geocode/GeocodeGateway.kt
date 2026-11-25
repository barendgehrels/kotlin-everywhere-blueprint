package org.example.slippy.geocode

import org.example.slippy.foundation.NetworkClient
import org.example.slippy.foundation.SessionStore
import org.example.slippy.tile.LatLon

/** A place that was found: [name] as the service names it, such as "Paris, France". */
data class Place(val latLon: LatLon, val name: String)

interface GeocodeGateway {
    /** A place name to its most probable location (AI; registered users with a verified email). */
    suspend fun geocode(placeName: String): Result<Place>
}

class CloudGeocodeGateway(private val session: SessionStore) : GeocodeGateway {
    override suspend fun geocode(placeName: String): Result<Place> = session.withIdToken { idToken ->
        NetworkClient.postJson<GeocodeRequest, GeocodeResponse>("geocode", GeocodeRequest(placeName), idToken)
            .map { Place(LatLon(it.lat, it.lon), it.name) }
    }
}
