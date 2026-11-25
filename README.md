# kotlin-everywhere-blueprint
A blueprint for Kotlin Multiplatform apps with their own Kotlin service on Cloudflare: shared types, authentication, AI calls and guests. Written so that an AI coding agent can build a new app from it.

## What it is

One architecture for an app on Android, iOS, the web and the desktop (Compose Multiplatform),
together with its own service: a Cloudflare Worker, also in Kotlin, compiled to JavaScript. The
apps and the service share their transfer types, error codes and validation, so the two sides
always agree.

This repository holds the documents, not a code template: the rules, the patterns, short code
examples and checklists. They come from apps that are built and used this way, among them
[Babel Magic](https://babelmagic.uk), a language-learning app that uses AI to correct, paint and
explain (closed source).

## Why

Most apps with AI need the same parts: a login, tokens that stay valid, API keys that
never leave the server, a budget per user, guests who can try the app without an account, and a
service that is cheap to run. Doing that well takes many small decisions. This blueprint writes
them down once, so that the next app starts from them rather than from scratch.

Kotlin on both sides means one language and one contract. Cloudflare means a service that starts
on the free tier, without servers to manage.

## What you can do with it

- **Build a new app from it:** give the documents to an AI coding agent, together with what your
  app should do. The names, layers and checklists are explicit, so the agent has little to guess.
- **Check an existing app against it:** the checklists say what to look for before calling a
  screen or an endpoint done.
- **Take one part:** guests and the human check, or a domain of its own for a worker, each stand
  on their own.

## The documents

- [BLUE_PRINT.md](BLUE_PRINT.md): the architecture. The app (container, stores, gateways, view
  models, screens), the shared core, and the service on Cloudflare (router, handlers,
  authentication, budgets, AI calls, data).
- [BLUE_PRINT_GUEST.md](BLUE_PRINT_GUEST.md): guests without an account, the human check
  (Cloudflare Turnstile) on the web, Android and iOS, rate limits, and the Android app as a download from
  the web app.
- [BLUE_PRINT_DOMAIN.md](BLUE_PRINT_DOMAIN.md): moving the worker from its `workers.dev` address
  to a domain of its own, and everything else that knows the address.

## Status

In progress: the documents follow the apps, and change with them. The versions they were last
checked with are at the end of [BLUE_PRINT.md](BLUE_PRINT.md#versions).

## License

[MIT](LICENSE)
