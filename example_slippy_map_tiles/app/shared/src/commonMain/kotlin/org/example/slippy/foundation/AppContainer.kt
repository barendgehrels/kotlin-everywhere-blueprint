package org.example.slippy.foundation

import kotlinx.coroutines.CoroutineScope
import org.example.slippy.account.CloudAccountGateway
import org.example.slippy.geocode.CloudGeocodeGateway
import org.example.slippy.tile.CloudTileGateway

/** All wiring, in one place. The scope is the app view model's, so the stores live as long as the app. */
class AppContainer(scope: CoroutineScope) {
    val localStorage = LocalStorage(platformStore())
    val messages = MessageStore()
    val session = SessionStore(CloudSessionGateway(), messages, localStorage, scope, ::humanCheckToken)
    val accounts = CloudAccountGateway(session)
    val tiles = CloudTileGateway(session)
    val geocode = CloudGeocodeGateway(session)
}
