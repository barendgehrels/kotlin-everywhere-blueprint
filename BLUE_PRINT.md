# Kotlin everywhere: the blueprint

This is the blueprint for Kotlin Multiplatform projects with a Compose Multiplatform app
(Android, iOS, web and desktop) and a Kotlin/JS Cloudflare Worker as their service. It describes the rules and the patterns, without one project's features.

Two more documents build on it:

- [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md): guests without an account, the human check,
  rate limits, and the APK download from the web app.
- [BLUE_PRINT_DOMAIN.md](BLUE_PRINT_DOMAIN.md): moving the worker from its `workers.dev`
  address to a domain of its own.

The reference implementation is [Babel Magic](https://babelmagic.uk), a language-learning app
on all four platforms with its service on Cloudflare (closed source).

## Start with the wizard

A new project always starts from JetBrains' Kotlin Multiplatform wizard: in Android Studio
(File → New → New Project → Kotlin Multiplatform, with the Kotlin Multiplatform plugin) or at
[kmp.jetbrains.com](https://kmp.jetbrains.com). Don't write the Gradle files or the Xcode
project by hand.

- **Choose:** Android, iOS (with the UI shared in Compose), Desktop and Web. Also Server: that
  gives the module shared between the apps and a server, which becomes `core`.
- **The wizard gives:** the Gradle setup with the version catalog, the Android app module, the
  desktop and web entry points, and the Xcode project in `iosApp`, with its build phase that
  calls `./gradlew :app:shared:embedAndSignAppleFrameworkForXcode` (the path of the shared
  module). It builds and runs on every platform as it is, and Android Studio knows it: its run
  configurations, previews and iOS run work without more setup.
- **Then, in this order:**
  1. Build and run it once, on each platform, before changing anything.
  2. Move and rename the modules to the layout below, if the wizard named them otherwise
     (its names differ between versions), and change the paths in `settings.gradle.kts` and in
     the Xcode build phase with them.
  3. Delete the wizard's server module (a Ktor server on the JVM). Add `service` by hand,
     as described under [The build](#the-build): it is the one module the wizard doesn't make.
  4. Add the dependencies and the generated config of this blueprint.
- **Never make a new `.xcodeproj`.** Change the wizard's: its bundle identifier, team and
  name in Xcode, its `Info.plist` and its `.xcconfig`.

## Project layout

```
app/shared       the Compose Multiplatform app (commonMain + platform actuals), a library
app/androidApp   the Android app: MainActivity, the manifest, signing (AGP 9 needs its own module)
app/iosApp       the Xcode project, which uses the framework "Shared" from app/shared
app/desktopApp   the desktop app: main() with a Window
app/webApp       the web app: main() with the page, index.html, and its wrangler.toml
core             what the app and the service share: transfer types, error codes, enums, validation
service          the Kotlin/JS Cloudflare Worker
service/smokeTest  a Kotlin/Native console program that calls the deployed service (optional)
```

`app/iosApp` is no Gradle module; the others are in `settings.gradle.kts`.

Inside each module, there are packages per **feature** (`settings`, `usage`,
`translate`, …) and one shared package, **`foundation`**.

## The build

### Targets

| Module | Targets |
|---|---|
| `core` and `app/shared` | `android`, `iosArm64`, `iosSimulatorArm64`, `jvm`, `js { browser() }`, `wasmJs { browser() }` |
| `service` | `js { nodejs() }` only; it uses `core`'s `js` library |

- **The web app is deployed as Wasm** (`wasmJsBrowserDistribution`, from
  `build/dist/wasmJs/productionExecutable`). The `js` target is kept for older browsers and
  for the shared web code (`webMain`), but not deployed.
- **`app/shared` and `core` are KMP libraries** with AGP 9's
  `com.android.kotlin.multiplatform.library` plugin (`android { … }` inside `kotlin { … }`).
  The Android app itself is `app/androidApp`, with `com.android.application`.
- **`app/shared` keeps `binaries.executable()` on `js` and `wasmJs`,** as the wizard made it,
  although it is a library: Compose's web tests need the bundle (CMP-4906), and find no tests
  without it.
- **No compiler flags.** On Kotlin 2.4, the explicit backing field
  (`val x: StateFlow<T>` with `field = MutableStateFlow(...)`) needs no flag.

### Dependencies

All in `gradle/libs.versions.toml`. The versions that were last checked are under
[Versions](#versions).

| Where | What | For |
|---|---|---|
| `core` | `kotlinx-serialization-json` | the transfer types |
| `app/shared` commonMain | Compose (`runtime`, `foundation`, `ui`, `material3`, `components-resources`, `ui-tooling-preview`) | the UI and its resources |
| | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` and `lifecycle-runtime-compose` | `ViewModel`, `viewModel { }` and `collectAsState` in common code |
| | Ktor client: `ktor-client-core`, `-content-negotiation`, `ktor-serialization-kotlinx-json`, `-logging` | `NetworkClient` |
| | `kotlinx-datetime`, `kotlinx-coroutines-core` | time, and coroutines |
| `app/shared` per platform | Ktor engines: `ktor-client-okhttp` (Android), `-darwin` (iOS), `-java` (desktop), `-js` (both web targets) | the HTTP calls |
| | `androidx.datastore:datastore-preferences` in `dataStoreMain` (Android, iOS, desktop) | local storage |
| | `kotlin-wrappers` `kotlin-browser` in `webMain` | `localStorage`, and the page |
| `app/webApp` | `kotlin-wrappers` `kotlin-browser` | the URL query, for the startup options |
| `service` | `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `kotlin-wrappers` `kotlin-browser` | promises, JSON, and `fetch`, `Request`, `Response` |
| tests | `kotlin-test` in `commonTest`; `kotlinx-coroutines-test` in `app/shared` | everywhere; the view model tests |

- **Ktor in the app, and not in the service.** The app needs one client on five platforms, and
  Ktor gives it, with an engine per platform. The worker already has the web's own `fetch`
  (through kotlin-wrappers); Ktor there would only add size to every cold start.
- **No DI library, no image library, no settings library:** see the principles, and local
  storage below. Compose decodes a PNG or JPEG itself (`decodeToImageBitmap`).

### The worker: from Kotlin to `index.js`

```kotlin
// service/build.gradle.kts
kotlin {
    js {
        nodejs()                                // close to the Workers runtime
        outputModuleName.set("myapp")           // a stable file name for index.js
        binaries.executable()                   // a .js file to run, not a library
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization)
            implementation(libs.wrappers.browser)
        }
    }
}
```

```kotlin
// Router.kt: the one function the runtime calls
@OptIn(ExperimentalJsExport::class)
@JsExport
fun handleRequest(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> = ...
```

```js
// service/index.js: the ES module that Cloudflare runs, written by hand
import { org } from './build/compileSync/js/main/productionExecutable/kotlin/myapp.js';

export default {
  async fetch(request, env, ctx) {
    return org.example.myapp.handleRequest(request, env, ctx);
  }
};
```

```toml
# service/wrangler.toml
name = "myapp-service-0b7c54c50eed5943"   # a random suffix: the workers.dev address is public
main = "index.js"
compatibility_date = "2026-06-16"
minify = true
```

- **The build:** `./gradlew :service:build` writes the JavaScript; `npx wrangler dev` (local) or
  `npx wrangler deploy` in `service/` bundles `index.js` with it.
- **The module kind is Kotlin/JS's default, UMD.** Don't set `useEsModules()`. A UMD module
  exports one object per top-level package (`org`), with the exported functions under their
  package path: hence `import { org }` and `org.example.myapp.handleRequest` in `index.js`.
  Wrangler's bundler (esbuild) turns the UMD module into a named import. With
  `useEsModules()` the output would export `handleRequest` itself, and `index.js` would have to
  change with it.
- **`@JsExport`** keeps the name `handleRequest` reachable, also with `minify = true`. The
  package (`org.example.myapp`) is the path in `index.js`.
- **`Router.kt` is in the root package** (`org.example.myapp`), not in `foundation` with the
  rest of the router's helpers: exported from there, the path would be
  `org.example.myapp.foundation.handleRequest`.
- **No `main()`.** The module only exports `handleRequest`.
- **The file:** `:service:build` writes
  `service/build/compileSync/js/main/productionExecutable/kotlin/<outputModuleName>.js`, next
  to the libraries it uses. Check that path once after the first build: `index.js` imports it.

### The web app: one worker or two

The web app is static files, served by Cloudflare as a worker's `[assets]`. Either:

- **Its own worker** (`app/webApp/wrangler.toml`, with only `[assets]`), deployed apart from the
  service. The service then answers CORS for the web app's origin (see
  [Configuration](#configuration-vars-secrets-and-cors)).
- **The service's own `[assets]`**, one worker for both; on the same host there is no CORS. See
  [BLUE_PRINT_DOMAIN.md](BLUE_PRINT_DOMAIN.md).

### The generated config of the app

The app knows its service's URL and its version at compile time. A small Gradle task in
`app/shared` writes them as Kotlin, into a source folder of `commonMain`:

```kotlin
// app/shared/build.gradle.kts
// secret.properties, in the root, in .gitignore: cloud_service_url=https://...
// Read only when the property is not given; null when it is missing or cannot be read.
fun secretProperty(name: String): String? = runCatching {
    Properties().apply { rootProject.file("secret.properties").inputStream().use { load(it) } }
        .getProperty(name)
}.getOrNull()

val generatedFolder = "generated/kotlin/config"
val cloudServiceUrl: String? = providers.gradleProperty("cloud_service_url").orNull   // -P wins
    ?: secretProperty("cloud_service_url")

val generateConfig = tasks.register("generateConfig") {
    val url = cloudServiceUrl
    val content = """
        package generated

        internal object AppConfig {
            const val CLOUD_SERVICE_URL: String = "$url"
            const val VERSION_NAME: String = "$versionName"
        }
    """.trimIndent()
    val file = layout.buildDirectory.file("$generatedFolder/AppConfig.kt")
    inputs.property("content", content)
    outputs.file(file)
    doLast {
        if (url == null) throw GradleException("cloud_service_url: not in secret.properties, and no -P")
        file.get().asFile.apply { parentFile.mkdirs(); writeText(content) }
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(layout.buildDirectory.dir(generatedFolder)) }
tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(generateConfig) }
```

- **No plugin** (no BuildKonfig): a task of a few lines, which only runs when the content changed.
- **`-P` first, the file second, and a missing URL fails only this task.** So a build with
  `-Pcloud_service_url` needs no `secret.properties` (CI, a sandboxed agent), and a build of
  another module (`:service:build`) never needs either. Plain values, not a chain of
  `Provider.orElse(provider { … })`: that evaluates the fallback and breaks the configuration
  cache.
- **`Configuration.csUrl`** is `reachableUrl(AppConfig.CLOUD_SERVICE_URL)`.
- **`-Pcloud_service_url=http://localhost:8787`** runs the app against a local `wrangler dev`.
  The native apps refuse plain http unless they are told:
  - **Android:** the `INTERNET` permission (which every Android app with a service needs), and a
    `networkSecurityConfig` that allows cleartext to `10.0.2.2` and `localhost` only.
  - **iOS:** `NSAppTransportSecurity` → `NSAllowsLocalNetworking` in `Info.plist`.
- **iOS needs its version below Kotlin too** (in `Info.plist`): a second task writes it into an
  `.xcconfig` for Xcode, which the wizard's `Config.xcconfig` includes with `#include?
  "Version.xcconfig"`. Xcode reads the `.xcconfig` files before its Gradle build phase runs, so a
  new version shows one build later. Keep the generated file in git, so a fresh clone builds.

## Principles

1. **No DI framework.** There is no Koin and no Hilt. All wiring is manual and in one place, the `AppContainer`.
   Dependencies come in through constructors.
2. **One direction of data:**
   - **Down:** state goes down to composables as plain values.
   - **Up:** events go up as actions.
   - **Out:** the network is reached only through gateways.
3. **Every class has one job.**
   - A store holds shared state.
   - A gateway talks to the service.
   - A view model holds one screen's state and logic.
   - A composable draws.
4. **Failures are values in the app, and exceptions in the service.**
   - **App:** gateways return `Result<T>`, and view models fold them into state or messages.
   - **Service:** handlers throw `ServiceException`, and the router turns it into the response, in one place.
5. **One contract.** The transfer types and error codes live in `core`, so both
   sides compile against the same definitions. They are the contract between **our** app and
   **our** service; an external API's format stays in the service (see [Core](#core)).
6. **Be honest about layers.** Add a layer only when it does something. When a layer
   is too thin to justify, say so and leave it out.

## Names

| Use | For | Not |
|---|---|---|
| `…Gateway` (interface) and `Cloud…Gateway` | the app's access to the service | `Repository`, `Service`, `Client` |
| `…Store` | app-wide state shared between screens | `Holder`, `Manager` |
| `…ViewModel` | one screen's state and logic | |
| `…UiState` | a view model's state, as a data class | |
| `…Action` | a sealed interface of everything the user can do on a screen | `Event`, `Intent` |
| `…Screen` | the stateful composable, which collects the flows | `Root` |
| `…Content` | the stateless composable, which takes values | |
| `…Preview` | the preview of a content, with sample data | |
| `foundation` | the shared package | `common`, `util` |

"Service" is the worker only.

---

## The app

### The container and its owner

```kotlin
class AppContainer(scope: CoroutineScope) {
    val messages = MessageStore()
    val session  = SessionStore(CloudSessionGateway(), messages, scope)
    val settings = SettingsStore(...)
    val usage    = CloudUsageGateway(session)
    // one gateway per feature
}

class AppViewModel : ViewModel() {               // named after the app
    val appContainer = AppContainer(viewModelScope)
    val activeScreen: StateFlow<Screen>
        field = MutableStateFlow(Screen.Home)    // the app's first screen
    fun setActiveScreen(v: Screen) { activeScreen.value = v }
}
```

- **`App()`** gets the container from its view model and hands each screen what that screen needs.
- **Screens and view models never see the container.** They get the stores and gateways themselves.
- **The scope:** the container uses the top-level view model's `viewModelScope`, so stores live as long as the app.

### Stores: shared state

A store is a plain class: not a `ViewModel` and not a singleton. Its parts:

- a `StateFlow` with an explicit backing field (`field = MutableStateFlow(...)`);
- setters that update the state at once and persist it in the background (in `LocalStorage` or through a gateway);
- an `init` that restores the persisted state.

The standard stores:

- **`MessageStore`** holds a queue of `UserMessage(id, text, resId: StringResource?, isError)`.
  - `show(text)`, `show(resId)` and `show(throwable)` add a message; `shown(message)` removes it.
  - `App` shows the first message in a snackbar, styled by `isError`, then calls `shown`.
  - See https://developer.android.com/topic/architecture/ui-layer/events.
- **`SessionStore`** holds the `Session(idToken, refreshToken, emailVerified)`, with
  `validIdToken()` and `setSession(session?)`.
  - It restores the session at startup, and refreshes the token when a call needs it (see
    below).
  - **Only the service's session-expired code logs the user out.** Being offline or a 5xx only shows a message.
- **`SettingsStore`** holds the user's preferences.
  - It updates at once and saves in the background.
  - When settings are kept by the service, the service's answer is the truth: a failed save loads the stored settings again.

Don't make a "common view model" for shared state: shared state goes in a store.

### The session token: refreshed when a call needs it, not on a timer

A Firebase ID token is valid for an hour. A loop with `delay(50.minutes)` looks enough, but
a timer does not run reliably: in a browser tab in the background, after a laptop slept, or
with an Android app in the background, it fires too late. The token has expired meanwhile,
and the next call gets a 401 ("Token has expired").

The token itself says when it expires: it is a JWT, with `exp` (in seconds) in its payload.
So the store checks it right before each call, and refreshes first when needed:

```kotlin
// SessionStore
private val refreshMutex = Mutex()

suspend fun validIdToken(): String? {
    val idToken = session.value.idToken ?: return null
    if (!expiresSoon(idToken)) return idToken
    refreshMutex.withLock {
        // Refreshed meanwhile, by another call
        val current = session.value.idToken ?: return null
        if (!expiresSoon(current)) return current
        refresh()
    }
    return session.value.idToken
}

// Some margin for a slow call, and for a device clock that is a bit off
private const val REFRESH_MARGIN_SECONDS = 5 * 60

private fun expiresSoon(idToken: String): Boolean {
    val expiry = runCatching {
        val payload = base64Url.decode(idToken.split('.')[1]).decodeToString()
        Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull
    }.getOrNull() ?: return false            // cannot be read: the service decides
    return Clock.System.now().epochSeconds + REFRESH_MARGIN_SECONDS >= expiry
}
```

- **The app reads `exp` only to know when to refresh.** It trusts nothing on it: the service
  still verifies the signature and the expiry of every token. Nothing changes for security.
- **Calls at the same time share one refresh** (the `Mutex`): the second one finds the new token.
- **A refresh that is overtaken is dropped.** When the session changed while the refresh was
  on its way (the user logged out, or logged in again), its answer is not stored.
- **The refresh at startup stays:** it also brings the email verification up to date.
- **An idle app doesn't refresh.** A refresh token that became invalid (a password
  changed elsewhere) is noticed at the next call instead: the user is logged out.
- **Known limit:** a device clock more than the margin behind sends an expired token, which
  the service refuses with a 401. Rare, and harmless; refreshing and retrying once on that 401
  would close it, but did not seem worth it.

### Gateways: the only way out

```kotlin
interface UsageGateway { suspend fun usage(): Result<Usage> }

class CloudUsageGateway(private val session: SessionStore) : UsageGateway {
    override suspend fun usage(): Result<Usage> = session.withIdToken { idToken ->
        NetworkClient.postJson<Unit, UsageResponse>("usage", idToken = idToken).map { it.usage }
    }
}

// SessionStore: the token for a call, and the one place where a call's answer logs out
suspend fun <T> withIdToken(call: suspend (idToken: String) -> Result<T>): Result<T> {
    val idToken = validIdToken() ?: return Result.failure(NotLoggedInException())
    val used = session.value
    return call(idToken).onFailure { if (isSessionExpired(it)) expire(used) }
}
```

- **One interface per feature.** The `Cloud…` implementation takes the `SessionStore` for the token.
- **Return values:** the gateway returns `Result<T>` with app types; it maps the transfer types itself.
- **Not its job:** it never touches view models, stores (other than reading the token) or UI.
- **`session.withIdToken { }`** gives the call a valid token, and fails with
  `NotLoggedInException` without a session. When the service answers `SESSION_EXPIRED`, it logs
  the user out, unless the session changed meanwhile (`expire(used)`). So a gateway still only
  reads the token, and every call logs out the same way.
- **`NetworkClient.postJson<Req, Res>(path, request?, idToken?)`** is the one generic call.
  - Both types are reified: JS and Wasm have no reflection, so `request: Any?` cannot be
    serialized there. `Req` is `Unit` without a body; `Res` is `Unit` for an answer without
    content (any body is accepted).
  - It sends a JSON body with `bearerAuth`.
  - It decodes `Res` on 2xx.
  - Otherwise it decodes core's `ErrorBody` into `ApiException(code, message, status)`.
  - It rethrows `CancellationException`.
- **`NetworkClient.getBytes(path, idToken?)`** is the other one, for the rare GET that returns
  bytes (an image, a map tile): the same bearer token and the same `ApiException` on an error,
  with the body as a `ByteArray` on 2xx.
- **Localized failures:** `ExceptionWithStringResource(resId)`, with subclasses such as `NotLoggedInException`. Make them classes, not objects.
- **The interface is the test seam.** A fake gateway makes a view model testable in `commonTest`.

### One view model per screen

```kotlin
data class UsageUiState(val usage: Usage? = null, val isOpen: Boolean = false)

sealed interface UsageAction {
    data object Open : UsageAction
    data object Close : UsageAction
}

class UsageViewModel(private val gateway: UsageGateway,
                     private val messages: MessageStore) : ViewModel() {
    val usageUiState: StateFlow<UsageUiState>
        field = MutableStateFlow(UsageUiState())

    fun onAction(action: UsageAction) = when (action) {
        UsageAction.Open -> open()
        UsageAction.Close -> close()
    }
    private fun open() { ... gateway.usage().fold(onSuccess = { ... }, onFailure = { messages.show(it) }) }
}
```

- **The state** is one `…UiState` data class, updated with `update { it.copy(...) }`.
- **The public surface** is the state flows and `onAction`. Everything else is `private`.
- **Settings** are read from the store inside an action, not copied into the view model's state.
- **Dialog and sheet state** belongs in the UiState (`passwordDialog: Mode?`, `isOpen`), not in `remember`, so it survives rotation.
- **Creation:** a default parameter of the screen creates the view model, `viewModel { UsageViewModel(gateway, messages) }`.

### The UiState holds what the screen shows, not the raw values

A `StateFlow` emits only when the new state differs from the old one (`equals`), and only then
does Compose recompose and draw. That protects the screen only if the state holds what the screen
shows. A state with exact meters and seconds, or a whole map camera, is new and unequal at every
location update (once a second) and at every camera move (every frame), while the screen looks the
same. In a map app, each of those redraws of the UI made the map beside it stutter.

- **Round in the view model, as the screen does:** a distance shown as "4.4 km" is stored as
  4,400 m, a time shown as "23 min" as 1,380 s. Use the same rounding functions as the formatting,
  so the two cannot drift apart.
- **Keep only the shown part of a fast-changing value:** the zoom level as an `Int`, not the
  camera.
- **One `update { it.copy(...) }` per event,** not one per field.
- **Test it:** two inputs that show the same screen give an equal state.

### The stateful screen, the stateless content, and its preview

```kotlin
// UsageScreen.kt
@Composable
fun UsageScreen(gateway: UsageGateway, messages: MessageStore,
                viewModel: UsageViewModel = viewModel { UsageViewModel(gateway, messages) }) {
    val uiState by viewModel.usageUiState.collectAsState()
    UsageContent(uiState, onAction = viewModel::onAction)
}

// UsageContent.kt
@Composable
fun UsageContent(uiState: UsageUiState, onAction: (UsageAction) -> Unit) { ... }

@Preview
@Composable
fun UsagePreview() = UsageContent(
    uiState = UsageUiState(usage = Usage(used = 1234, budget = 5000), isOpen = true),
    onAction = {})
```

- **The screen** collects the flows, from its view model and from stores such as session and
  settings. It stays in `XScreen.kt` (or `App.kt`).
- **The content** gets values and `onAction`. Its parts get only what they draw, and never a
  view model. It goes, with its private helpers, in `XContent.kt`.
- **No default arguments** on `XContent`, and no `@Preview` on it. A slot parameter, such as a
  composable lambda that only some callers fill, may keep `= {}`.
- **A separate preview with realistic sample data,** named after the screen (`XPreview`).
- **Guard platform calls with `LocalInspectionMode`.** A preview renders without an installed
  app, so for example Android's `PackageManager` throws. The preview then only says "Render
  problem".

  ```kotlin
  @Composable
  override fun getVersionName(): String {
      if (LocalInspectionMode.current) return "preview"
      return getPackageVersionName(LocalContext.current)
  }
  ```

### Local storage: one interface, DataStore once, `localStorage` on the web

The platforms only store text by name. Everything else is common code, once:

```kotlin
// commonMain
class StorageEntry<T>(val name: String, val serialize: (T) -> String, val deserialize: (String) -> T)

interface KeyValueStore {
    suspend fun get(name: String): String?
    suspend fun set(name: String, value: String)
}

expect fun platformStore(): KeyValueStore

class LocalStorage(private val store: KeyValueStore) {
    suspend fun <T> get(entry: StorageEntry<T>): T? = ...  // logs an unreadable store; guards deserializing
    suspend fun <T> set(entry: StorageEntry<T>, value: T) = store.set(entry.name, entry.serialize(value))
}

// AppContainer
val localStorage = LocalStorage(platformStore())
```

- **The entries are typed,** all in one `StorageEntries` object.
- **`LocalStorage` is a class, made once in the `AppContainer`,** and handed to the stores and
  view models that persist something. Not an `object` with a lazy `platformStore()`: tests would
  then write to the real store (on the desktop, in the user's home folder). Tests use a
  `MemoryKeyValueStore` of a few lines.
- **Android's store needs a `Context`** for its path. `MainActivity` hands the application
  context to the platform code once, before `App()` (`initAndroidPlatform(applicationContext)`).
- **Prefer an interface plus an `expect fun` over an `expect class` or `expect object`.**
  Expect/actual classes are still Beta, and need `-Xexpect-actual-classes`. With this design no
  project needs the flag.
- **DataStore exists once,** in `DataStoreKeyValueStore(path)`, in a source set of its own
  that Android, iOS and the desktop share. Each platform keeps only its path:
  `actual fun platformStore() = DataStoreKeyValueStore(getPath())`.
- **Such a source set needs the default hierarchy explicitly:**

  ```kotlin
  kotlin {
      applyDefaultHierarchyTemplate()   // explicitly, because we add a source set of our own
      sourceSets {
          val dataStoreMain = create("dataStoreMain") {   // `by creating` is deprecated in Gradle 9.6
              dependsOn(commonMain.get())
              dependencies { implementation(libs.androidx.datastore.preferences) }
          }
          androidMain { dependsOn(dataStoreMain); dependencies { ... } }
          iosMain { dependsOn(dataStoreMain) }
          jvmMain { dependsOn(dataStoreMain) }
      }
  }
  ```
- **The web uses the browser's `localStorage`** (kotlin-wrappers), not cookies. Cookies travel
  with every request to the site (tokens included), vanish when the browser closes, and break
  on values with `;`.
- **Reading is robust:** rethrow `CancellationException`, log a store that cannot be read, and
  catch a value that no longer deserializes (such as a renamed enum constant) separately.
- **The stored tokens stay out of backups.** On Android, exclude the DataStore file in the
  backup rules (`dataExtractionRules` and `fullBackupContent`). On iOS, keep the file in
  Application Support, with `NSURLIsExcludedFromBackupKey`, not in Documents.
- **No external library.** DataStore plus a few lines is enough; `multiplatform-settings` was
  considered and not used.

### Dark mode: the app decides, the platforms pass the choice

Don't override what the platform reports. Give the app its own notion of the theme chosen at
startup:

```kotlin
// foundation/DarkTheme.kt
val LocalDarkTheme = compositionLocalOf<Boolean?> { null }      // null follows the system

@Composable
fun isDarkTheme(): Boolean = LocalDarkTheme.current ?: isSystemInDarkTheme()

fun darkThemeOf(theme: String?): Boolean? = when (theme) { "dark" -> true; "light" -> false; else -> null }
```

- **All app code calls `isDarkTheme()`,** never `isSystemInDarkTheme()` directly.
- **`App(darkTheme: Boolean? = null)`** provides `LocalDarkTheme`. Platform code never sees a
  `CompositionLocal`.
- **It uses only public API.** No internal `SystemTheme`/`LocalSystemTheme` (made public by
  mistake, and removed since), no Android `Configuration.uiMode` override, and no JavaScript
  `matchMedia` override.

### Startup options are parameters of `App`

Options such as the theme are read in each entry point, and passed to `App`. All four
platforms use the same names. Desktop, iOS and the web share one `MinimalArgumentParser`;
Android reads its intent extras directly:

| Platform | Source | Example |
|---|---|---|
| Desktop | `main(args)` | `--theme dark --language nl` |
| Android | the intent extras | `adb shell am start ... --es theme dark` |
| iOS | `NSProcessInfo` launch arguments | `-theme dark` |
| Web | the URL query, read in Kotlin | `?theme=dark&language=nl` |

On the web, the query becomes arguments for the same parser:

```kotlin
private fun queryArguments(search: String): List<String> =
    search.removePrefix("?")
        .split("&")
        .filter { it.contains('=') }
        .flatMap { pair ->
            val (key, value) = pair.split("=", limit = 2)
            listOf("-$key", value)
        }
```

**An honest exception: the language on the web.** Compose Resources reads `navigator.languages`
itself, and there is no public API to override the locale. So a small `setLang.js`, loaded
before the app, stays. It does use the same parameter name, `?language=`. Moving it into Kotlin
would only hide the same hack in harder-to-read code.

### Foundation helpers

- **`Platform`:** expect/actual for logging, the version name and `reachableUrl(url)`. On Android, `localhost` becomes `10.0.2.2`, so the emulator reaches the development machine.
  - **iOS logs with `NSLog(message.replace("%", "%%"))`,** never `NSLog("%@", message)`.
    Kotlin/Native passes a `String` in a C vararg list as a C string, not as an `NSString`, so
    `%@` crashes, at the first line logged (in a network app often Ktor's first request).
- **`Configuration.csUrl`:** the service URL from the generated config, through `reachableUrl`.
- **`isPortraitWindow()`:** one function for every layout decision, so the navigation and the screens switch at the same moment.
- **Navigation:** a bar in portrait and a rail in landscape. The choice is by orientation, not by width: on a phone in landscape, height is what is scarce.
- **A short window scrolls.** A phone browser leaves less height than the native app, and
  landscape even less. A screen with a list that takes "the rest of the height"
  (`Modifier.weight(1f)`) gets nothing there: give the list a height of its own, with a
  minimum, and let the screen scroll.

### A GPU view beside Compose: let Compose draw with the CPU

An app that shows a continuously rendering GPU view (a map, a game, video effects in a
`SurfaceView`) next to its Compose UI has two GPU clients: that view, and Compose's RenderThread.
When the view keeps the GPU busy, every UI redraw queues behind it and makes the view stutter. That
happened even with few redraws, on the emulator and on a real tablet. Threads do not help: there is
one GPU. A renderer that draws its own UI (ImGui, in the same frame) never has the problem.

- **Switch off hardware acceleration for the activity** (Android):

  ```xml
  <activity android:name=".MainActivity" android:hardwareAccelerated="false">
  ```

  Compose then draws the window with the CPU, a few milliseconds per redraw on the main thread.
  The `SurfaceView` keeps rendering on the GPU, and the display hardware composes both.
- **The price:** shadows (elevation), blur, `RenderEffect` and some `graphicsLayer` features degrade
  or disappear. Fine for a UI with little animation of its own; not for one that animates a lot.
- **Fewer redraws first** (see "The UiState holds what the screen shows"): that helps on every
  platform.
- **Confirm it with Perfetto:** red frames in the app's `Actual Timeline` ("App Deadline Missed")
  while the `GPU completion` row is busy without a break mean GPU competition. Afterwards, the
  RenderThread row is empty and the GPU work runs as an even pattern.

---

## Core

- **Transfer types:** requests and responses are `@Serializable`, in `…Interface.kt` files, per feature.
- **`ErrorBody(code, errorMessage)` and `ErrorCodes.kt`**, with these codes. The app decides on
  the code, never on the message: the message is for the log, and the app shows its own
  localized text. A new code goes at the end of its group.

  | Code | Name | Status | When |
  |---|---|---|---|
  | 1 | `GENERAL` | 400, 404, 405, 500 | invalid input, an unknown path, an unexpected failure |
  | 2 | `PROVIDER` | 502 | an AI or other provider failed, or answered something unusable |
  | 3 | `BUDGET_EXCEEDED` | 429 | the weekly budget is used up |
  | 4 | `ENVIRONMENT` | 500, 503 | a variable, secret or binding is missing |
  | 5 | `TOTAL_BUDGET_EXCEEDED` | 429 | the total budget is used up |
  | 101 | `REFRESH_TOKEN` | 400 | the refresh failed |
  | 102 | `SESSION_EXPIRED` | 400, 401 | the session is gone: the app logs out (the only code that does) |
  | 103 | `TOKEN_VALIDITY` | 401 | the ID token is missing or invalid |
  | 104 | `SEND_MAIL` | 400 | an email (verification, reset) could not be sent |
  | 105 | `REGISTER` | 400 | the registration was refused |
  | 106 | `LOGIN` | 400 | a wrong email address or password |
  | 107 | `CHANGE_PASSWORD` | 400 | the new password was refused |
  | 108 | `RESET_PASSWORD` | 400 | the password reset was refused |
  | 109 | `AUTHENTICATION` | 502 | Firebase itself failed |
  | 110 | `GUEST` | 403 | a guest calls what is for registered users |
  | 111 | `HUMAN_CHECK` | 403 | a new guest without a passed human check |
  | 112 | `GUEST_NAME` | 400 | a guest name of the wrong length or form |
  | 113 | `EMAIL_NOT_VERIFIED` | 403 | a call that costs money, before the email is verified |
  | 114 | `TOO_MANY_REQUESTS` | 429 | over a rate limit, ours or Firebase's |
  | 115 | `GUEST_QUOTA` | 429 | a guest's quota of today is used up |

  The codes 110–115 are for apps with guests ([BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md)).
- **Validation both sides need** lives in core too, such as the maximum length of a text, the
  rules for a name, or the range of a coordinate, so that the app refuses what the service
  would refuse. It is plain functions, with unit tests in `core/commonTest`.

### Our own contract, not an upstream format

A transfer type in `core` is the contract between **our** app and **our** service. An external
API's format stays in the service.

```kotlin
// core: our contract
@Serializable
data class TranslateRequest(val text: String, val targetLanguage: Language)

@Serializable
data class TranslateResponse(val text: String, val detectedSourceLanguage: String)

// service: maps it to DeepL's format and back; DeepLRequest/DeepLResponse are service-only
val deepLRequest = DeepLRequest(text = listOf(body.text), target_lang = body.targetLanguage.code)
```

- The app doesn't know the provider's field names (`target_lang`, `detected_source_language`).
- The service validates for free: a request with an unknown language fails to decode, so it is
  a 400 before the provider is called.
- **The exception: a transparent proxy.** As long as the service only forwards, speaking the
  provider's format is right: the same client works against the provider directly or through the
  proxy. When the service starts doing its own work (checking tokens, cleaning errors, counting
  usage), it deserves its own contract.
- **An open set stays a `String`.** A language that the provider *detects* can be any language,
  not only one of ours.

### Enums both sides need

Enums such as `Language` are `@Serializable` in core.

- **Between our own two ends,** `@Serializable` alone is enough: the wire value is the
  constant's name (`"ES"`), and both ends share the enum through core.
- **`@SerialName` is only needed when someone else's format, or stored data, dictates the
  strings,** for example when the database stores the codes (`"it"`).
- **A `code` property** (`name.lowercase()`) serves an external API that wants ISO codes, for
  example DeepL, without annotating every constant.
- If an enum must ever be sent in an external format itself, write one small serializer based
  on `code`, in one place, rather than an annotation per constant.
- **The UI parts,** such as flags and localized names, are extension properties in the app.
  They use exhaustive `when`s, so a new value without its UI doesn't compile.

---

## The service (Cloudflare Worker, Kotlin/JS)

### The router owns the cross-cutting concerns

```kotlin
class Call(val request: Request, val env: Env, val ctx: ExecutionContext, val token: String?)

fun handleRequest(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> =
    GlobalScope.promise(Dispatchers.Unconfined) {
        // OPTIONS preflight, GET /, GET routes for the browser, 405 for other non-POST
        val response = try { route(path, Call(request, env, ctx, bearerToken)) }
            catch (e: ServiceException) { createError(e.code, e.message ?: "", e.status) }
            catch (e: Throwable) { console.error(...); createError(ERROR_CODE_GENERAL, "Internal error", 500) }
        // the CORS header, once, here
        response
    }
```

- **`Dispatchers.Unconfined`, always.** The Workers runtime cancels continuations that are scheduled across requests.
- **Handlers take a single `Call`.** Adding something later changes `Call`, not every handler.
- **Calls are POSTs with JSON.** For some use cases, such as a proxy that returns an image, a GET
  is possible. It is authenticated like any other call (the bearer token), and gets a line in
  `route` like a POST.
- **`createError`** logs with `console.error` for status ≥ 500 and `console.warn` otherwise.
- **CORS, once, here.** The preflight (`OPTIONS`) and every response get
  `Access-Control-Allow-Origin` with the request's `Origin`, but only when that origin is in
  `ALLOWED_ORIGIN` (plus `http://localhost:8080`, the local web app). An origin that isn't is
  logged with a warning: the browser itself only shows a vague CORS error. The native apps send
  no `Origin`, and are not checked.

### Handlers read top to bottom

```kotlin
suspend fun handleX(call: Call): Response {
    val userId = guardRequest(call)                 // or requireUserId(call)
    val body = call.request.decodeBody<XRequest>()  // 400 on invalid JSON
    val result = callProvider<...>(...)             // 502 on a provider error
    storeAiCall(call, userId, ...)                  // after the response
    return jsonResponse(XResponse(...))
}
```

- **Errors:** `throw ServiceException(code, message, status)`. There are no sealed `Allowed`/`Denied` or `Success`/`Failure` types to unwrap.
- **The environment:** `Env` properties are all nullable. `requireEnv(env.X, ENV_X)` throws a 500 and `requireDb(env)` a 503 (a binding, as a missing limiter), both with the environment code; the variable's name goes to the log, not to the user.
- **Input:** every text that goes to a provider or the database has a maximum length, and
  every code (a language, a time) a fixed form. Check them before anything costs money.
- **Authorization:**
  - `requireUserId(call)` verifies the Firebase token and returns its `sub`. A missing or invalid token is a 401.
  - `guardRequest(call)` adds what calls that cost money need: the budget (a 429, the total
    before the week), and a rate limit per user.
  - **Budgets are in ticks, not tokens.** A tick is a fixed amount of money (for example $0.0001). A `model_rates` table holds each model's ticks per million input and output tokens, with a `valid_from`. Each usage row stores the model, the tokens, and the ticks worked out at insert time, so a price change never rewrites history. A model without a rate counts at the highest known rate, with an error in the log. The app shows ticks, and users would buy kiloticks.
- **External calls:**
  - `callProvider` is for AI and other APIs. On an error it logs the provider's text and throws a 502; that text never reaches the user.
  - `callAuthentication` is for Firebase.
    - A 4xx that the call expects (a wrong password; an expired refresh token, which is
      session expired) is the user's problem: a 400 with a specific code.
    - Too many attempts is a 429: nothing is wrong with the account.
    - Any other answer, and a 5xx, is Firebase's problem: a 502.
  - **One `Json` for the service, with `encodeDefaults = true`** (and `ignoreUnknownKeys`,
    `explicitNulls = false`). Firebase needs `returnSecureToken: true`, a field with a default,
    and kotlinx.serialization leaves defaults out otherwise: the answer then has no tokens.
- **Bookkeeping:** use `afterResponse(call, what) { ... }`, which wraps `ctx.waitUntil`. The user doesn't wait for it, and a failure is logged, not returned.
- **Firebase signing keys:** cache them for their `Cache-Control` max-age, and the imported keys per `kid`. Refetch for an unknown `kid`, at most once a minute.
- **Logs:** what a user wrote stays out of the logs, except with a debug switch (`DEBUG_AI`)
  that is off when deployed.

### The account endpoints

Firebase Authentication is reached only through the worker, with its REST API (the Identity
Toolkit, `identitytoolkit.googleapis.com` and `securetoken.googleapis.com`) and the public
`FIREBASE_API_KEY`. The apps never talk to Firebase themselves, and need no Firebase SDK.

| Endpoint | Body (bearer token) | Answer | Firebase | Notes |
|---|---|---|---|---|
| `/login` | `LoginRequest(email, password)` | `AccountResponse` | `accounts:signInWithPassword` | `LOGIN_LIMITER` per IP; a wrong password is `LOGIN` |
| `/register` | `RegisterRequest(email, password)` (a guest's, optional) | `AccountResponse` | `accounts:signUp`, or `accounts:update` for a guest | `ACCOUNT_LIMITER` per IP |
| `/refresh_token` | `RefreshTokenRequest(refreshToken)` | `AccountResponse` | `securetoken` `token` | an invalid refresh token is `SESSION_EXPIRED` |
| `/verify_email` | – (the user's) | – | `accounts:sendOobCode`, `VERIFY_EMAIL` | `ACCOUNT_LIMITER` per IP; with `continueUrl` = `CONFIRM_URL` |
| `/password_reset` | `PasswordResetRequest(email)` | – | `accounts:sendOobCode`, `PASSWORD_RESET` | `ACCOUNT_LIMITER` per IP; an unknown address is `RESET_PASSWORD` |
| `/change_password` | `ChangePasswordRequest(idToken, password)` | `AccountResponse` | `accounts:update` | new tokens: the old ones stop working |
| `/confirm` | GET, from the email's link | a small HTML page | – | "Your email address is verified; go back to the app" |
| `/guest` | `GuestRequest(humanToken, name)` | `AccountResponse` | `accounts:signUp` (anonymous) | see [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md) |

```kotlin
// core
@Serializable
data class AccountResponse(
    val idToken: String,
    val refreshToken: String,
    val emailVerified: Boolean,
    val isGuest: Boolean = false,
    val name: String? = null,            // a guest's name
)
```

- **The app keeps the answer as its `Session`** (`idToken`, `refreshToken`, `emailVerified`,
  and for a guest `isGuest` and `name`), in local storage.
- **Each of these handlers also updates the `users` row** (email, verified, `synced_at`), or
  makes it for a new registered user.
- **The password reset has no `continueUrl`:** the `/confirm` page says the address is
  verified, which is wrong after a reset. Firebase's own page then ends the flow.
- **Firebase's error messages, per endpoint.** Firebase answers a 400 with a message such as
  `EMAIL_EXISTS` or `WEAK_PASSWORD : Password should be at least 6 characters` (compare the part
  before ` : `):

  | Endpoint | Firebase message | Our code |
  |---|---|---|
  | `/login` | `INVALID_LOGIN_CREDENTIALS`, `EMAIL_NOT_FOUND`, `INVALID_PASSWORD`, `USER_DISABLED`, `INVALID_EMAIL` | `LOGIN` |
  | `/register` | `EMAIL_EXISTS`, `INVALID_EMAIL`, `WEAK_PASSWORD`, `CREDENTIAL_TOO_OLD_LOGIN_AGAIN` | `REGISTER` |
  | `/refresh_token` | `TOKEN_EXPIRED`, `USER_DISABLED`, `USER_NOT_FOUND`, `INVALID_REFRESH_TOKEN` | `SESSION_EXPIRED` (400) |
  | `/change_password` | `WEAK_PASSWORD`, `CREDENTIAL_TOO_OLD_LOGIN_AGAIN` | `CHANGE_PASSWORD` |
  | | `INVALID_ID_TOKEN`, `TOKEN_EXPIRED` | `SESSION_EXPIRED` (401) |
  | `/password_reset` | `EMAIL_NOT_FOUND` | `RESET_PASSWORD` |
  | all | `TOO_MANY_ATTEMPTS_TRY_LATER`, or an HTTP 429 | `TOO_MANY_REQUESTS` (429) |
  | all | anything else | `AUTHENTICATION` (502) |
- **Firebase in the console, once:** a project, with the **Email/Password** sign-in provider
  on (Authentication → Sign-in method). Its Web API key is `FIREBASE_API_KEY`, its project id
  `FIREBASE_PROJECT_ID` (both vars: they are not secret). The worker checks tokens against
  that project.

### Configuration: vars, secrets and CORS

The worker gets all its configuration through `env`, declared in one place:

```kotlin
// foundation/Env.kt: every property nullable, every var a String
external interface Env {
    val ALLOWED_ORIGIN: String?        // var: the web app's origins, comma-separated
    val FIREBASE_API_KEY: String?      // var: public, it is in every app as well
    val FIREBASE_PROJECT_ID: String?   // var
    val ANTHROPIC_MODEL: String?       // var
    val ANTHROPIC_MAX_TOKENS: String?  // var: a number, as text
    val ANTHROPIC_API_KEY: String?     // secret
    val WEEKLY_BUDGET_TICKS: String?   // var
    val DB: D1Database?                // binding: [[d1_databases]]
    val USER_LIMITER: RateLimiter?     // binding: [[ratelimits]]
}
```

| Kind | Where | Examples | Locally |
|---|---|---|---|
| **var:** public, may change without a code change | `[vars]` in `wrangler.toml`, in git | models, budgets, `ALLOWED_ORIGIN`, `CONFIRM_URL`, the Firebase API key and project | the same file |
| **secret:** never in git | `npx wrangler secret put NAME`, once per secret | the API keys of the AI and other providers | `service/.env` (in `.gitignore`), read by `wrangler dev` |
| **binding:** a Cloudflare resource | `wrangler.toml`: `[[d1_databases]]`, `[[ratelimits]]`, `[images]` | `DB`, the limiters | `wrangler dev` simulates them, or `--remote` uses the real ones |

- **A var is text.** Keep the quotes in `wrangler.toml` (`ANTHROPIC_MAX_TOKENS = "4096"`), and
  convert in Kotlin (`toIntOrNull()`, with a default).
- **Missing is a 500 or 503, never a silent default for what matters:** `requireEnv(env.X,
  "X")` for a var or secret is a 500; `requireDb(env)` for the database, and a missing limiter,
  are a 503, as every missing binding.
- **Bindings** are `external interface`s of our own (`cloudflare/D1.kt`, `RateLimiter`), with
  only the methods we use.
- **`wrangler.toml` explains each var** in a comment: what it is, its unit, and its default.
- **In a public repository, `wrangler.toml` stays out of git.** Its vars are not secret, but
  together with the worker names, the database id and the account's `workers.dev` subdomain they
  say where the service runs, and the random suffix of the name is there to hide that. Commit a
  `wrangler.example.toml` with `<placeholders>` instead, keep the real `wrangler.toml` in
  `.gitignore` (as `secret.properties`), and let the deploy script stop when it is missing. A
  change of a var or a limiter goes into the example too. The web app's `wrangler.toml` the same.
- **`ALLOWED_ORIGIN`** lists the web app's origins (`https://myapp.example.com`, and its
  `workers.dev` address while that is used). The router uses it for CORS, and the human check
  for the hostnames it accepts.

### AI calls

One client per provider (`claude/ClaudeClient.kt`, `openai/…`), on top of `callProvider`. A
handler only sees a function such as `callClaude(env, systemPrompt, userPrompt): AiResult`,
with `AiResult(content, model, inputTokens, outputTokens)`.

```kotlin
// claude/ClaudeClient.kt
@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
)

suspend fun callClaude(env: Env, systemPrompt: String, userPrompt: String): AiResult {
    val model = requireEnv(env.ANTHROPIC_MODEL, "ANTHROPIC_MODEL")
    val response = callProvider<ClaudeRequest, ClaudeResponse>(name = "Claude",
        url = "https://api.anthropic.com/v1/messages",
        request = ClaudeRequest(
            model = model,
            maxTokens = env.ANTHROPIC_MAX_TOKENS?.toIntOrNull() ?: 1024,
            system = systemPrompt,
            messages = listOf(ClaudeMessage(role = "user", content = userPrompt))),
        headers = mapOf(
            "x-api-key" to requireEnv(env.ANTHROPIC_API_KEY, "ANTHROPIC_API_KEY"),
            "anthropic-version" to "2023-06-01"),
        debug = isAiDebug(env))
    val text = response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
    if (response.stopReason == "max_tokens") console.warn("Claude reached max_tokens: the answer is cut off")
    return AiResult(text, model, response.usage.inputTokens, response.usage.outputTokens)
}
```

- **The model is a var** (`ANTHROPIC_MODEL`), so a better or cheaper model is a change of
  `wrangler.toml`, not of code. One endpoint can have its own (`ANTHROPIC_MODEL_FEEDBACK`, falling
  back to the general one).
- **`max_tokens` is a var too,** with a default in code. Set it to what the answer needs, with
  room: it is a ceiling, not a target. A cut-off answer (`stop_reason` `max_tokens`) is logged,
  because otherwise it only shows as invalid JSON.
- **Structured answers:** the system prompt describes the JSON, with its fields, and ends with
  "Return ONLY a raw JSON object, no markdown". The service then decodes it into a
  `@Serializable` class of its own (`decodeAiJson`): it takes the last complete `{…}` block of
  the answer that decodes, because a model sometimes adds a code fence or a remark anyway. No
  valid block is a 502 (`PROVIDER`). The log gets the answer's length, and the answer itself
  only with `DEBUG_AI`: a model often repeats the user's text. For a shape that must never
  fail, tool use with an input schema is stricter; the prompt is simpler, and was enough so far.
- **Check what comes back** as you check user input: a latitude between −90 and 90, a word that
  is really in the text. The model's answer is data from outside.
- **The user's text:** the instructions are in the system prompt; the user's text only goes in
  the user message, after a label (`Correct this text: '…'`), with its maximum length checked
  first. The system prompt says what to do with that text, so an instruction inside it is text
  to work on, not a command. Never put a secret, or another user's data, in a prompt.
- **Every AI call is paid for by a user.** The handler starts with `guardRequest(call)` (the
  budget, and the rate limit per user), and ends with `storeAiCall(call, userId, operation,
  provider, model, inputTokens, outputTokens)` after the response, which turns the tokens into
  ticks. An app without any accounts doesn't fit this blueprint: it would need at least a
  limit for all calls together.
- **Who may make paid calls,** in `guardRequest`:
  - a registered user only with a verified email: a 403 (`EMAIL_NOT_VERIFIED`) before, so a
    made-up address can't spend;
  - a guest within its own total budget, `GUEST_TOTAL_BUDGET_TICKS` (a var), and no weekly one
    of its own. A guest starts without a starter grant: this budget is all it has.
    `GUEST_TOTAL_BUDGET_TICKS = "0"` makes paid calls for registered users only.
  - Both answer a 429 when their budget is used up.

### CPU time on the Worker: native JSON for megabytes, and a minified bundle

The free Workers plan allows about 10 ms of CPU per request (waiting for a provider does not
count). Kotlin/JS is fine for that, with two exceptions: megabytes of JSON, and the cold start.

**A generated image is megabytes of base64 inside JSON.** kotlinx.serialization reads and writes
it character by character, in Kotlin code. JavaScript's own `JSON.parse` and `JSON.stringify`
do the same natively. Measured in Babel Magic, for `/generate_image`: 26–34 ms with kotlinx,
15–22 ms with native parsing, and a scene image (written back in full) from 48 ms down as well.

- **Parse the image response natively,** into an `external interface`, the same way D1 rows are
  read. The request stays `@Serializable`: it is small.

  ```kotlin
  // FetchJson.kt: fetchProvider is the shared part (fetch, error handling, debug log)
  suspend inline fun <reified Req, Res> callProviderNative(
      name: String, url: String, request: Req, headers: Map<String, String>,
      debug: Boolean = false,
  ): Res = JSON.parse(fetchProvider(name, url, request, headers, debug))

  // The response, parsed natively: nothing is checked, so every field is nullable
  private external interface GeminiInlineData {
      val mimeType: String?
      val data: String?
  }
  ```
  A missing image is then an explicit 502 ("Gemini response contains no image").
- **Write the image response natively** too. Its shape stays the contract type in core:

  ```kotlin
  fun nativeJsonResponse(body: Json): Response =                  // JSON.stringify(body)
  private fun imageResponse(image: String) = nativeJsonResponse(json("image" to image))
  ```
- **Decode base64 natively, when the bytes are needed** (resizing, storing): `Uint8Array.fromBase64`
  and `toBase64()` (ES2025, in kotlin-wrappers), with Kotlin's `Base64` as a fallback for a
  runtime that lacks them.
- **Everything else keeps kotlinx.serialization:** the other responses are small, and typed.

**The cold start.** The Kotlin/JS output is about 2 MB (the stdlib alone is about 600 KB). A new
isolate loads it, and V8 compiles each function the first time it runs. The first requests after
a pause cost 30–45 ms more than the same requests warm (in Babel Magic: 49 ms against 12 ms).
An OPTIONS request that costs more than 0 ms shows a cold isolate.

- **`minify = true` in `wrangler.toml`:** less code to parse at each cold start. Only local names
  get shorter, so the exported `handleRequest` stays reachable. Stack traces get harder to read;
  `upload_source_maps = true` restores the names in Cloudflare's logs.
- **With more users, isolates stay warm,** and the cold start mostly disappears.

**Measuring:** the dashboard shows the CPU time per request (Observability, Invocations). Single
values above 10 ms are accepted; what matters is "Exceeded CPU" (error 1102) in the logs. The
paid plan ($5 a month, for all Workers of the account) allows 30 s of CPU by default.

### Data

- **D1** holds users and per-call usage rows. Totals are sums, so concurrent calls never overwrite each other.
- **Migrations** are numbered (`migrations/NNNN_name.sql`), idempotent (`IF NOT EXISTS`, `INSERT OR IGNORE/REPLACE`, `ON CONFLICT … DO NOTHING`), and commented with how to apply them `--local` and `--remote`.
- **A new migration goes first,** before the service that needs it is deployed.
- **D1's API** is declared as external interfaces (`cloudflare/D1.kt`), because it is not part of the web standard.
- **Defaults** that change without a deployment go in `wrangler.toml` `[vars]`, with per-user overrides in the database.
- **Deleting a user** deletes all their rows: every table refers to `users` with
  `ON DELETE CASCADE`.
- **Times are text,** ISO 8601 in UTC (`2026-11-25T13:35:00.000Z`), so they sort and compare
  as strings. Booleans are `INTEGER` 0 or 1.

The first migration, which every app has. The app's own tables follow in later migrations.

```sql
-- migrations/0001_init.sql
--   npx wrangler d1 migrations apply myapp --local    (and --remote)

-- A user: made at the first call of a registered user, or by POST /guest for a guest
CREATE TABLE IF NOT EXISTS users (
  id                  INTEGER PRIMARY KEY,
  firebase_id         TEXT NOT NULL UNIQUE,      -- the token's "sub"
  email               TEXT,                      -- NULL for a guest
  email_verified      INTEGER NOT NULL DEFAULT 0,
  is_guest            INTEGER NOT NULL DEFAULT 0,
  name                TEXT,                      -- a guest's name
  weekly_budget_ticks INTEGER,                   -- NULL: WEEKLY_BUDGET_TICKS
  total_budget_ticks  INTEGER,                   -- NULL: TOTAL_BUDGET_TICKS (or the guest's)
  created_at          TEXT NOT NULL,
  synced_at           TEXT NOT NULL              -- updated at every login and refresh
);

-- The models of the AI and other providers, and what they cost
CREATE TABLE IF NOT EXISTS models (
  id       INTEGER PRIMARY KEY,
  name     TEXT NOT NULL UNIQUE,                 -- as the provider names it: 'claude-haiku-5-5'
  provider TEXT NOT NULL                         -- 'claude', 'openai', 'gemini', ...
);

-- A price change is a new row with a later valid_from, so old calls keep their price
CREATE TABLE IF NOT EXISTS model_rates (
  model_id                 INTEGER NOT NULL REFERENCES models(id),
  valid_from               TEXT NOT NULL,
  ticks_per_million_input  INTEGER NOT NULL,
  ticks_per_million_output INTEGER NOT NULL,
  PRIMARY KEY (model_id, valid_from)
);

-- One row per paid call: the budgets are sums of these
CREATE TABLE IF NOT EXISTS ai_calls (
  id            INTEGER PRIMARY KEY,
  user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at    TEXT NOT NULL,
  operation     TEXT NOT NULL,                   -- the endpoint: 'geocode', 'correct', ...
  model_id      INTEGER NOT NULL REFERENCES models(id),
  input_tokens  INTEGER NOT NULL,
  output_tokens INTEGER NOT NULL,
  ticks         INTEGER NOT NULL                 -- worked out at the time of the call
);
CREATE INDEX IF NOT EXISTS idx_ai_calls_user_time ON ai_calls (user_id, created_at);

-- The models in use, with their prices. These numbers are an example: look up the
-- current ones. 1 tick = $0.0001, so $1 per million tokens is 10000 ticks per million.
INSERT INTO models (name, provider) VALUES ('claude-haiku-5-5', 'claude')
  ON CONFLICT (name) DO NOTHING;
INSERT INTO model_rates (model_id, valid_from, ticks_per_million_input, ticks_per_million_output)
  SELECT id, '2000-01-01T00:00:00.000Z', 10000, 50000 FROM models WHERE name = 'claude-haiku-5-5'
  ON CONFLICT DO NOTHING;
```

- **The weekly budget** is the sum of `ticks` since Monday 00:00 UTC, the total budget the sum
  of all. A user's own budget overrides the var, when it is not `NULL`.
- **A model that is used but has no rate** is added to `models` at its first use, and counted
  at the highest known rate, with an error in the log: add its rate then.
- **Guests** need nothing more here. What a guest uses per day, if the app limits it, has its
  own table (see [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md)).

### Smoke test

A small Kotlin/Native console program (`service/smokeTest`) calls the deployed
service end to end with a test account. It leaves the account as it was. The
steps that cost money or send mail are opt-in flags. It exits non-zero on a failure.

---

## Tests

What a new app has from the start, all with `kotlin-test`:

- **`core/commonTest`: unit tests of everything in core.** The validation (lengths, names,
  ranges), the calculations (such as a conversion between coordinates), and the serialization
  of the transfer types where it is not plain: a renamed field, an enum with `@SerialName`, a
  default value that an older client leaves out. They run on every target
  (`./gradlew :core:allTests`), so they also prove that the code is truly common.
- **`app/shared/commonTest`: view model tests with fake gateways.** A fake implements the
  gateway interface and returns a fixed `Result`, so a test sends an action and checks the
  UiState and the `MessageStore`: a success, a failure, and an overtaken request. Use
  `kotlinx-coroutines-test` (`runTest`, and `Dispatchers.setMain` with a test dispatcher,
  because `viewModelScope` uses the main dispatcher). Give a store a scope of its own on the
  test's scheduler (`CoroutineScope(StandardTestDispatcher(testScheduler))`), not
  `backgroundScope`: `advanceUntilIdle()` doesn't run the work of `backgroundScope`, so the
  store's saves in the background would never happen.
- **Pure logic in the app** (a comparison, a parser, a WAV header) is a plain function, with
  its own unit test.
- **The service:** what a handler decides is a plain function in its own file (a check, a
  calculation, the mapping of a provider's answer), tested in `service/src/jsTest`. A whole
  handler, with `fetch`, D1 and the runtime around it, is covered by the smoke test against
  `wrangler dev` or the deployed service, not by unit tests.
- **Every test names what it shows** (`hugeChunkIsNoRecording`, `lettersAndDigitsOnly`),
  with a one-line comment when the name isn't enough.

---

## Versions

The versions of the reference implementation in November 2026:

| | |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 (material3 1.12.0-alpha03) |
| AGP / Gradle | 9.4.0 / 9.9.1 |
| AndroidX lifecycle (JetBrains) / DataStore | 2.11.0 / 1.3.0 |
| Ktor | 3.6.0 |
| kotlinx: coroutines / serialization / datetime | 1.11.0 / 1.13.0 / 0.7.1 |
| kotlin-wrappers | 2026.10.4 |
| Android | compileSdk 37, targetSdk 36, minSdk 24; JVM toolchain 11 |

The example in this repository ([example_slippy_map_tiles](example_slippy_map_tiles)) keeps
what the wizard gave in November 2026, and builds with it as the blueprint describes: AGP 9.5.2,
Gradle 9.6.0, Ktor 3.6.1, kotlin-wrappers 2026.11.2, targetSdk 37; the rest as above.

One version is not free: the desktop package (`nativeDistributions.packageVersion`) needs a
major version of at least 1 for a macOS DMG. An app at 0.x keeps `1.0.0` there; the app itself
shows its own version.

The step to Kotlin 2.4.20, Compose Multiplatform 1.12, AGP 9.4 and kotlin-wrappers 2026.9.3
(October 2026) required these changes:

- **`compileSdk` 37.** Compose Multiplatform 1.12 brings AndroidX Compose 1.12 (material3
  1.5), which requires it. `targetSdk` and `minSdk` don't change.
- **`fetch` returns a `Resource`.** In kotlin-wrappers 2026.9.3, `web.http.fetch` returns
  `Resource<Response>`, an `AutoCloseable`. Take `.value`. Closing the resource aborts the
  request, so leave it open while the body is read: the same behavior as before.

  ```kotlin
  return fetch(url, RequestInit(...)).value
  ```
- **npm or Yarn.** The reference implementation went from Yarn to npm with Kotlin 2.4 (the lock
  file in `kotlin-js-store` became `package-lock.json`, after deleting the Yarn-era
  `build/js`, `build/wasm` and `kotlin-js-store` once). A new wizard project on Kotlin 2.4.21
  still uses Yarn (`kotlin-js-store/yarn.lock`, and `wasm/yarn.lock`). Either works. With Yarn,
  a new JS dependency stops the build with "Lock file was changed": run
  `./gradlew kotlinUpgradeYarnLock`, and `kotlinWasmUpgradeYarnLock` for Wasm, separately.
- **Deprecated internals disappear,** for example `SystemTheme` and `LocalSystemTheme` (see
  dark mode).

---

## Checklists

**A new screen:**
1. Transfer types in `core/<feature>/…Interface.kt`.
2. `…Gateway` and `Cloud…Gateway`, then add the gateway to the `AppContainer`.
3. `…UiState`, `…Action` and `…ViewModel(gateway, stores, messages)`.
4. `…Screen` (stateful) in `…Screen.kt`, and `…Content` (stateless, without default
   arguments) in `…Content.kt`, with an `…Preview()` with sample data.
5. An entry in `Screen` and a branch in `App`.
6. A view model test with a fake gateway: a success and a failure.

**A new endpoint:**
1. Its types in `core`: our own types. A provider's format stays in the service.
2. `handleX(call)` with guard, decode, validation, work, bookkeeping and response.
3. JSON of megabytes (an image as base64), from a provider or to the app, is parsed and
   written natively: `callProviderNative` and `nativeJsonResponse`, not kotlinx.serialization.
4. A line in `route`.
5. A migration, if it stores data.
6. Its new vars in `[vars]`, with a comment; its secrets with `wrangler secret put`, and in
   `service/.env` for local runs.
7. Unit tests of its validation and logic, in core or the service.
8. A step in the smoke test.

**Before calling something done:**
- no view model or `MutableStateFlow` leaks into composables;
- failures reach `MessageStore`;
- the error codes are in core;
- the provider texts are logged, not returned;
- state survives rotation;
- every preview renders, including the ones that touch the platform;
- no `isSystemInDarkTheme()` outside `isDarkTheme()`;
- no `expect class` or `expect object`, and no `-Xexpect-actual-classes`;
- no timer for the session token: gateways call `validIdToken()`;
- a state that changes often (a location, a camera, a timer) holds rounded, shown values only;
- the screen still fits in a phone browser, in portrait and in landscape.

## Open items

These are not done yet in the reference implementation:

- view model tests with fake gateways (it has the unit tests of core and of the app's pure
  logic; a new app has both from the start, see [Tests](#tests));
- loading states;
- cancelling stale jobs, such as a request that a newer one overtakes.
