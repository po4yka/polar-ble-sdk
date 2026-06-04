# AtomicSet Vectors

These vectors characterize Android `AtomicSet` behavior before KMP extraction. The broader atomic and stream-continuation helpers remain platform/runtime primitives; only this collection-like contract is currently shared enough to capture as deterministic data.

`sdk/atomic-set/add-duplicate-null-and-snapshot.json`, `sdk/atomic-set/clear-remove-missing-and-fetch-none.json`, and `sdk/atomic-set/reverse-iteration-and-fetch.json` are Android-only compatibility fixtures consumed by `AtomicSetTest.kt`. They intentionally keep `platforms.ios=false` and `platforms.common=false` until a future migration slice designs a shared concurrency collection and chooses whether these newest-first iteration, null-add, duplicate-add, clear, snapshot, and fetch semantics must be preserved in common code.
