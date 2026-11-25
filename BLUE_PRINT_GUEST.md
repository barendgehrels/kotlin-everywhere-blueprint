# Guests, the human check and the APK download

How to let people in without an account, with the stack of the
[blueprint](BLUE_PRINT.md): Kotlin Multiplatform with Compose (web, Android, iOS, desktop), a
Cloudflare Worker as the service, also in Kotlin (Kotlin/JS, compiled to JavaScript), D1 as the
database, and Firebase Authentication through its REST API (the Identity Toolkit), called by the
worker only. The examples of what a guest may use are those of two kinds of apps: map tiles and
shapes in a map app, exercises in a learning app. Your app has its own.

The idea:

- **A guest** types a name and taps Guest. They get a real session, which
  means a Firebase anonymous user and a row in our `users` table, so
  everything is metered and limited per user as usual.
- **A human check** (Cloudflare Turnstile) comes before a new guest, so a
  script cannot make guests by the thousand.
- **A guest who registers** stays the same user, with everything they made.
- **The Android app** can be downloaded from the web app itself, without a
  store.

## 1. The APK below the web app

The worker serves the web app as static assets, and the deploy puts the
signed APK next to it. `index.html` shows one small line when it finds one:

```
Android: download the app (1.4.2)
```

### Signing

- **One key, for good.** Android only installs an update over
  an APK signed with the same key, so this key must never change. It stays
  outside the repository.
- **The key's settings are in `~/.gradle/gradle.properties`:**
  ```
  org.example.key.store=/path/to/release.jks
  org.example.key.alias=...
  org.example.key.password=...
  org.example.store.password=...
  ```
- **Without them the release is built unsigned** (`app/androidApp/build.gradle.kts`
  only warns), and the file name says so.
- **The output name** carries the app, signed or not, and the version from
  git:
  `<app>-release-signed-version-<version>.apk`. The deploy script relies on
  that name.

### The deploy (`scripts/deploy_app.sh`)

1. It builds the service, the web app and the Android release in one Gradle
   call.
2. It looks for `<app>-release-signed-*.apk`. Only a signed one, since an
   unsigned APK doesn't install.
3. It copies the APK into the web app's folder, under `android/`, and writes
   `android.json` next to `index.html`:
   ```json
   {"file": "myapp-release-signed-version-1.4.2.apk", "version": "1.4.2"}
   ```
4. `wrangler deploy` uploads the worker with that folder as its `[assets]`.

### The page (`app/webApp/src/webMain/resources/index.html`)

```html
<footer id="android" hidden>
    Android: <a id="android-link" download>download the app</a> <span id="android-version"></span>
</footer>
<script>
    fetch("android.json")
        .then(response => response.ok ? response.json() : null)
        .then(apk => {
            if (!apk) return;
            const link = document.getElementById("android-link");
            link.href = "android/" + apk.file;
            document.getElementById("android-version").textContent = "(" + apk.version + ")";
            document.getElementById("android").hidden = false;
        })
        .catch(() => {});
</script>
```

The footer stays hidden when there is no `android.json`. That happens on a
local run, or when the deploy found no signed APK. The `download` attribute
makes the browser save the file instead of opening it.

### Notes

- **The user installs from an unknown source.** Android asks them once to
  allow installs from their browser. Say so to the first users.
- **The version in the name** tells the user whether they have the latest:
  Android shows no update otherwise.
- **The size counts.** A Cloudflare Workers asset can be at most 25 MiB; the
  APK must stay below that.

## 2. Turnstile in Cloudflare

Turnstile is Cloudflare's CAPTCHA replacement. Mostly it is invisible; only a
visitor it doubts gets a checkbox. It gives a token, valid once for 5
minutes, which the service checks with Cloudflare.

### Setting it up

1. **Make a widget** in the Cloudflare dashboard, under Turnstile, one per app.
   Mode: Managed.
2. **Its hostnames** must contain every host the check runs on:
   - the web app's host (`myapp.example.com`);
   - the host the native apps call, `cloud_service_url`, because their check
     runs on the worker's own page (see 3.2). With a custom domain (see
     [BLUE_PRINT_DOMAIN.md](BLUE_PRINT_DOMAIN.md)) that is the domain itself, else the
     `*.workers.dev` address.
3. **The site key is public.** It goes:
   - in the web app's build (a Gradle property), which puts it in
     `index.html`;
   - in `wrangler.toml` as the var `TURNSTILE_SITE_KEY`, for the worker's
     page `/human_check`.
