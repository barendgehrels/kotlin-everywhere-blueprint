# Slippy

A small Kotlin Multiplatform app (Android, iOS, desktop, web) with its own Kotlin service on
Cloudflare, built from the [blueprints](..) to test them. What it does: [specs.md](specs.md).
How to set it up: [SETUP.md](SETUP.md).

| Module | What |
|---|---|
| `core` | the contract of app and service: transfer types, error codes, validation, the tile math |
| `app/shared` | the Compose Multiplatform app |
| `app/androidApp`, `app/desktopApp`, `app/webApp`, `app/iosApp` | the entry points per platform |
| `service` | the Cloudflare Worker, Kotlin/JS |

## Tests

```
./gradlew :core:allTests :app:shared:allTests :service:jsTest -Pcloud_service_url=http://localhost:8787
```
