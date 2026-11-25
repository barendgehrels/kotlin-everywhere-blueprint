# Setting up the accounts: a tutorial

What a new app of the blueprint needs outside its code: the accounts, keys and settings at Resend,
Firebase, Cloudflare, MapTiler and Anthropic, in the order that works. The examples use the names
of the example app, Slippy ([its SETUP.md](example_slippy_map_tiles/SETUP.md) says where each value
goes in its code); your app has its own.

Keep every key you get in a password manager: most pages show a key only once.

# 0. The addresses of the workers

Firebase and Turnstile ask for the workers' addresses, so choose them first.

* Make a Cloudflare account (free), then Workers & Pages: choose your `workers.dev` subdomain
  (`<account>`). It is in every address, and it can't easily change later.
* Choose a random suffix for each worker, because a `workers.dev` address is public:
  `openssl rand -hex 8`, twice. The two addresses are then
  * the service: `slippy-service-<service-suffix>.<account>.workers.dev`
  * the web app: `slippy-web-<web-suffix>.<account>.workers.dev`
* Copy the example config, and put the names in it (it stays out of git):
  * `cp service/wrangler.example.toml service/wrangler.toml`
  * `cp app/webApp/wrangler.example.toml app/webApp/wrangler.toml`

# 1. Resend (free): the mail server for Firebase's emails

Firebase sends the verification and password reset emails. Its own sender works, but often lands
in spam; with Resend they come from your own domain.

* Add your domain (Domains → Add domain), and add the DNS records it shows at your domain's DNS
  provider. Wait until Resend says "Verified". Without a verified domain, Resend only sends to
  your own address.
* Add an API key (API Keys → Create)
  * give it a name
  * permission: "Sending access" is enough
  * store the key safely! It is shown once
  * you need it in Firebase (next step), as the SMTP password

# 2. Firebase: the accounts

* It needs a Google account
* New project
  * give it a name
  * disable all "Gemini" checkboxes
  * Google Analytics is not needed
* Add app
  * choose Web (the apps never use a Firebase SDK, but the web app gives the Web API key)
  * give it a name
  * skip Firebase Hosting
  * click Register app
  * copy the **project ID** (not the name: it can differ, such as `slippy-1a2b3`) and the
    **API key** (`apiKey`)
  * the project ID goes into `service/wrangler.toml` (`FIREBASE_PROJECT_ID`, a var)
  * the API key goes in as a secret (step 3). It is public, as it is in every app, so a var
    would do; a secret keeps it with the other keys
* Authentication → Sign-in method
  * add Email/Password
  * add Anonymous, for the guests. Its automatic cleanup deletes guests after 30 days without
    use (Firebase may ask you to upgrade to Identity Platform for it). A deleted guest can't be
    resumed: the app then makes a new guest
* Authentication → Settings
  * Authorized domains: add the address of the service,
    `slippy-service-<service-suffix>.<account>.workers.dev`. The verification email's link
    ends on its `/confirm` page (`CONFIRM_URL`), and Firebase refuses to send the email
    (`UNAUTHORIZED_DOMAIN`) for a domain it doesn't know
  * User actions: switch **email enumeration protection off**. With it on, a guest can't become
    a registered user (Firebase answers `OPERATION_NOT_ALLOWED`)
* Authentication → Templates
  * SMTP settings: click Enable
    * sender address: an address on your Resend domain, such as `noreply@example.com`
    * SMTP server: `smtp.resend.com`
    * port: 587
    * SMTP account: `resend`
    * password: your Resend API key
    * security mode: STARTTLS
  * Optional: the sender name and the texts of the emails (Email address verification, Password
    reset)

# 3. Cloudflare: the service, the database and the web app

All commands in `service/`, after `npx wrangler login` once (it opens the browser).

* Create the database
  * `npx wrangler d1 create slippy`
  * you get its id back: enter it in `service/wrangler.toml` (`database_id`)