4. **The secret key is a worker secret:**
   ```
   npx wrangler secret put TURNSTILE_SECRET_KEY
   ```
   Without it the service skips the check only locally (`wrangler dev`, on a
   localhost or private address). Deployed, `/guest` answers a 503, so a
   forgotten secret doesn't open the door.

### Testing locally

The real site key doesn't work on localhost. Cloudflare has test keys that
always pass:

- **For the web app**, build with `-PturnstileTest`; the build then uses the
  site key `1x00000000000000000000AA`.
- **In the local `.env` of the worker:**
  ```
  TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA
  TURNSTILE_SITE_KEY=1x00000000000000000000AA
  ```
  The second line is needed for Android and iOS, which use the worker's page.

A real secret refuses the test token, so the two kinds of keys can't be mixed.

### The check in the service (`authentication/HumanCheck.kt`)

`requireHuman(call, token)`:

1. **No `TURNSTILE_SECRET_KEY`:** locally it returns without checking; deployed it is a 503
   (`ENVIRONMENT`), with an error in the log.
2. **An empty token:** a 403 (`HUMAN_CHECK`).
3. **A token starting with `error:`** (the native page's report of a failed
   Turnstile, see 3.2): a 403, with the code logged.
4. **Otherwise** it posts `{secret, response, remoteip}` to
   `https://challenges.cloudflare.com/turnstile/v0/siteverify`, and gives a
   403 unless the answer has `success: true`. `remoteip` is the request's
   `CF-Connecting-IP`.
5. **The hostname** in the answer must be one of ours: a host of the web app
   (`ALLOWED_ORIGIN`) or the worker's own host. Else it is a 403 too, because the token
   was solved on someone else's page with our site key. Not locally: the test keys answer
   `example.com`.

## 3. The human check in the apps

The common interface (`foundation/HumanCheck.kt`, `expect`/`actual`):

```kotlin
// The token: "" without a site key or when Turnstile failed; null: no check here
expect suspend fun humanCheckToken(): String?

// Only where there is a check, the Guest button shows
expect val hasHumanCheck: Boolean

// Where the check is shown, if the platform shows it itself (Android, iOS).
// Once, at the top of the app.
@Composable
expect fun HumanCheckHost()
```

`SessionStore.startGuest(name)` calls `humanCheckToken()` and posts the
token with the name to `/guest`.

### 3.1 Web (`HumanCheck.web.kt` and `index.html`)

The page does the work in JavaScript, and the Kotlin side polls it:

- **`index.html` has an empty `<div id="turnstile">`** below the app, and two
  functions:
  - `requestHumanCheck()` loads Turnstile's script the first time
    (`api.js?render=explicit&onload=...`), so a visitor who never taps Guest
    never loads it. Then it renders the widget with
    `appearance: "interaction-only"`, which is invisible unless the visitor
    has to tick the box.
  - `humanCheckResult()` returns the token, or `null` while there is none.
    It returns `""` without a site key or when Turnstile failed.
- **The site key gets into the page at build time.** `app/webApp/build.gradle.kts`
  replaces `@TURNSTILE_SITE_KEY@` with the site key (`ReplaceTokens`, as
  for `@TITLE@`).
- **Kotlin declares both functions `external`**, calls
  `requestHumanCheck()`, and then polls `humanCheckResult()` every 200 ms,
  for at most 3 minutes.
- **The `#turnstile` div is centered** with a little CSS
  (`display: flex; justify-content: center`).

### 3.2 Android and iOS: the worker's page in a web view

Turnstile only runs in a web page on one of the widget's hostnames, so the
native apps can't render it themselves. The worker therefore serves a tiny
page, `GET /human_check` (`HumanCheckPage.kt`), and the apps show it in a web
view inside a small dialog.

**The page:**

- It renders Turnstile with `TURNSTILE_SITE_KEY`, on the worker's own host;
  that host is in the widget's hostnames.
- It hands the token to the app through a bridge:
  - Android: `window.HumanCheck.onToken(token)`, a `@JavascriptInterface`;
  - iOS: `window.webkit.messageHandlers.humanCheck.postMessage(token)`, a
    `WKScriptMessageHandler`.
- **On failure** it sends `"error:<code>"` (from `error-callback`), or
  `"error:expired"`. `/guest` refuses those and logs the code, which tells
  you why it failed on a device.
- **Without a site key** it sends `""` at once, for local development.
- **It reports its height.** A `ResizeObserver` on the widget sends it
  through `onHeight` or `humanCheckHeight`, in CSS pixels, which are dp on
  Android and points on iOS. The dialog starts at 72 high (the widget is 65)
  and grows when Turnstile shows its checkbox or a message.
