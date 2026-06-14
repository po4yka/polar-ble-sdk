# Device Capability Vectors

These vectors characterize product capability lookup behavior before shared ownership extraction. They cover the portable JSON/default behavior that Android, iOS, and future common code can execute today: file-system type mapping, unknown file-system strings, default fallback for missing device types, case-insensitive device lookup, recording-support defaults, firmware-update defaults, activity-data defaults, sensor-device defaults, and version-mismatch user-config merge behavior.

`capability-lookup-readiness.json` names the shared-contract behavior-family gate for capability parsing. `capability-resource-override-ownership.json` keeps Android asset/external-file selection and iOS Bundle.main/SDK-bundle/sandbox selection platform-owned until common resource loading is deliberately introduced.
