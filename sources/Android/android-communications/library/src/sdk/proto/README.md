# Protobuf Schema Ownership

This directory contains the checked-in SDK protobuf schema source of truth. Schema changes must be reviewed as protocol/API changes, then propagated by the canonical Android Gradle protobuf generation and `scripts/generate_swift_protobuf.sh` so generated Java/Kotlin and Swift outputs stay synchronized.

Do not place generated protobuf output in this directory. Generated Android output belongs under Gradle build directories, and generated Swift output belongs under `sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf`.