* Migrate
  * `npx wrangler d1 migrations apply slippy --remote` (and `--local` for `wrangler dev`)
  * a new migration goes first, before the service that needs it is deployed
* Set the vars in `service/wrangler.toml`: `ALLOWED_ORIGIN` (the web app's address, with
  `https://`), `CONFIRM_URL` (the service's address, with `/confirm`), and the Firebase project ID
* Set the secrets
  * Remote, once each (wrangler asks for the value):
    * `npx wrangler secret put FIREBASE_API_KEY`
    * `npx wrangler secret put MAPTILER_API_KEY`
    * `npx wrangler secret put ANTHROPIC_API_KEY`
    * `npx wrangler secret put TURNSTILE_SECRET_KEY` (step 4)
  * Locally: in `service/.env` (in `.gitignore`), for `npx wrangler dev`
  * `npx wrangler secret list` shows which are set (not their values)
* `secret.properties` in the project root (in `.gitignore`), for the apps' build:
  ```
  cloud_service_url=https://slippy-service-<service-suffix>.<account>.workers.dev
  turnstile_site_key=<turnstile-site-key>
  ```
* Deploy: `scripts/deploy_app.sh`
  * it builds the service and the web app, applies the remote migrations, and deploys both,
    each as a worker
  * `npx wrangler deploy` in `service/` (or `app/webApp/`) deploys one of them by hand
* Watch the service's log: `npx wrangler tail` in `service/`. A refused human check, a
  Firebase error or a provider failure says why there, not in the app

# 4. Turnstile in Cloudflare (for guest users)

* In Cloudflare, add Turnstile (Turnstile widget), mode Managed
* Set its hostnames to both workers, and press Save or Update:
  * the web app's, where the browser runs the check
  * the service's, where Android and iOS run it (on its `/human_check` page)
* Its **site key** is public, and goes in two places:
  * `service/wrangler.toml`: `TURNSTILE_SITE_KEY` (for Android and iOS)
  * `secret.properties`: `turnstile_site_key` (for the web app, built into its page)
* Its **secret key** is a secret of the service: `npx wrangler secret put TURNSTILE_SECRET_KEY`.
  The service checks every client's token with it
* Locally, Cloudflare's test keys always pass; the real keys don't work on localhost:
  * in `service/.env`: `TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA` and
    `TURNSTILE_SITE_KEY=1x00000000000000000000AA`
  * the web app: build with `-PturnstileTest`
* A changed hostname takes a minute or two

# 5. MapTiler (free): the map tiles

* Make an account at cloud.maptiler.com
* Account → API keys: the default key works, or create one for this app
* Don't restrict it to HTTP origins: the service calls MapTiler from the worker, without an
  `Origin`, so a key restricted to origins refuses every tile
* `npx wrangler secret put MAPTILER_API_KEY`
* The free plan has a monthly limit of requests; the guests' daily tile quota
  (`GUEST_TILES_PER_DAY`) keeps them from using it up

# 6. Anthropic: the AI calls

* Make an account at console.anthropic.com, and buy some credits (a few dollars is plenty)
* Set a monthly spend limit (Settings → Limits): the budgets in ticks are ours, this one is
  Anthropic's, and the last line of defense
* API keys → Create key; store it safely, it is shown once
* `npx wrangler secret put ANTHROPIC_API_KEY`
* The model is a var (`ANTHROPIC_MODEL`), and needs a price in the database (`model_rates`, in
  the migrations), else it is counted at the highest known price

# 7. The apps

* Android and iOS read `cloud_service_url` from `secret.properties` at build time, so build them
  again after changing it
* iOS on a real iPhone needs your team: `TEAM_ID` in `app/iosApp/Configuration/Config.xcconfig`
  (or Signing & Capabilities in Xcode). The simulator needs none
* The Android release key, and the APK as a download from the web app: see
  [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md) §1
