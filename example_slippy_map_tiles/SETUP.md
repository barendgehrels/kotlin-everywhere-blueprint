# Setting up Slippy

What to create once, outside the code, and where its values go. The code has placeholders in
`<angle brackets>` for each of them.

## 1. Accounts and keys

- **Firebase** (console.firebase.google.com): a new project.
  - Authentication → Sign-in method: **Email/Password** on, and **Anonymous** on (for guests).
  - Authentication → Settings → User actions: **email enumeration protection off** (else a guest
    can't become a registered user).
  - Authentication → Templates → SMTP settings: your SMTP relay (Resend).
  - Project settings: the **Web API key** and the **project ID**.
- **MapTiler** (cloud.maptiler.com): an API key.
- **Anthropic** (console.anthropic.com): an API key.
- **Cloudflare:** `npx wrangler login` once, then the database:
  ```
  cd service && npx wrangler d1 create slippy
  ```
- **Turnstile** (Cloudflare dashboard → Turnstile): one widget, mode Managed, with these two
  hostnames:
  - `slippy-web-<web-suffix>.<account>.workers.dev` (the web app);
  - `slippy-service-<service-suffix>.<account>.workers.dev` (the service: the native apps' check
    runs on its `/human_check` page).

`<account>` is your workers.dev subdomain (Workers & Pages → your subdomain). The suffixes are
random, because a workers.dev address is public: `openssl rand -hex 8`, one for each worker.

## 2. Where the values go

The worker config with your values stays out of git, as `secret.properties` does. Copy the
examples once, and fill in their `<placeholders>`:

```
cp service/wrangler.example.toml service/wrangler.toml
cp app/webApp/wrangler.example.toml app/webApp/wrangler.toml
```

A change of a var, a limiter or a comment goes into the example too, without your values.

| Value | Where |
|---|---|
| Firebase Web API key | `npx wrangler secret put FIREBASE_API_KEY` (public, but kept with the other keys) |
| Firebase project ID | `service/wrangler.toml`: `FIREBASE_PROJECT_ID` |
| `<account>`, the suffixes | `service/wrangler.toml`: `name`, `ALLOWED_ORIGIN` and `CONFIRM_URL`; `app/webApp/wrangler.toml`: `name` |
| D1 database id | `service/wrangler.toml`: `database_id` |
| Turnstile site key | `service/wrangler.toml`: `TURNSTILE_SITE_KEY`, and `secret.properties` |
| Turnstile secret key | `npx wrangler secret put TURNSTILE_SECRET_KEY` (in `service/`) |
| MapTiler key | `npx wrangler secret put MAPTILER_API_KEY` |
| Anthropic key | `npx wrangler secret put ANTHROPIC_API_KEY` |

`secret.properties`, in the project root (in `.gitignore`):

```
cloud_service_url=https://slippy-service-<service-suffix>.<account>.workers.dev
turnstile_site_key=<turnstile-site-key>
```

## 3. The database

```
cd service
npx wrangler d1 migrations apply slippy --local
npx wrangler d1 migrations apply slippy --remote
```

A new migration goes first, before the service that needs it is deployed.

## 4. Running locally

1. `cp service/.env.example service/.env`, and fill in `FIREBASE_API_KEY`, `MAPTILER_API_KEY` and `ANTHROPIC_API_KEY`.
   The Turnstile test keys in it always pass.
2. The service: `./gradlew :service:build`, then `cd service && npx wrangler dev` (port 8787).
3. The apps against it, with `-Pcloud_service_url=http://localhost:8787 -PturnstileTest`:
   - desktop: `./gradlew :app:desktopApp:run -Pcloud_service_url=http://localhost:8787`
   - web: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun -Pcloud_service_url=http://localhost:8787 -PturnstileTest`
   - Android emulator: the same property; `localhost` becomes `10.0.2.2` there.
   - iOS simulator: put the property in `gradle.properties` for the Xcode build, or use the
     deployed service.

Startup option: `theme` (`dark` or `light`): `--theme dark` (desktop), `-theme dark` (iOS),
`?theme=dark` (web), `--es theme dark` (Android).

## 5. Deploying

```
scripts/deploy_app.sh
```

It builds the service and the web app, applies the remote migrations, and deploys both workers.

Later: the Android release key, and the signed APK as a download from the web app
(BLUE_PRINT_GUEST §1).
