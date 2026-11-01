<p align="center"><img src="src/main/resources/static/assets/lunixi.svg" width="240" alt="Lunixi"></p>

# Spring Boot checkout example

1.0.1 source uses the canonical money-string contract and Java SDK 1.0.1. Public source and Maven Central publication are verified separately. New installations default to merchant-site embedding. Hosted checkout uses only backend paymentPageUrl; missing legacy URLs remain on hold without a new payment/key. Retained attempts keep their original explicit mode/body/key.



[English](README.md) · [Türkçe](README.tr.md) · [Guide](https://docs.lunixi.com/en/sdks/spring-boot) · [Source](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot)

A runnable **Spring Boot 4.1.2 / Java 17+** MVC application using **Lunixi Spring Boot Starter 1.0.1 / Java SDK 1.0.1**. Native Spring Security JDBC login, CSRF and owned-order checkout connect to hosted or embedded Lunixi form. Exact raw signed webhooks and authenticated payment reads update native H2 orders, event inbox and an encrypted durable outbox in the same Spring JDBC transaction.

Two explicitly initialized demo users each own a server-side 19.90 TRY order. The browser cannot supply the amount or customer identity. The application collects no card data. Alternative methods, split cards, installments and insurance remain configured in Lunixi's payment form. Authentication, money validation, split-parent settlement proof, encryption and retry fences belong to the shared SDK/Starter.

## Build from public source

Requirements: JDK 17+, Gradle compatible with your installed JDK, and Node/Python 3 for the extra verification tools. Tested with Gradle 9.7.1; production classes target Java 17. This example uses explicit Gradle composite builds from reviewed source checkouts; it does not depend on an unverified Maven Central release.

```bash
git clone https://github.com/Lunixi-Payment/lunixi-spring-boot-starter.git
git clone https://github.com/Lunixi-Payment/lunixi-java.git
cd lunixi-spring-boot-starter/samples/spring-boot
# Choose reviewed release commits/tags in both source checkouts before building.
gradle -PlunixiStarterSource=../.. -PlunixiSdkSource=../lunixi-java test installDist browserContract dependencyInventory
```

The SDK path is resolved by the starter's included build, relative to the starter root. For arbitrary layouts pass absolute paths for both source properties. No checkout or download runs implicitly. The starter and SDK coordinates are substituted by the explicit local source builds. Application dependencies are pinned in `gradle.lockfile`.

## Configure and run

Copy `.env.example` to this example's ignored `.env`, fill the registered TEST credentials and independent secrets, then explicitly load this **shell environment file**. No parent directory environment file is loaded.

```bash
cp .env.example .env
# Generate an independent storage key; do not derive it from an API/webhook/DB secret.
python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())'
set -a
. ./.env
set +a
build/install/lunixi-spring-checkout-example/bin/lunixi-spring-checkout-example
```

| Variable | Requirement |
| --- | --- |
| `LUNIXI_MERCHANT_ID` | Registered merchant identity |
| `LUNIXI_ENVIRONMENT` | `TEST` or `LIVE`, matching the actual key and webhook connection |
| `LUNIXI_KEY_ID`, `LUNIXI_PRIVATE_KEY` | Registered server-only signing credentials; PEM may contain actual newlines |
| `LUNIXI_WEBHOOK_SECRET` | Merchant webhook signing secret |
| `LUNIXI_STORAGE_KEY` | Independent base64-encoded 32-byte token/outbox encryption key |
| `LUNIXI_JDBC_URL`, `LUNIXI_JDBC_USER`, `LUNIXI_JDBC_PASSWORD` | Explicit private H2 database URL and credentials |
| `LUNIXI_PUBLIC_BASE_URL` | Operator-configured public HTTPS callback base URL |
| `LUNIXI_CHECKOUT_MODE` | `embedded` (default) or `hosted` |
| `LUNIXI_PREAUTH` | `true` for authorization first; otherwise `false` |
| `LUNIXI_LOCALE` | Initial order/form locale: `en` or `tr` |
| `LUNIXI_INITIALIZE_DISPOSABLE` | Explicit `true` only to initialize this isolated TEST example |
| `DEMO_ALICE_PASSWORD`, `DEMO_BOB_PASSWORD` | Different explicit passwords of at least 16 characters |
| `LUNIXI_SECURE_COOKIES` | Defaults to `true`; `false` is only for isolated loopback HTTP development |

There are no default credentials, database URL, signing key or automatic schema initialization. The explicit disposable initializer is H2-only, rejects LIVE mode and outer transactions, and creates the ledger, native users/orders and outbox without deleting financial history. Set `LUNIXI_INITIALIZE_DISPOSABLE=true` explicitly for the first TEST start, then turn initialization off. Keep the H2 files outside any web root and retain the independent storage key across restarts/upgrades/backups. This runnable adapter and SQL dialect are **H2 only**; validate a native schema/ledger adapter and same-DataSource transaction behavior before choosing another production database.

Open `http://localhost:8080/login` and use `alice` or `bob` with your supplied password. For actual TEST checkout use HTTPS/TLS termination or a TLS tunnel and set `LUNIXI_PUBLIC_BASE_URL` to that address. Register **`https://your-host/webhooks/lunixi`** in Lunixi. Production needs HTTPS, secure HttpOnly SameSite=Lax cookies, trusted host/proxy configuration and an application-specific identity policy. Errors never render gateway response bodies or stack traces.

## Routes and authority

| Route | Native boundary |
| --- | --- |
| `GET/POST /login` | Spring Security JDBC password hash, native CSRF and session rotation |
| `POST /logout` | Native authenticated CSRF-protected logout |
| `GET /orders` | Current user's own native orders |
| `POST /payments/{id}` | Customer role, CSRF and current server-side order ownership |
| `POST /payments/{id}/return` | Stateless informational form POST; fixed same-origin 303 to the owned GET |
| `GET /payments/{id}/return` | Authenticated owner only; query/form payment fields have no authority |
| `POST /webhooks/lunixi` | Raw server-to-server signed webhook plus authenticated payment GET |

Only the exact webhook POST and informational return POST are CSRF-exempt. The return adapter reads at most 64 KiB plus one rejection byte and ignores every field, including status and signature. It performs no customer/order lookup, database change or gateway call, and returns the same empty 303 for an unknown order. A cross-site POST may omit SameSite=Lax cookies; the following GET uses normal session and ownership checks. Do not verify a browser return with the webhook secret. Checkout/login/logout remain CSRF-protected.

The gateway origin is fixed at `https://api-gateway.lunixi.com`; the actual key selects TEST/LIVE access without an invented environment field. Hosted mode uses only the exact validated backend `paymentPageUrl`; its token may differ from the embedded token and no URL is reconstructed. Embedded mode uses only `https://cdn.lunixi.com/lunixi-checkout.min.js`, a properly escaped browser token and the official object configuration/mount API. It never renders gateway HTML. Configure CSP for the documented form origins.

## Persistence and settlement

A short native row-locked reservation commits before gateway network I/O. The shared `CommerceService` then persists a stable attempt, exact body and idempotency key using the Starter's `JdbcLedgerStore`. Browser tokens and outbox payloads are authenticated-encrypted by the shared `SecretBox` with an independent key.

Timeouts preserve the same request/key. Unknown create results never authorize automatic replacement. The shared SDK applies a conservative **23-hour replay fence**, before the gateway's 24-hour idempotency-cache expiry; an older uncertain record can be reconciled with authenticated GET/webhook evidence but cannot cause another financial POST. A signed event and matching opaque original attempt/fingerprint can recover a lost create response. `FAILED` without definitive outcome certainty remains review, without a fresh key.

The webhook bounds raw input to 1 MiB before UTF-8 decoding, rejects conflicting repeated signature headers, verifies v2 raw-byte HMAC/timestamp/event identity and signed environment, then performs authenticated GET. Merchant/order/payment identity, currency, exact amount, settlement balances and complete split-parent aggregate evidence must match. Neither a signed status alone nor a browser return marks paid.

The Starter ledger and native `JdbcTemplate` share the same DataSource and Spring transaction manager. Event claim, ledger state, native order and outbox commit together. The application locks and compares the current native order/fingerprint, owner and merchant/environment again inside that transaction. Cancellation, changed identity and backward settlement fail closed. A native SQL constraint failure rolls everything back, allowing the same verified event to succeed after repair.

Authorization and partial capture remain on hold. Manual holds remain review even when settlement is complete. Base order amount stays separate from gross charged/captured/refunded balances when installment fees apply. Only fully authenticated capture creates one encrypted `order.paid` outbox event. Financial history and keys are not automatically removed.

## Reconciliation and postcommit work

Enable the optional read-only background reconciler explicitly:

```bash
# Spring relaxed environment binding selects sample.reconciliation-enabled.
export SAMPLE_RECONCILIATION_ENABLED=true
```

It polls up to 100 reserved orders every minute, with `checked_at` ordering to avoid starving later records. It performs authenticated reads, keeps uncertain/failed orders on hold and never retries an old financial POST. `sample.reconciliation-delay` can change the delay in milliseconds.

`PaidOutbox.claim()` must run **after** the financial transaction commits. It rechecks the current paid/hold state, durably changes `ready` to `sending`, and returns a stable event key and decrypted payload. Connect your own fulfillment/email worker, use that stable key with an idempotent downstream system, then call `acknowledge(key, true)` only after confirmed success. An uncertain result becomes `uncertain`; a crash in `sending` stays held for reconciliation/manual review. Neither is blindly reset/retried. The example does not invent your stock, tax, shipping or external delivery rules.

The SDK provides low-level capture/refund/void calls requiring a persisted idempotency key; it does not provide a durable administration operation ledger in this Java release. Before adding these features, implement and test application operation reservation, actual operation IDs, role/CSRF checks, unresolved-operation serialization, the 23-hour retry fence and authenticated GET settlement confirmation. This customer example exposes no financial administration endpoint.

## Verification

Run these commands from this sample, with the source paths described above:

```bash
gradle -PlunixiStarterSource=../.. -PlunixiSdkSource=../lunixi-java test installDist browserContract dependencyInventory
python3 tools/http-smoke.py
python3 tools/audit-dependencies.py
```

Tests boot real Spring Boot MVC, Spring Security, JDBC and H2. Gateway HTTP responses are deterministic injected transport fixtures, while SDK cryptography/evidence/encryption and native transaction behavior are real. Cases include role/owner/CSRF, no implicit DDL, encrypted tokens, escaped embedded config, anonymous form POST → fixed303 → protected GET, raw-body tamper/replay/environment, fee/partial/FAILED states, SQL constraint rollback and exact-event retry, stable lost-response recovery, expired replay fence and durable postcommit uncertainty.

The separate HTTP smoke starts the installed application with actual Tomcat, JDBC accounts, file H2 and native cookies/session rotation. It sends no valid financial payment POST. `browserContract` executes the actual embedded JavaScript asset against the object/token/mount contract. `audit-dependencies.py` checks resolved Maven coordinates against public OSV and writes an audit report; dependency inventory/reports contain no merchant data. The example has not charged a live Lunixi merchant. Before deployment complete registered TEST-merchant hosted and embedded payment/webhook acceptance on your public HTTPS application.

Primary references: [Spring Boot servlet MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html), [native CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [JDBC authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/jdbc.html), [Spring transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html).
