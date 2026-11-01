# Changelog

## 1.0.0 — 2026-11-01

- Breaking: public money request/response fields require canonical minor-unit strings; JSON numbers are rejected.
- Exact signed 64-bit fiat arithmetic and currency-aware decimal text conversion preserve values above JavaScript’s safe-integer limit.
- See [MIGRATION-1.0.md](MIGRATION-1.0.md) before upgrading.


## 0.1.2

- Use Java SDK 0.2.1 hosted URL contract. New MVC installations default to embedded merchant-site checkout; retained explicit hosted orders keep their original choice.
- Redirect only to backend paymentPageUrl and show review when unavailable; never infer a URL or rotate an attempt. URL and token stay encrypted in the shared ledger.

## 0.1.1

- Add Maven Central developer/SCM metadata and an existing-GPG-agent signing publication with clean repository-layout bundle tasks.
- Derive artifact verification from the release version instead of a hard-coded coordinate.
- No runtime payment, ledger or webhook behavior changed; earlier functional CI acceptance remains the baseline, with this release rebuilt and checked as a publication metadata patch.

## 0.1.0

- Spring Boot 4.1.2 / Java 17+ auto-configuration for Java SDK 0.2.0, approved HTTPS gateway origins, native token-store/transport injection and strict merchant webhook adaptation.
- Explicitly enabled JDBC ledger with native transaction/connection enforcement, row locks, persistent event ID/body-hash collision protection and rollback-safe application callbacks. Bean creation never installs schema.
- Independent storage-key configuration and optional shared CommerceService; no automatic HTTP routes or durable capture/refund/void orchestration.
- Checksum-pinned public SDK source bootstrap, full-cache validation, standalone source build, artifact/POM checks, static analysis, source secret scans and dependency audit.
- Runnable MVC checkout example under samples/spring-boot with its own native auth/CSRF/order lifecycle and verification suite.
- Security audit pins the test-only MariaDB Connector/J to 3.5.11; affected BOM driver 3.5.10 is not used in native acceptance.
