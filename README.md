<p align="center"><picture><source media="(prefers-color-scheme: dark)" srcset=".github/assets/lunixi-logo-dark.svg"><img src=".github/assets/lunixi-logo.svg" alt="Lunixi" width="240"></picture></p>

# Lunixi Spring Boot Starter

**1.0.0 uses the canonical money-string API.** Historical 0.x artifacts do not implement this contract. The source bootstrap uses a reviewed immutable Java SDK 1.0.1 pin; Maven Central availability is verified separately before registry installation. See [migration](MIGRATION-1.0.md).

Historical release record: **Spring Boot Starter 0.1.2** and **Java SDK 0.2.1** are published on Maven Central. Their release files were verified byte-for-byte against the signed bundle on 2026-10-31. New installations default to merchant-site embedding. Hosted checkout uses only backend paymentPageUrl; missing legacy URLs remain on hold without a new payment/key. Retained attempts keep their original explicit mode/body/key.



[![CI](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/actions/workflows/ci.yml) · [Türkçe](README.tr.md) · [Guide](https://docs.lunixi.com/sdks/spring-boot) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md)

**1.0.0** connects **Java SDK 1.0.1** to **Spring Boot 4.1.2 / Java 17+**. It provides native auto-configuration, strict raw-body merchant webhook adaptation and an opt-in transactional JDBC ledger. It uses Spring/SDK dependencies; it is not a dependency-free package.

The library creates **no HTTP route**, performs **no automatic DDL**, and does not supply customer authentication, storefront controllers or a durable capture/refund/void queue. The [runnable MVC sample](samples/spring-boot) is a separate application with its own native order/authentication lifecycle. [Java SDK payment/commerce guide](https://github.com/Lunixi-Payment/lunixi-java/blob/main/docs/commerce.en.md).

## Build from reviewed public source

Use JDK 17+, Python 3.9+ and Gradle 9.7.1. The committed `tools/sdk-source.json` pins Java SDK 1.0.1 to its reviewed public commit and downloaded codeload archive SHA-256. An incomplete pin fails before any download.

```sh
python3 tools/bootstrap-sdk.py
gradle test
```

Every default Gradle build verifies the pinned archive and complete cached source tree offline. A changed, missing or additional source file fails closed. No moving branch or implicit SDK download is used. For explicit development only, `-PlunixiSdkSource=/absolute/path/to/reviewed/java` selects a reviewed checkout; its version must match `lunixiSdkVersion`. CI/release acceptance uses the public pin.

Once the 1.0.0 release is verified on Maven Central, application consumers use:

```groovy
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-spring-boot-starter:1.0.0' }
```

Public source, local Maven installation and registry publication are separate facts. The bootstrap does not publish either package.

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


## Maven Central release bundle

Maintainers with the existing signing key unlocked in their GPG agent can run:

```bash
gradle centralBundle -Psigning.gnupg.keyName=YOUR_PUBLIC_KEY_FINGERPRINT
```

Output: `build/central/lunixi-spring-boot-starter-1.0.0-bundle.zip`. It contains the library, sources, Javadoc, POM, checksums and detached signatures in Maven layout. `-PlunixiUnsigned` is for local layout diagnostics only and cannot produce a publishable Central bundle. Upload and registry availability remain separate release steps.

See [the money-string migration](MIGRATION-1.0.md) before upgrading from 0.x. Registry publication must be verified independently of source export or bundle generation.
