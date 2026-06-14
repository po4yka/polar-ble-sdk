# PSFTP RFC76 Golden Vectors

These vectors characterize RFC76 frame-header decoding under shared ownership. They cover single-frame, multi-frame, header-only, and error-frame behavior, and `PsFtpByteCodecCommonPolicyTest.kt` now consumes them as executable shared common policy coverage. Higher-level request/response sequencing, notification streams, and timeout behavior remain covered by platform client tests plus PSFTP fake-runtime vectors.

The aggregate `sdk/psftp-message-stream/byte-codec-readiness.json` manifest references every RFC76 frame vector here and keeps `rfc76-header` next/status/sequence/payload decoding, error-frame platform split behavior, platform codec vector references, and the `compile-verification-gate` explicit before production RFC76 decoding moves into shared.
