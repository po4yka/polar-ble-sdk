# Daily Summary Golden Vectors

These vectors characterize `PbDailySummary` to public daily-summary model mapping under shared ownership. They cover date, calories, steps, distance, activity goal summary, activity class times, daily-balance feedback, readiness feedback, and DSUM request path construction. `ActivitySummaryCommonPolicyTest.kt` provides executable shared common policy coverage for these scalar, enum, duration, request-path mappings, and the combined `sdk/activity-samples/activity-summary-readiness.json` behavior-family gate before daily-summary model code moves to shared.
