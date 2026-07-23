# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Spring Boot backend for a personal trading tool built on Zerodha's Kite Connect API. It's a rewrite of a
previous FastAPI app (`kite_app`) that has since been removed from this repo — do not reference or resurrect
that Python code. This app sits behind a Cloudflare Tunnel (see `~/.cloudflared/config.yml`), which is why the
server port (8000) is fixed rather than left at the Spring default. A separate `kite-frontend` project (sibling
directory) is the frontend for this API.

This is a work-in-progress personal project, not a hardened multi-tenant product. Favor small, direct
implementations over defensive engineering for cases that can't happen yet.

## Commands

```bash
./mvnw spring-boot:run          # run the app locally (port 8000)
./mvnw test                     # run the full test suite
./mvnw test -Dtest=OrderControllerTest                      # run a single test class
./mvnw test -Dtest=OrderControllerTest#buy_marketOrder_placesOrderWithMarketProtection  # run a single test method
./mvnw compile                  # compile only
./mvnw package                  # build the jar
```

Required environment variables (see `src/main/resources/application.properties`): `KITE_API_KEY`,
`KITE_API_SECRET`, `DB_USERNAME`, `DB_PASSWORD`, and optionally `FRONTEND_URL` (defaults to
`http://localhost:5173`). The app expects a local Postgres database named `kite` on the default port.

Tests use an in-memory H2 database via `@DataJpaTest`, so `./mvnw test` does not require Postgres to be running.

## Architecture

Package layout under `com.ayush.kite` (each has a `package-info.java` with a one-line description — check
those first when orienting in an unfamiliar package):

- **`auth`** — App-level login (username/password, BCrypt-hashed, our own `users` table) is entirely separate
  from the Kite login (OAuth-style token exchange). `AuthController` handles app signup/login/logout via
  `HttpSession` (session attribute `userId`). `KiteSessionService` computes when a Kite access token expires:
  Kite flushes tokens daily between 5:00–7:30 AM IST, so `computeExpiresAt` pins expiry to the next 7:30 AM IST
  boundary after login. `KiteSession` is upserted (one row per user, no history) on every successful `/callback`.
- **`controller`** (note: lowercase class name `controller`, in its own package with no `package-info.java`) —
  Kite's OAuth dance: `/login` requires an app session and redirects to Kite's login URL, round-tripping our
  own `userId` via `redirect_params` since Kite echoes that back untouched; `/callback` exchanges
  `request_token` for an access token via the Kite SDK and redirects to `{frontendUrl}/dashboard`. Also owns
  `/health`.
- **`client`** — `KiteClientFactory` builds a per-request `KiteConnect` SDK instance from the caller's stored
  Kite session, throwing 401 if the user hasn't completed the Kite login. Order/GTT endpoints get a fresh
  client per call rather than caching one.
- **`orders`** — `OrderController` covers: placing market/limit buy orders (`/orders/buy`), Kite's fill
  postback webhook (`/postback`, HMAC-SHA256-checksummed against `apiSecret` — reject on mismatch), order
  status lookup, and GTT (Good-Till-Triggered) OCO target/stoploss exits computed off the order's actual fill
  price fetched from the live quote. `GttPricing` is a pure static-method helper (tick-size rounding to 0.05,
  target/SL price math) — keep new pricing logic there and unit-tested directly, no mocking needed.
- **`store`** — JPA-backed persistence for orders (`OrderRecord`/`OrderRepository`, wrapped by `OrderStore`)
  and GTT triggers (`GttRecord`/`GttRepository`). Note the `store` package-info still says "in-memory order
  tracking" — that's stale; orders are Postgres-backed (see `spring.jpa.hibernate.ddl-auto=update`, which
  auto-migrates the schema from entities — fine for now, but should become Flyway/Liquibase before this holds
  real user data).
- **`config`** — `WebConfig` sets up CORS for `/auth/**` and `/orders/**`, scoped to `app.frontend.url` with
  credentials allowed (required for session cookies across origins).

### Cross-cutting patterns worth matching

- Every controller method that needs the caller's identity re-checks the `HttpSession` attribute `userId`
  itself via a local `requireLoggedIn` helper (not a shared filter/interceptor) — there's no Spring Security
  filter chain in this project, only `spring-security-crypto` for BCrypt.
- Kite SDK failures (`KiteException`, `JSONException`, `IOException`) are caught at the controller boundary and
  turned into `ResponseStatusException` with an appropriate HTTP status — never let SDK exceptions leak, and
  never include stack traces in responses (`server.error.include-stacktrace=never`, set deliberately).
- Ownership checks return 404 (not 403) when a resource exists but belongs to a different user, e.g.
  `getOrder_ownedByDifferentUser_throwsNotFound` — avoids confirming a resource's existence to a non-owner.
- Controllers are tested directly (constructed and called as plain objects, `HttpSession`/SDK clients mocked
  with Mockito) rather than through `MockMvc`; persistence-touching tests use `@DataJpaTest` with real
  repositories autowired in, so schema/entity mapping is exercised for real. Follow this pattern for new tests.