- **It is served with `Cache-Control: no-store`.**
- **The site key is checked** against `^[A-Za-z0-9_-]*$` before it goes into
  the script.

**The app side (`HumanCheckRequests.kt`, shared):**

- `token()` puts a `CompletableDeferred<String>` in a `StateFlow` (`pending`)
  and waits on it, for at most 3 minutes.
- `HumanCheckHost()` watches `pending`. While there is a request, it shows a
  `Dialog` with the web view of `${csUrl}/human_check`.
- The bridge completes the deferred with the token. Dismissing the dialog
  completes it with `""`, so the check fails and the app says so.
- There is a new web view per request (`key(request)`), so each bridge
  answers its own request.

**Android (`HumanCheck.android.kt`):**

- an `AndroidView` with a `WebView`;
- `javaScriptEnabled` and `domStorageEnabled` set;
- a transparent background;
- `addJavascriptInterface(bridge, "HumanCheck")`;
- `destroy()` in `onRelease`;
- in a debug build, `WebView.setWebContentsDebuggingEnabled(true)`, so the
  page can be inspected from the computer in `chrome://inspect`.

**iOS (`HumanCheck.ios.kt`):**

- a `UIKitView` with a `WKWebView`;
- the two script message handlers (`humanCheck` and `humanCheckHeight`),
  added to its configuration;
- transparent, without scrolling.

In `onRelease` the handlers are removed again. The content controller holds
on to them, and without the removal the web view is never freed.

### 3.3 Desktop

There is no check on the desktop, because a web view is heavy there:
`hasHumanCheck = false`, so no Guest button. On the desktop you log in or
register.

## 4. Guests

### What a guest is

- **In Firebase,** an anonymous user. The service calls
  `accounts:signUp` without an email or password. That needs the
  **Anonymous provider** on, under Authentication → Sign-in method; without
  it, Firebase answers `ADMIN_ONLY_OPERATION`.
- **In the token,** `firebase.sign_in_provider == "anonymous"`. That is
  `JwtPayload.isAnonymous` in the worker.
- **In our database,** a `users` row with `is_guest = 1` and the name, and no
  email. The row is made before the answer, so what the guest uses is
  counted from the start.
- **No starter grant, but a guest budget:** a guest's total budget is
  `GUEST_TOTAL_BUDGET_TICKS` (a var), and that is all it gets. Set it to what a good look
  around costs, for example a day or two of normal use; `"0"` keeps all paid calls for
  registered users. After registering, the normal `TOTAL_BUDGET_TICKS` applies, with what the
  guest used counted against it.

### Only `/guest` makes a guest

Firebase's web API key is public: it is in every app. So anyone can call
`accounts:signUp` themselves and get an anonymous token, without our human
check and without our limits. The token is valid, but the user isn't ours.

- **A valid anonymous token without a `users` row is refused,** with a 401 and
  the session-expired code. It never gets a row of its own: for a registered user
  the service makes the row at the first call, for a guest only `/guest` does.
- **`/refresh_token` for an anonymous user** only refreshes a guest that has a row; any
  other is session expired too.

### `POST /guest {humanToken, name}` (`Guest.kt`)

1. **A rate limiter on the IP address** (`GUEST_LIMITER`, see 6): at most 5
   new guests per IP per minute.
2. **The name:** 3 to 40 letters (a to z, capitals allowed) or digits, so it is never
   taken for an email address (`isGuestNameValid` in core, shared with the app). This is
   checked before the human check, so a wrong name doesn't use up a token.
3. **At most `GUEST_DAILY_MAX` new guests a day** in all (a var, for example 200), counted
   in the `users` table: a 429. The limiter is per IP address, which a script can change;
   this is the ceiling of what guests can cost in a day.
4. **`requireHuman`** (see 2).
5. **Firebase `signUp`**, then the `users` row. The answer is the usual
   `AccountResponse`, plus `isGuest: true` and the name.

### What a guest may do

That is decided per endpoint in the worker (`foundation/Guard.kt`):

- **`requireSession(call)`** accepts any valid token, a guest's too. It is
  for shared data that should only be read through the app, not by everyone
  (in a map app, say, the shapes of regions).
- **`requireUser(call, allowGuest = true)`** is for what a guest may do, but
  counted and limited per user (in a map app the map tiles and a few
  shapes of their own; in a learning app the exercises).
- **`requireUser(call)` and `requireUserId(call)`** answer a guest with a
  403 (`GUEST`, "Register first"): what is only for a registered user.
- **`requireVerifiedUserId(call)`** also needs a verified email: a 403
  (`EMAIL_NOT_VERIFIED`) otherwise. It is for what only a registered user with a real
  address may do.
