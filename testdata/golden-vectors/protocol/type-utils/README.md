# Type Utility Golden Vectors

These vectors characterize deterministic byte conversion behavior before moving parser primitives into KMP common code. `TypeUtilsCommonPolicyTest.kt` executes the intended shared byte-conversion policy, including platform-difference vectors where common code should choose deterministic empty-payload, unsigned high-bit, signed-minimum, and payload-too-long behavior before production parser primitives move. `type-utils-readiness.json` names the pre-migration behavior-family gate for unsigned byte/int/long conversion, signed sign extension, offset and size selection, signed-minimum boundaries, high-bit unsigned platform decisions, empty payload and payload-too-long error policy, UInt64 max decimal preservation, platform type-utils vector references, and the `compile-verification-gate`.

Required cases:

- Unsigned byte conversion.
- Little-endian unsigned integer conversion for one to four bytes.
- Little-endian unsigned long conversion for one to eight bytes.
- Little-endian signed integer conversion and sign extension.
- Offset and length conversion.
- Empty payload and oversized payload behavior.
- Current platform differences for unsigned conversion at native-width boundaries.
