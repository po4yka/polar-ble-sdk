# PMD Golden Vectors

This directory should hold PMD settings, control-point, and frame-level vectors before PMD logic is moved into shared KMP code. `PmdSettingsCommonPolicyTest.kt` now executes the intended shared PMD settings parsing, selected-setting serialization, duplicate overwrite, skipped FACTOR, and deterministic malformed-setting policy over the settings vectors. `PmdControlPointCommonPolicyTest.kt` executes shared active-measurement bit decoding, control-point response/status parsing, `more` flag handling, parameter extraction, and short-payload rejection. `PmdSecretCommonPolicyTest.kt` executes shared secret strategy byte mapping, key validation, SECURITY setting serialization, NONE/XOR decryption, AES block-alignment fixture checks, and unknown-strategy rejection while full common AES crypto selection and frame parser promotion remain separate gates.

`protocol/pmd/settings-readiness.json` is the `pmd-settings-readiness` behavior-family gate for PMD settings, and `pmdSettingsReadinessManifestNamesEveryPreMigrationBehaviorFamily` pins the manifest from shared common tests. It pins basic settings parsing, `duplicate-setting-overwrite`, FACTOR parsing, `selected-setting-serialization`, selected FACTOR skip policy, `range-milliunit-signedness-platform-decision`, `security-setting-platform-error-policy`, truncated-value and unknown-setting-type platform decisions, platform PMD settings vector references, and the `compile-verification-gate`.

`protocol/pmd/control-point-readiness.json` is the `pmd-control-point-readiness` behavior-family gate for PMD control-point responses and active measurements, and `pmdControlPointReadinessManifestNamesEveryPreMigrationBehaviorFamily` pins the manifest from shared common tests. It pins `active-measurement-bit-decoding`, platform active-measurement state names, success response parsing, `more` flag and parameter extraction, settings and measurement-status responses, `control-point-status-code-coverage`, unknown measurement type handling, `short-payload-deterministic-error-policy`, platform control-point vector references, and the `compile-verification-gate`.

`protocol/pmd/secret-readiness.json` is the `pmd-secret-readiness` behavior-family gate for PMD secret strategy handling, and `pmdSecretReadinessManifestNamesEveryPreMigrationBehaviorFamily` pins the manifest from shared common tests. It pins `security-strategy-byte-mapping`, `unknown-security-strategy-rejection`, SECURITY setting serialization, NONE/XOR/AES key validation, NONE/XOR decryption, `aes-fixture-pinning`, AES block-alignment gating, platform PMD secret vector references, and the `compile-verification-gate`.

Required groups:

- Settings serialization and parsing.
- Control-point success and error responses.
- Active measurement parsing.
- Offline trigger parsing.
- Secret/security metadata boundaries.
- Malformed payloads and unsupported measurement types.
