# Time Date Golden Vectors

This KMP slice owns deterministic time/date helper policy that does not require a platform clock, timezone database, Swift `Date`, Java `Calendar`, Java `LocalDateTime`, or protobuf runtime in shared code. `TimeDateCommonPolicyTest.kt` executes field mapping, timezone-offset conversion, millisecond/nanosecond conversion, duration math, time-string formatting, and plain-date validation against these vectors before platform wrappers delegate pure utility behavior to shared KMP.

`sdk/time-date/time-date-readiness.json` is the behavior-family gate for this slice. It keeps platform calendar and timezone surfaces explicit while allowing portable field, offset, duration, formatting, and plain-date policy to move to common code.

Required cases:

- Local date/time field projection.
- Trusted system-time flag projection.
- Timezone offset conversion in minutes.
- Millisecond to nanosecond conversion.
- Nanosecond to millisecond rounding.
- Duration to milliseconds.
- Time string formatting.
- ISO plain-date validation including leap-day behavior.
