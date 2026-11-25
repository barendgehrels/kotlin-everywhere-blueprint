This is a mini Kotlin Multiplatform app, "slippy" (package `org.example.slippy`), deployed on
Android, iOS, desktop and the web (Wasm). Its service is hosted on Cloudflare and fully written
in Kotlin.

Its purpose is to test the blueprints in the folder above, in this same repo, all of them:
including Firebase authentication, registration (with the verification email) and guests.

## The UI

* Enter lat, long and level of detail (zoom 0–22), then view the slippy map tile.
* Or vice versa: enter a slippy map tile (z, x, y), then view its lat/long boundaries.
* Or enter a place name, and use AI to convert it to lat, long. The result fills in the lat,
  long fields.

One tile is shown, as a PNG, with the attribution "© MapTiler © OpenStreetMap contributors".
The tile is decoded with Compose's `decodeToImageBitmap`, without an external image library
(no Coil).

The last input is remembered in `LocalStorage`.

## Accounts

Full accounts and guests, as in the blueprints: log in, register (with the verification email),
guest (with the human check), and guest to registered user.

* Tiles: guests too (`requireUser(allowGuest = true)`), with the daily guest tile quota.
* Place name to lat, long (AI): verified users only (`requireVerifiedUserId`).

The web app is its own worker (`app/webApp/wrangler.toml`), apart from the service, and offers
the Android APK as a download.

## The service

* It stores `MAPTILER_API_KEY` as a secret, and is a proxy to MapTiler's raster tiles
  (`maps/streets-v2/{z}/{x}/{y}.png`). The tile endpoint is a GET, authenticated like every
  other call (`NetworkClient.getBytes` in the app). No caching.
* It stores `ANTHROPIC_API_KEY` as a secret, and asks Claude (the model is the var
  `ANTHROPIC_MODEL`) to convert a place name to lat, long. Claude answers with the most probable
  place.

## The math

The conversions, and the validation of lat, long, z, x and y, live in `core`, with unit tests.
Latitude is clamped to ±85.0511. See:

https://mapmath.dev/blog/lat-lon-to-tile-coordinates

## Out of scope

The smoke test, and a custom domain.

