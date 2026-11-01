# 1.0.0 migration

All public monetary request and response fields now use canonical base-10 **strings in minor units**. For example, `amount: "1990"` represents TRY 19.90. This includes basket `price`, split and note amounts, capture/refund, balances and fixed fees on supported endpoints. Numeric JSON money is rejected; convert trusted native exact integers explicitly at your application boundary. Rates, basis points, quantities, counts and timestamps remain numeric.

Use the package’s decimal-text conversion helper with the actual currency. JPY has exponent 0, TRY/USD 2, KWD 3. Do not parse decimal text through float/double or assume a factor of 100. `"9007199254740993"` remains exact. Fiat amounts must fit signed 64-bit storage (maximum `"9223372036854775807"`); provider/platform limits may be lower. Crypto is a separate domain.

Canonical strings reject whitespace, signs on nonnegative values, leading zeros, decimals, exponents, `NaN`, `Infinity`, and negative zero. Payments are positive. Settlement totals may be zero; balances may be signed. An omitted/null capture or refund argument retains the remaining-amount semantics. Explicit `"0"` is invalid. Split-refund amount remains required where the existing API requires it.

Update call sites, persisted outbound request schemas and gateway response fixtures together. Preserve an existing attempt’s exact body, idempotency key, environment and credential profile; reconcile historical attempts before creating new ones. The SDK does not silently rewrite historical payment bytes or accept both numeric and string wire money. Native internal exact integer storage may remain unchanged.

Raw webhook verification, signatures, authentication and replay/idempotency protections are unchanged. Upgrade the gateway and integrations as one coordinated breaking cut. Public source export, signed bundle generation and Maven Central availability are verified separately.

The default source build uses Java SDK 1.0.1 from the reviewed public commit and codeload SHA-256 in `tools/sdk-source.json`: run `python3 tools/bootstrap-sdk.py`, then `gradle test`. Incomplete pins fail before download; Gradle verifies the complete cached source offline. `-PlunixiSdkSource=/absolute/path/to/canonical/java` remains an explicit development override with a version-match check. Release acceptance uses the public pin and does not substitute a historical 0.x artifact.
