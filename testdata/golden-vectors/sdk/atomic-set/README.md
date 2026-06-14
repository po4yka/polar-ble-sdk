# AtomicSet Vectors

These vectors characterize Android `AtomicSet` behavior and the shared `PolarAtomicSet` compatibility fixture used by the Android wrapper. The broader atomic and stream-continuation helpers remain platform/runtime primitives; only this collection-like contract is common-owned.

`sdk/atomic-set/add-duplicate-null-and-snapshot.json`, `sdk/atomic-set/clear-remove-missing-and-fetch-none.json`, and `sdk/atomic-set/reverse-iteration-and-fetch.json` are compatibility fixtures consumed by Android `AtomicSetTest.kt` and shared `AtomicSetCommonPolicyTest.kt`. They intentionally keep `platforms.ios=false` because Swift atomic and stream-continuation helpers remain platform/runtime primitives.
