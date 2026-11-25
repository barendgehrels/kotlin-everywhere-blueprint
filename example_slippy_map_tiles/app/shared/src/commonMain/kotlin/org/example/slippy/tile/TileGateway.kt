package org.example.slippy.tile

import org.example.slippy.foundation.NetworkClient
import org.example.slippy.foundation.SessionStore

interface TileGateway {
    /** The tile as a PNG (512 pixels square); a session is needed, a guest's too. */
    suspend fun tile(tile: Tile): Result<ByteArray>
}

class CloudTileGateway(private val session: SessionStore) : TileGateway {
    override suspend fun tile(tile: Tile): Result<ByteArray> = session.withIdToken { idToken ->
        NetworkClient.getBytes(tilePath(tile), idToken)
    }
}