- **`guardRequest(call)`** is for what costs money, such as the AI (see
  [BLUE_PRINT.md](BLUE_PRINT.md#ai-calls)): a guest within its guest budget, a registered
  user only with a verified email, and both within their budget.

Limits of a guest: give a guest enough for a good look around, not for a day
of use. Two kinds:

- **A quota a day** for what costs per request, for example the map tiles:
  `GUEST_TILES_PER_DAY` (a var, default 2000), then a 429
  (`GUEST_QUOTA`). It is counted in memory per isolate, starting from
  what the database had, so a little more gets through, never much, at one
  database read per guest a day. Its table:

  ```sql
  CREATE TABLE IF NOT EXISTS usage_days (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day     TEXT NOT NULL,                       -- '2026-11-25', UTC
    count   INTEGER NOT NULL DEFAULT 0,          -- here the tiles of that day
    PRIMARY KEY (user_id, day)
  );
  ```
- **A maximum of what a guest stores,** for example 3 shapes of their own,
  against 100 for a registered user.

On the app side, a feature switch turns guests on for the app
(`Features.guests`), and the Guest button shows only where `hasHumanCheck`
is true too. `Session.hasSession` (any session) and `Session.isRegistered`
(not a guest) decide what the screens show; without any session, hide what
doesn't work then, rather than show it dead.

### A guest who logs out, and comes back

An anonymous Firebase user has no password, so once its tokens are gone
nobody can sign in to it again. Therefore:

- **Logging out remembers the guest on the device:** its name and its
  refresh token, in `KnownGuests` in local storage (`SessionStore.logOut`).
- **The Guest button with the same name** (ignoring case) resumes that guest
  with `/refresh_token`. There is no human check and no new user, and
  everything they made is still there.
- **A refresh token that is no longer valid** (`SESSION_EXPIRED`) means a new
  guest, and the old entry goes. Offline, the entry stays for next time.

## 5. From guest to registered user

The guest becomes the account itself, not a new user. So the Firebase uid
stays, and with it the `users` row and everything the guest made.

### In the app

- **The guest screen** has a button "Change guest login into email
  registration". The email field there is a separate one (`registerEmail`),
  because the login field holds the guest's name.
- **`AccountViewModel.register()` refreshes the guest's token first**
  (`refreshNow()`), so the service gets one that is valid, and then calls
  `/register`.
- **`CloudAccountGateway.register`** sends the guest's ID token as the bearer
  when the session is a guest's.

### In the service (`Register.kt`)

- **With a guest's token,** it calls Firebase `accounts:update` with
  `{idToken, email, password}`. That links the email and password to the
  anonymous user, so the uid stays the same.
- **Without one,** it calls `accounts:signUp` as for any new user.
- **Then, as at login,** it updates the `users` row: email, verified, and
  `is_guest = 0`. A new account gets the starter grant now.
- **The app then asks for the verification email** (`/verify_email`), as
  after any registration.

### Firebase settings

- **Email enumeration protection must be off:** Authentication → Settings →
  User actions. With it on, Firebase refuses to set an email on an existing
  user through `accounts:update`, and answers `OPERATION_NOT_ALLOWED`. The
  protection exists to hide whether an email is registered. Here the service
  calls Firebase itself, and limits and checks the requests (see 6), so turning it
  off is acceptable.
- **An email that already has an account** is refused with `EMAIL_EXISTS`.
  The user then logs in to that account instead; the guest's things stay
  with the guest.
- **The Anonymous provider** must be on (see 4).
- **The verification and reset emails** are sent by Firebase itself, here through a custom
  SMTP relay (Authentication → Templates → SMTP settings). `CONFIRM_URL` is the
  `continueUrl`, the page the email link ends on (`/confirm`).

## 6. Rate limiters

These are Cloudflare's rate limiting bindings, in `wrangler.toml`:

```toml
[[ratelimits]]
name = "GUEST_LIMITER"
namespace_id = "1001"          # any number, unique in the Cloudflare account
simple = { limit = 5, period = 60 }
```

| Limiter | Key | Limit | For |
|---|---|---|---|
| `GUEST_LIMITER` | IP address | 5 a minute | new guests; a script stops here, even with the human check passed |
| `LOGIN_LIMITER` | IP address | 10 a minute | `/login`: guessing passwords |
| `ACCOUNT_LIMITER` | IP address | 3 a minute | `/register` and the password reset: each sends an email |
| `USER_LIMITER` | user | 20 per 10 seconds | every call that costs money, on top of the budget |
| one per kind of data | user | high, such as 600 a minute | data a script could copy, a guest's too |

- **Over the limit:** a 429 (`TOO_MANY_REQUESTS`).
- **A missing binding is a 503,** not a pass: a limiter that is forgotten in
  `wrangler.toml` doesn't open the endpoint.
- **The IP address** is the request's `CF-Connecting-IP`.
- **Firebase has limits of its own.** Its "too many attempts" is a 429 for the app too, not
  a wrong password or an expired session.

The counts are per Cloudflare location, so the limits are rough. The period
can only be 10 or 60 seconds.

## 7. The service endpoints

| endpoint          | method, body                   | for                                                         |
|-------------------|--------------------------------|-------------------------------------------------------------|
| `/guest`          | POST `{humanToken, name}`      | a new guest: limiter, name, daily maximum, Turnstile, Firebase `signUp` |
| `/human_check`    | GET                            | the Turnstile page for the native apps' web view            |
| `/register`       | POST `{email, password}`, a guest's bearer token optional | a new account, or the guest turned into one (`accounts:update`) |
| `/refresh_token`  | POST `{refreshToken}`          | new tokens; also resumes a remembered guest                 |
| `/verify_email`   | POST, bearer token             | Firebase sends the verification email                       |
| `/confirm`        | GET                            | the page the verification link lands on                     |

`AccountResponse` has `isGuest` and `name`, so the app knows what kind of
session it has.

The error codes the apps handle:

| code | name               | when                                                    |
|------|--------------------|---------------------------------------------------------|
| 110  | `GUEST`            | a guest calls what is for registered users (403)        |
| 111  | `HUMAN_CHECK`      | `/guest` without a passed Turnstile check (403)         |
| 112  | `GUEST_NAME`       | a name of fewer than 3 or more than 40 characters, or with other than letters and digits (400) |
| 113  | `EMAIL_NOT_VERIFIED` | a call that costs money, before the email is verified (403) |
| 114  | `TOO_MANY_REQUESTS` | over a rate limit, ours or Firebase's (429)            |
| 115  | `GUEST_QUOTA`      | a guest's quota of today is used up (429)               |

All codes, the general ones too, are in [BLUE_PRINT.md](BLUE_PRINT.md#core).

## 8. The texts

The signed-out screen, top to bottom:

1. **One field,** "Email address or guest name". Log in and Register need a
   valid email address there, Guest a name; a guest name has only letters
   and digits, so the two never mix up.
2. **The buttons:** Log in, Register, Guest.
3. **A small gray explanation** (bodySmall, onSurfaceVariant), of what each
   gives, for example:
   - "Log in as guest to try it out";
   - "Register for everything the app does";
   - "Register to keep what you made, on every device".

The guest screen has the button "Change guest login into email registration",
with the same explanation below it.

## 9. Checklist for another app

- [ ] **Firebase:**
  - the Email/Password provider on (as for every app of the blueprint), and the Anonymous
    provider too;
  - email enumeration protection off;
  - SMTP for the emails (optional).
- [ ] **Turnstile:**
  - one widget per app, with the web host and the worker host;
  - the site key in the web app's build and in `wrangler.toml`;
  - the secret with `wrangler secret put`.
- [ ] **Worker:**
  - `/guest`, `/human_check` and `requireHuman`, which refuses without a secret when deployed,
    and checks the hostname;
  - `/register` that links a guest;
  - `isAnonymous` from the token;
  - no `users` row for an anonymous token that `/guest` didn't make, also not in
    `/refresh_token`;
  - the guards (`requireSession`, `requireUser(allowGuest)`, `requireUser`,
    `requireVerifiedUserId`, `guardRequest`), decided per endpoint;
  - a guest's quota a day and maximum stored;
  - `GUEST_DAILY_MAX`;
  - the limiters of section 6, and one per user where data could be copied.
- [ ] **Database:**
  - `users.is_guest` and `users.name`;
  - no starter grant for a guest, and `GUEST_TOTAL_BUDGET_TICKS` as its budget.
- [ ] **App:**
  - `humanCheckToken`, `hasHumanCheck` and `HumanCheckHost` per platform;
  - `HumanCheckHost()` once at the top of the app;
  - `KnownGuests` for logging out and resuming;
  - the register flow with the guest's token, refreshed first.
- [ ] **Web page:**
  - the `#turnstile` div and the two functions;
  - `@TURNSTILE_SITE_KEY@` replaced at build time;
  - the `android.json` footer.
- [ ] **Deploy:**
  - a signed APK named with its version, copied next to the web app with
    `android.json`;
  - the https check on the service URL (`-Pdeploy`);
  - local runs with `-PturnstileTest` and the test secret.
