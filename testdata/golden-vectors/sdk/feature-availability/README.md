# Feature Availability Golden Vectors

`feature-availability-readiness.json` pins the shared KMP ownership boundary for SDK feature availability preconditions. The shared module owns deterministic feature-name normalization plus neutral service and capability requirements, while Android and iOS adapters keep service discovery, GATT client lookup, `clientReady` waits, PMD feature reads, notification readiness, BLE transport execution, public callbacks, and public error mapping platform-owned.
