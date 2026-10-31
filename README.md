<p align="center"><picture><source media="(prefers-color-scheme: dark)" srcset=".github/assets/lunixi-logo-dark.svg"><img src=".github/assets/lunixi-logo.svg" alt="Lunixi" width="240"></picture></p>

# Lunixi Spring Boot Starter

[![CI](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/actions/workflows/ci.yml) · [Türkçe](README.tr.md) · [Guide](https://docs.lunixi.com/sdks/spring-boot) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md)

**0.1.0** connects **Java SDK 0.2.0** to **Spring Boot 4.1.2 / Java 17+**. It provides native auto-configuration, strict raw-body merchant webhook adaptation and an opt-in transactional JDBC ledger. It uses Spring/SDK dependencies; it is not a dependency-free package.

The library creates **no HTTP route**, performs **no automatic DDL**, and does not supply customer authentication, storefront controllers or a durable capture/refund/void queue. The [runnable MVC sample](samples/spring-boot) is a separate application with its own native order/authentication lifecycle. [Java SDK payment/commerce guide](https://github.com/Lunixi-Payment/lunixi-java/blob/main/docs/commerce.en.md).

## Source installation and Maven coordinates

Coordinates: `com.lunixi:lunixi-spring-boot-starter:0.1.0`, requiring `com.lunixi:lunixi-java-sdk:0.2.0`. A GitHub tag, composite source build or local Maven artifact does **not** imply Maven Central availability. Until both exact coordinates are published there, use the reviewed source:

```sh
git clone --depth 1 --branch v0.1.0 https://github.com/Lunixi-Payment/lunixi-spring-boot-starter.git
cd lunixi-spring-boot-starter
python3 tools/bootstrap-sdk.py
gradle build
```

Requires JDK 17+, Gradle 9.7.1 and Python 3.9+. `tools/sdk-source.json` fixes the SDK repository, full commit, version and SHA256. Bootstrap downloads only that immutable HTTPS archive, verifies checksum, rejects archive links/path escapes and atomically installs `.sdk-cache/lunixi-java`. Every default Gradle settings evaluation runs offline verification of the archive **and every cached source file**, including additional/missing/tampered files. No mutable branch or latest-tag download is used. Gradle never silently downloads SDK source; run bootstrap explicitly first. Keep the manifest unchanged unless the reviewed SDK pin is intentionally upgraded.

To include this starter from a Gradle application before registry publication:

```groovy
// Application settings.gradle; path points to the reviewed starter source.
includeBuild('/absolute/path/to/lunixi-spring-boot-starter')
```

```groovy
// Application build.gradle
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-spring-boot-starter:0.1.0' }
```

The starter's verified included build supplies SDK 0.2.0. For an explicit local Maven workflow, first install the bootstrapped SDK and then the starter:

```sh
gradle -p .sdk-cache/lunixi-java publishToMavenLocal -PlunixiUnsigned
gradle publishToMavenLocal
```

Consumers then use `mavenLocal()` before `mavenCentral()` (Maven itself reads its local repository automatically). These commands do not publish remotely. `-PlunixiSdkSource=/absolute/reviewed-sdk` is an **explicit development override** of immutable bootstrap; review that source yourself. CI and release acceptance use the default verified public pin. See [bootstrap/security verification](docs/verification.md).

## Server configuration

Configure operator-owned protected values, never browser/admin arbitrary gateway input:

```yaml
lunixi:
  base-url: https://api-gateway.lunixi.com
  allowed-origins:
    - https://api-gateway.lunixi.com
  key-id: ${LUNIXI_KEY_ID}
  private-key: ${LUNIXI_PRIVATE_KEY}
  environment: TEST
  timeout: 30s
  webhook-secret: ${LUNIXI_WEBHOOK_SECRET}
  storage-key: ${LUNIXI_STORAGE_KEY}
  jdbc-ledger-enabled: true
  table-prefix: lunixi_
```

| Setting | Contract |
|---|---|
| `base-url`, `allowed-origins` | Required exact approved HTTPS origin; no userinfo/path/query/fragment |
| `key-id`, `private-key` | Registered server-only Ed25519 key; upload only public key to Lunixi |
| `environment` | TEST by default; retain original environment/profile for existing attempts |
| `timeout` | 30 seconds by default |
| `webhook-secret` | Enables `MerchantWebhooks`; separate endpoint secret |
| `storage-key` | Enables `SecretBox`; base64 of an independent backed-up random 32-byte key |
| `jdbc-ledger-enabled` | False by default; true requires a native JDBC DataSource |
| `table-prefix` | `lunixi_` by default, strictly validated SQL identifier prefix |

Register your business merchant identity in the server-owned `OrderSnapshot`; the starter does not invent a merchant ID property or infer browser ownership. Custom `LunixiClient`, `TokenStore`, `HttpTransport`, `LedgerStore` and other documented bean types can replace corresponding defaults. Network retries default to zero in the starter. Configuration diagnostics redact credential values.

## Explicit schema and native transaction contract

`JdbcLedgerStore` inspects the configured DataSource and opens the transaction manager for **that exact DataSource**. Dialect paths exist for H2, PostgreSQL and MySQL/MariaDB; local/native acceptance verifies H2 and MariaDB 11. Validate exact production driver/schema/dialect behavior before deploying another database. MySQL/MariaDB native order tables and extension tables must be InnoDB; the starter creates only its own tables as InnoDB when explicitly asked and never migrates native engines.

Perform an operator-reviewed migration/startup administration step outside any active transaction:

```java
// Explicit administration code, never a webhook or ordinary bean side effect.
ledger.install();
```

This installs only `<prefix>attempts` and `<prefix>events`. Native order/stock/history/outbox tables belong to your application. Use the same DataSource/connection for their updates inside `CommerceService.reconcile`'s callback. Ledger methods reject ambient/nested transactions and record access that escapes its transaction. Reservations must commit **before** gateway dispatch; do not wrap `CommerceService.start` in a controller-wide `@Transactional` block.

Event identity/body hash is durable. Replays apply once; a reused event ID with different authenticated content fails. Inbox and native writes roll back together. Your native application must still make repeated unchanged GET evidence idempotent, preserve manual/fraud holds, lock/recheck current persisted totals and queue notifications for postcommit delivery. Never send mail/shipping during the payment transaction. Retain financial history, keys and receipt IDs through the reconciliation/refund period.

## Strict webhooks and checkout

`MerchantWebhooks.verify(byte[], Map<String,List<String>>)` rejects oversized input (1 MiB), invalid UTF-8 and duplicate signature headers before strict SDK merchant-envelope verification. Preserve raw bytes and delivery headers through the proxy. Locate reservations only from trusted stored bindings or strictly parsed signed candidates. `CommerceService` requires a strict `payment.*` event/raw hash and obtains authenticated payment/split GET evidence; browser status fields never mark paid. Send 2xx only after native commit and return retryable failure otherwise.

Partial capture, incomplete multi-card parents, unknown/FAILED outcomes and native manual/fraud holds do not authorize fulfillment or a fresh key. The SDK persists create reservations, original context/key and a conservative 23-hour create retry fence. **It does not implement a durable financial operation queue:** persist capture/refund/void IDs, exact bodies/times/keys, reject conflicting unresolved operations, enforce a safe replay fence and confirm actual balances yourself. The sample deliberately exposes no financial administration endpoint.

Hosted checkout uses `pay.lunixi.com`; embedded checkout uses the official Lunixi bundle and its object/token/mount contract. Available methods, installments, meal cards, shopping credit, insurance and multi-card options stay managed in Lunixi form settings. PAN/CVV never belongs on these servers. Hosted/split/3DS browser returns POST: only an informational bounded POST may bypass checkout CSRF and 303 to an owned same-origin GET, without trusting posted fields. Native checkout/admin requests still require authentication/ownership/CSRF. See the sample for runnable routes and secure cookies/CSP.

## Verification

```sh
python3 tools/bootstrap-sdk.py
python3 -m unittest discover -s tools/tests -v
gradle --no-daemon -I tools/verification.init.gradle clean test spotbugsMain resolvedAuditDependencies jar sourcesJar javadocJar generatePomFileForStarterPublication
python3 tools/check-artifacts.py
python3 tools/audit-dependencies.py
python3 tools/check-secrets.py
```

For the real MariaDB test, explicitly supply `LUNIXI_TEST_DB_FILE` pointing to a private disposable local fixture JSON with host/port/database/username/password; schema name must start `lunixi_`. The test creates unique-prefixed temporary InnoDB tables and drops only those tables. Never use production credentials. Root CI provisions an isolated MariaDB schema and requires this test to execute; a run without an explicit fixture skips only that integration test. H2 rollback/concurrency/receipt tests use actual native Spring JDBC. The sample has its own MVC/HTTP/browser/dependency suite. No live payment or marketplace certification is claimed.

License: [MIT](LICENSE). [Contributing](CONTRIBUTING.md).
