# Source bootstrap and build verification

The Java SDK 1.0.1 public source pin is recorded in `tools/sdk-source.json`: exact public repository, full commit, expected SDK version and codeload archive SHA256. An incomplete commit/checksum is rejected before network access. Source availability, local Maven installation and Maven Central publication are separate statuses. A source build does not publish any registry package.

Run `python3 tools/bootstrap-sdk.py` explicitly before a default build. Gradle settings always call `--verify` offline for that default source. Checks include archive digest, bounded download/expansion, exact archive root, no links/traversal/duplicate entries, expected SDK version/build files, and a complete file-by-file cache comparison. A process lock serializes concurrent bootstrap, and source becomes visible through an atomic rename only after verification. Generated root Gradle output/cache directories are excluded from source comparison. Tampering fails closed; remove only the disposable `.sdk-cache` and bootstrap again after investigating. `-PlunixiSdkSource` is an explicit development override of this contract; CI/release acceptance do not use it.

`python3 -m unittest discover -s tools/tests -v` checks checksum tamper, traversal/absolute paths/links, duplicate entries, missing/extra/changed cached files, source/cache/lock symlinks, no-network offline behavior, invalid manifest and concurrent installation. Tests synthesize local archives and never override the release manifest with a fake public pointer.

`tools/verification.init.gradle` applies strict javac and SpotBugs Gradle plugin 6.5.11 / engine 4.10.4 to the Starter library only. `-Xlint:-processing` excludes javac's expected warning that runtime Spring annotations are unclaimed by an annotation processor; all other enabled warnings remain fatal. Two exact EI_EXPOSE_REP getter/setter filters cover Spring's intentionally mutable operator-controlled `@ConfigurationProperties` allowlist bean. The gateway URL is validated before an independent SDK profile is built. No package/category/security detector is globally excluded.

`resolvedAuditDependencies` inventories exact external runtime/test Maven artifacts. The OSV audit follows pagination, bounds responses and fails on advisories or incomplete/unavailable results. Only public package coordinates are sent. The source SDK is pinned separately and its own root verification audits its external test tools. The Starter cannot honestly claim zero runtime dependencies; it depends on Spring and the SDK.

The artifact checker inspects actual Java 17/class61 library/source/Javadoc JARs, metadata and POM. SDK source/cache, test classes, private runtime files and application samples must never enter the library JAR. It verifies the exact SDK dependency declared in `gradle.properties` and native Boot auto-configuration registration. Gitleaks scans the full clean source including the runnable sample, preserves default rules and uses no fixture exclusions in this release; injected API/private-key negative controls must be detected.

CI uses SHA-pinned actions and read-only repository permissions. An isolated MariaDB service supplies the optional native ledger test with a task-local credential JSON file; tests touch only their unique-prefixed tables. The workflow requires no skipped library tests in the acceptance job. H2/MariaDB acceptance does not certify every PostgreSQL/MySQL driver/version or arbitrary native order hooks. The runnable sample is H2-specific and has its own native MVC/HTTP/browser checks. No live payment is made in CI.

Primary references: [Gradle composites](https://docs.gradle.org/current/userguide/composite_builds.html), [Spring Boot auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html), [Spring JDBC transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction.html), [SpotBugs 6.5.11](https://github.com/spotbugs/spotbugs-gradle-plugin/releases/tag/6.5.11), [OSV API](https://google.github.io/osv.dev/api/).

The real MariaDB test pins Connector/J 3.5.11 as a test-only dependency instead of the Boot BOM's affected 3.5.10. The [official 3.5.11 release](https://github.com/mariadb-corporation/mariadb-connector-j/releases/tag/3.5.11) fixes the October 2026 driver advisories. The application chooses its own production database driver; the test driver is not a Starter runtime dependency.


## 0.1.1 publication metadata patch

The original 0.1.0 functional CI acceptance is unchanged. Version 0.1.1 adds only Central-required developer/SCM metadata, signing and clean staging/bundle tasks, and a version-derived artifact check. Release preparation rebuilds the Java 17 library/source/Javadoc JARs and checks the generated POM, checksums and detached GPG signatures. Full functional/live tests were not rerun for this metadata-only patch. Registry publication is verified separately from local bundle generation.


## 0.1.2 focused checkout / immutable SDK upgrade

Prepared against Java SDK 0.2.1 public commit `1679f0baa8257e4c06ae7180c98e937a13b67169`, codeload archive SHA256 `5b8f513c0be42ff62f7e588b4d8a6a876f58206ee259aaf9c67b44873ce8ee65`. Default bootstrap and offline verification passed in a clean standalone source copy, with no SDK source override. Changed source, unexpected source file and altered archive controls were rejected, then exact sources restored and reverified. Ten bootstrap/tamper unit tests passed.

Strict library compilation, SpotBugs (zero findings), JAR/source/Javadoc/POM validation and source-secret negative controls passed. The standalone MVC application passed 22 real Spring/Security/JDBC/H2 tests, the actual browser object/token/mount contract and installDist against the public pinned SDK. New tests cover embedded default, retained explicit hosted preference, encrypted backend URL persistence and missing legacy URL without another create/key. Gateway transport was synthetic; no live merchant charge or new full licensed/native platform matrix was run.

## 1.0.0 release preparation with SDK 1.0.1

The bootstrap requires the exact Java SDK version declared by the starter. The final source pin is Java SDK 1.0.1 public commit `f2080a171093c4ca4a457b3c9d710042e6f751a8`, codeload archive SHA-256 `d8ec086133ed8ecffbf2b0556f63725dc6abaf4140d259c0114697de16c2cc9c` (152,222 bytes; 112 source files). Default bootstrap, subsequent offline verification and all ten synthetic archive/cache tamper checks passed. Strict library compilation and actual Java 17 JAR/source/Javadoc/POM checks passed with the SDK 1.0.1 dependency. This validates source/artifacts; it does not claim Maven Central availability or a new native database test run.

SDK 1.0.0 had also been checked during preparation, but is superseded by this pin. Historical 0.x functional measurements above remain historical. Explicit development overrides enforce the same SDK version and are not used for public release acceptance. Update both the real public commit and downloaded digest whenever the pinned SDK changes; never reuse an old checksum.
