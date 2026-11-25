# A domain of its own for a Cloudflare Worker

How to move an app from its `*.workers.dev` address to a domain of its own, such as
`myapp.example.com`, with the stack of the [blueprint](BLUE_PRINT.md): a Cloudflare Worker in
Kotlin (Kotlin/JS) that is both the service and the host of the web app (its static assets),
with Firebase Authentication called by the worker.

The idea:

- **One worker, two addresses.** The worker gets the domain as a custom
  domain, and keeps its `workers.dev` address. Nothing that knows the old
  address breaks: installed apps, links in emails sent before.
- **The web app lives on the domain.** A visit to the root of the old
  address is redirected there.
- **Cloudflare does DNS and the certificate** by itself, at the first deploy.

## 1. The domain in Cloudflare

The domain must be a zone in the same Cloudflare account as the worker:

- **Bought at Cloudflare** (Registrar): it is a zone already.
- **Bought elsewhere:** add it as a site in the dashboard (the free plan is
  enough) and set the nameservers at the registrar to the two Cloudflare
  gives. It works once Cloudflare says the zone is active.

One domain can carry many apps, one subdomain each, such as
`atlas.example.com` and `quiz.example.com`.

Don't make a DNS record for the subdomain yourself: the custom domain makes
its own, and an existing record of that name makes the deploy fail.

## 2. `wrangler.toml`

```toml
name = "myapp-0b7c54c50eed5943"

# The custom domain: Cloudflare makes the DNS record and the certificate at
# the first deploy
routes = [{ pattern = "myapp.example.com", custom_domain = true }]
# And keep the workers.dev address: installed apps and sent emails use it
workers_dev = true

[assets]
directory = "../app/webApp/build/dist/wasmJs/productionExecutable"
binding = "ASSETS"
# The worker sees "/" first, to redirect the old address (see 3)
run_worker_first = ["/"]

[vars]
# Where the web app lives; "/" on any other host redirects here
WEB_URL = "https://myapp.example.com"
# Where the link in the verification email lands (see 5)
CONFIRM_URL = "https://myapp.example.com/confirm"
```

- **`custom_domain = true`**, not a plain route: a route needs a DNS record
  and a certificate of your own, a custom domain makes both. It takes the
  whole host (no paths, no wildcards), which is what an app wants.
- **`workers_dev = true`** explicitly. Once there are `routes`, don't rely on
  the default.
- **The first deploy** (`npx wrangler deploy`) prints both addresses. The
  certificate can take a few minutes; until then the browser warns.
- **With wrangler environments** each environment has its own `routes`,
  `workers_dev` and `[vars]`; none of them is inherited from the top level.

## 3. The root redirect

Static assets are normally served before the worker runs, so the worker
couldn't redirect `/`. `run_worker_first = ["/"]` sends only the root to the
worker first; every other file still goes out as an asset, without a worker
call.

The worker's `GET /` (`handleRoot` in `Router.kt`):

```kotlin
val url = URL(request.url)
val webUrl = env.WEB_URL?.takeIf { it.isNotBlank() }?.let { URL(it) }
if (webUrl != null && webUrl.host != url.host) {
    // Moved for good; the query goes along (?language=nl)
    val headers = Headers()
    headers.append("Location", webUrl.origin + "/" + url.search)
    return Response(null, ResponseInit(status = 301, headers = headers))
}
return env.ASSETS?.fetch(request)?.await()
    ?: jsonResponse(HealthResponse(...))   // no assets: the health check
```

- **On the domain** it serves `index.html` as before.
- **On the `workers.dev` address** it answers a 301 to the domain. Only
  the root: the API (`/login`, …) keeps answering on both
  addresses, so an app that still calls the old one goes on working.
- **Without `WEB_URL`** (local development, `localhost:8787`) there is no
  redirect.

## 4. The apps

- **The service URL of the apps** (`cloud_service_url`) can be the domain
  too. New builds then call it; the ones installed keep calling the
  `workers.dev` address, which stays.
- **The web app** calls the service at that URL. On the same host as the
  page there is no CORS at all; on another host the worker's CORS answer
  covers it: `Access-Control-Allow-Origin` with the web app's origin (or `*`), and
  `Authorization` allowed.
- **The APK download** (see [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md)) comes along: it is
  one of the static files.

## 5. What else knows the address

Every place that has a host in it: check them all when the address changes.

- **The allowed origins** (`ALLOWED_ORIGIN`, when the worker answers CORS for named
  origins only): add the domain. The human check uses the same list (see
  [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md)).
- **Turnstile:** the widget's hostnames in the
  Cloudflare dashboard need the domain (the web app), and the host the
  native apps call for `/human_check`. As long as installed apps call the
  `workers.dev` address, keep that one in too.
- **Firebase, the email links:**
  - `CONFIRM_URL` is the `continueUrl` of the verification email, so the
    link ends on the domain's `/confirm`.
  - Its domain must be in Firebase's **authorized domains** (Authentication
    → Settings → Authorized domains), else `sendOobCode` refuses it
    (`UNAUTHORIZED_DOMAIN`).
  - Keep the `workers.dev` address there too: emails sent before have it in
    their links.
  - Test it after the move: register with a fresh address (a `+test`
    variant of your own works with most providers), and click the link. It
    is easy to forget, because it is rarely done.
- **The sender of the emails**, only if it is on the domain
  (`noreply@example.com`). Then the receiving mail servers check whether the
  domain allows the relay to send for it: SPF and DKIM, DNS records (in
  Cloudflare) that the relay tells you to add. Without them the mails tend to
  end in spam. A sender on the relay's own domain, or a Gmail address, needs
  nothing. To see how it goes: open a received verification email in Gmail,
  "Show original", which says `SPF: PASS` and `DKIM: PASS`, or not.
- **The deploy check:** the service URL must be https (`-Pdeploy`, see
  [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md)); the domain is.
- **Texts and documents** with the old address: the README, the store
  listing, links you sent around.

## 6. Checklist for another app

- [ ] The domain is a Cloudflare zone (bought there, or its nameservers
      moved), active.
- [ ] No DNS record of the subdomain yet.
- [ ] `wrangler.toml`:
  - `routes = [{ pattern = "<host>", custom_domain = true }]`;
  - `workers_dev = true`;
  - `run_worker_first = ["/"]` in `[assets]`;
  - `WEB_URL` and `CONFIRM_URL` on the domain.
- [ ] The worker redirects `/` to `WEB_URL` on any other host, with the query.
- [ ] `npx wrangler deploy`; both addresses answer, the certificate is there.
- [ ] `ALLOWED_ORIGIN`: the domain added.
- [ ] Turnstile: the domain in the widget's hostnames (the old one too
      while it is called).
- [ ] Firebase: the domain in the authorized domains, the old one kept.
- [ ] Register with a fresh address, and click the link in the email.
- [ ] The sender on the domain: SPF and DKIM in its DNS.
- [ ] The apps' service URL on the domain, for the next builds.
- [ ] The old address in texts and documents replaced.
