# SDK Domain Golden Vectors

This directory holds higher-level SDK vectors for behavior that is deterministic but not a raw sensor frame. These fixtures are shared-ownership shared ownership contracts: Android, iOS, and future common tests should consume the same inputs before a domain model, parser helper, runtime workflow, or facade delegation moves into shared code.

Recommended groups:

- Activity and automatic samples.
- Sleep and nightly recharge.
- Offline recording metadata.
- Training-session metadata.
- Firmware status and failure mapping.
- Public facade command runtime planning.
- Shared fake-transport readiness and timeout planning.
- Disk and local-time facade query planning.
- User-device-settings facade read/write planning.
- Stream lifecycle runtime policy.
- Stored data cleanup workflow planning.
- Product capability JSON parsing.
- Watch-face and complication models.

Each child directory with JSON fixtures must include a local README that states the shared ownership boundary, unresolved platform policy, or common-test prerequisite for that slice.
