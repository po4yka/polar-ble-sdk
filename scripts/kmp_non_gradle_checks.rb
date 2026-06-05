#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "set"

ROOT = File.expand_path("..", __dir__)
VECTOR_ROOT = File.join(ROOT, "testdata/golden-vectors")
SCHEMA_PATH = File.join(VECTOR_ROOT, "schema/golden-vector.schema.json")
ANDROID_TEST_ROOT = File.join(ROOT, "sources/Android/android-communications/library/src/test/java")
COMMON_TEST_ROOT = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin")
IOS_TEST_ROOT = File.join(ROOT, "sources/iOS/ios-communications/Tests")
REQUIRED_FIELDS = %w[id area case source input expected consumerTests platforms].freeze
PLATFORM_FIELDS = %w[android ios common].freeze
CONSUMER_PLATFORMS = %w[android ios commonPrototype].freeze
TOP_LEVEL_FIELDS = %w[
  id
  area
  case
  source
  description
  input
  expected
  consumerTests
  platformExpectations
  platforms
  execution
  commonDecision
  notes
].freeze
ROOT_GOLDEN_VECTOR_README_SCHEMA_TERMS = [
  "`description`",
  "`consumerTests`",
  "`execution`",
  "`commonDecision`",
  "`platformExpectations`",
  "`source` traceable",
  "Common-owned vectors",
  "shared commonTest",
  "Runtime planning vectors"
].freeze
SOURCE_PROVENANCE_TERMS = %w[characterization readiness planning backlog policy prototype migration Android iOS KMP shared].freeze
KMP_COVERAGE_DOCS = %w[
  documentation/KmpCoverageInventory.md
  documentation/KmpFakeTransportTestPlan.md
  documentation/KmpTddStrategy.md
].freeze
KMP_SHARED_COMMON_TEST_DOCS = %w[
  documentation/KmpCoverageInventory.md
  documentation/KmpFakeTransportTestPlan.md
  documentation/KmpFullCoverageTddBacklog.md
  documentation/KmpTddStrategy.md
  documentation/KmpValidationCommands.md
].freeze
SHARED_COMMON_TEST_DOC_EXCLUSIONS = %w[
  GoldenVectorJson.kt
  GoldenVectorTestData.kt
].freeze
TEST_REFERENCE = /`([^`]+Tests?\.(?:kt|swift))`/
TEST_FILE_NAMES = /.*Tests?\.(kt|swift)/
FIXTURE_README_KOTLIN_ARTIFACT_REFERENCE = /`([^`]+Test\.kt)`/
FIXTURE_README_BARE_SHARED_COMMON_TEST_REFERENCE = /`([A-Za-z0-9]+(?:CommonPolicyTest|CommonTest|UtilityCommonPolicyTest|RuntimePolicyCommonTest|CommonFakeWorkflowTest|ByteCodecCommonPolicyTest|MappingCommonPolicyTest|TransportPolicyCommonTest|CompressionPolicyCommonTest))`/
CHECKED_CHECKLIST_ITEM = /^- \[x\] (.+)$/
CHECKLIST_EVIDENCE_ROW = /^\| ([^|]+) \| ([^|]+) \|$/
COMPLETED_ITEM_EVIDENCE_SECTION = /## Completed Item Evidence.*?(?=\n## |\z)/m
BACKTICK_REFERENCE = /`([^`]+)`/
PARTIAL_ROW_GATE_TERMS = ["before", "keep ", "do not migrate", "platform-specific", "common fake", "fake-transport", "implement", "add "].freeze
CURRENT_EXECUTABLE_COMMON_COVERAGE_SECTION = /## Current Executable Common Coverage.*?(?=\n## |\z)/m
ROOT_README_FIXTURE_DIRECTORY_LISTING = /^testdata\/golden-vectors\/(.+\/)$/
PUBLIC_FACADE_OPERATION_FAMILIES = [
  "User device settings writes and reads",
  "REST service discovery and description",
  "Low-level file read write delete and list",
  "Disk and time facade reads/writes",
  "Stored data cleanup and deletion workflows",
  "Reset sync notification and H10 recording commands",
  "Firmware update workflow",
  "Offline trigger runtime"
].freeze
FIRMWARE_FACADE_GATE_REQUIRED_TERMS = [
  "facade gate open",
  "PolarFirmwareUpdateUtilsTest.kt",
  "PolarFirmwareUpdateUtilsTest.swift",
  "workflow-runtime-policy.json",
  "FirmwareWorkflowRuntimePolicyCommonTest.kt",
  "injectable production dependencies",
  "facade progress",
  "cleanup",
  "cancellation",
  "retry scheduling",
  "error-mapping tests before delegation"
].freeze
FACADE_GATE_OPEN_REQUIRED_TERMS = {
  "User device settings writes and reads" => [
    "facade gate open",
    "BDBleApiImplTest.kt",
    "PolarUserDeviceSettingsTest.kt",
    "UserDeviceSettingsCommonPolicyTest.kt",
    "PolarBleApiImplTests.swift",
    "PolarUserDeviceSettingsUtilsTest.swift",
    "settings-runtime-policy.json",
    "UserDeviceSettingsRuntimePolicyCommonTest.kt",
    "protobuf serialization",
    "platform defaults",
    "daylight-saving time source",
    "public error mapping before delegation"
  ],
  "Stored data cleanup and deletion workflows" => [
    "facade gate open",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "cleanup-workflow-policy.json",
    "StoredDataCleanupRuntimePolicyCommonTest.kt",
    "facade compatibility tests",
    "cleanup error/path splits before delegation"
  ],
  "Reset sync notification and H10 recording commands" => [
    "facade gate open",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "reset-sync-h10-command-policy.json",
    "CommandRuntimePolicyCommonTest.kt",
    "platform facade success/error compatibility tests",
    "sync failure splits before delegation"
  ],
  "Firmware update workflow" => FIRMWARE_FACADE_GATE_REQUIRED_TERMS
}.freeze
RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS = {
  "REST service discovery and description" => [
    "facade response-error pinned",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "rest-facade-runtime-policy.json",
    "rest-request-transport-policy.json",
    "RestFacadeRuntimePolicyCommonTest.kt",
    "RestRequestTransportPolicyCommonTest.kt",
    "response-error platform mapping",
    "empty-success policy",
    "facade-level public error compatibility assertions",
    "additional delegated REST operations before shared REST runtime delegation"
  ],
  "Low-level file read write delete and list" => [
    "facade empty-read and read/write/delete response-error pinned",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "file-facade-runtime-policy.json",
    "runtime-error-policy.json",
    "FileFacadeRuntimePolicyCommonTest.kt",
    "FileRuntimeErrorPolicyCommonTest.kt",
    "empty read payload success",
    "read/write/delete response errors",
    "write-stream failures",
    "facade-level public error compatibility assertions",
    "every additional delegated file operation before shared runtime delegation"
  ],
  "Disk and time facade reads/writes" => [
    "facade query-error pinned",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "disk-time-query-policy.json",
    "DiskTimeRuntimePolicyCommonTest.kt",
    "disk/local-time transport-error terminals",
    "filesystem capability gates",
    "additional public error mapping before delegation"
  ],
  "Offline trigger runtime" => [
    "Android/iOS facade and adapter gates partially covered",
    "BDBleApiImplTest.kt",
    "PolarBleApiImplTests.swift",
    "BlePmdClientTest.kt",
    "BlePmdClientTest.swift",
    "trigger-runtime-policy.json",
    "OfflineTriggerRuntimePolicyCommonTest.kt",
    "response-queue cleanup split explicit",
    "cancellation coverage only if production shared delegation introduces cancellable tasks, observers, or streams"
  ]
}.freeze
PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS = {
  "Timeout without notification" => [
    "notification-timeout-policy.json",
    "PsFtpRuntimePolicyCommonTest.kt",
    "no built-in timeout",
    "consumer-owned virtual-clock timeout cleanup",
    "fake-clock or injectable-timeout facade compatibility before production timeout delegation"
  ],
  "PSFTP notification continuation timeout" => [
    "notification-continuation-timeout-policy.json",
    "PsFtpRuntimePolicyCommonTest.kt",
    "typed continuation timeout",
    "without wall-clock waits",
    "fake-clock facade compatibility before production PSFTP timeout delegation"
  ],
  "PSFTP write acknowledgement timeout" => [
    "write-ack-timeout-policy.json",
    "PsFtpRuntimePolicyCommonTest.kt",
    "typed write-ack timeout",
    "without wall-clock waits",
    "fake-clock facade compatibility before production PSFTP write-timeout delegation"
  ]
}.freeze
FAKE_TRANSPORT_PRE_MIGRATION_GATE_REQUIRED_TERMS = [
  "A runtime migration slice must name the exact rows from the matrix that it implements.",
  "A slice that delegates only parser/model code does not need fake transport tests unless it changes stream or facade behavior.",
  "A slice that delegates public facade operations must include both platform facade tests and common fake-transport tests for success, error, cancellation, and timeout where applicable.",
  "A slice that keeps a transport area platform-specific must update `KmpCoverageInventory.md` with the ownership reason and the existing platform tests that remain authoritative.",
  "A runtime test must assert observer cleanup or cancellation propagation whenever it opens a stream, registers a listener, or starts an internal task."
].freeze
GNSS_LOCATION_OWNERSHIP_COMMON_POLICY_REQUIRED_TERMS = [
  "gnssLocationGoldenVectorsPinSharedParserPolicyWithAndroidProductionDelegation",
  "gnssLocationReadinessManifestNamesEverySharedParserDelegationFamily",
  "GNSS_LOCATION_SHARED_PARSER_VECTORS",
  "protocol/sensors/gnss-location-readiness.json",
  "gnss-location-readiness",
  "sharedParserAndroidProductionDelegation",
  "protocol/sensors/gnss-location-raw-type0-coordinate.json",
  "protocol/sensors/gnss-location-raw-type1-satellite-dilution.json",
  "protocol/sensors/gnss-location-raw-type2-satellite-summary.json",
  "protocol/sensors/gnss-location-raw-type3-nmea.json",
  "shared-parser-raw-type0-coordinate",
  "shared-parser-raw-type1-satellite-dilution",
  "shared-parser-raw-type2-satellite-summary",
  "shared-parser-raw-type3-nmea",
  "android-production-delegation",
  "non-ios-parser-ownership",
  "shared-parser-parity-gate",
  "compile-verification-gate",
  "coordinate",
  "satelliteDilution",
  "satelliteSummary",
  "nmea",
  "consumerTests.hasStringArray(\"ios\")",
  "platforms.booleanValue(\"ios\")",
  "platforms.booleanValue(\"common\")",
  "SHARED_GNSS_LOCATION_MIGRATION_OWNERSHIP"
].freeze
FAKE_TRANSPORT_HARNESS_DESCRIPTION_REQUIRED_TERMS = [
  "deterministic command capture",
  "payload capture",
  "scripted byte responses",
  "response errors",
  "transport errors",
  "completion",
  "timeout behavior",
  "connection-state guards",
  "disconnect-after-operation limits",
  "active observer counts",
  "idempotent stream cancellation",
  "cleanup callback counts",
  "upstream cancellation observation",
  "virtual clock for timeout checks without wall-clock sleeps"
].freeze
SHARED_COMMON_PRODUCTION_CODEC_DEPENDENCY_TERMS = [
  "protobuf",
  "flatbuffers",
  "flatbuffer",
  "crypto",
  "cryptography",
  "gzip",
  "zlib",
  "compression",
  "okio",
  "kotlinx-io"
].freeze
BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS = {
  "KmpFullCoverageTddBacklog.md" => [
    "add real common protobuf/gzip production dependencies",
    "full AES implementation ownership still must be chosen",
    "add byte-level shared codec vectors before moving gzip/deflate behavior into common code",
    "byte-identical output",
    "KVTX"
  ],
  "KmpCoverageInventory.md" => [
    "Keep generic iOS `Data.deflated`/`Data.inflated` platform-specific unless a future shared REST codec deliberately standardizes gzip/zlib behavior for KMP common code.",
    "Keep iOS nil-on-truncation compatibility adapter-owned if required while common parsing uses typed malformed-script errors.",
    "semantic and codec-ownership/readiness policy executable"
  ],
  "KmpPreMigrationRemainingWork.md" => [
    "Add real common protobuf/gzip/crypto/codec dependencies",
    "training-session payload parsing",
    "PMD AES secret handling",
    "compression helpers",
    "shared FlatBuffer/KVTX byte-identical output decision"
  ],
  "payload-read-policy.json" => [
    "byteLevelParserGate",
    "add-common-protobuf-and-gzip-parser-dependencies-before-byte-level-payload-migration",
    "deferred-until-common-protobuf-and-gzip-parser-exist"
  ],
  "payload-parser-policy.json" => [
    "Before moving byte-level training payload parsing to common code, add production common protobuf and gzip dependencies",
    "without claiming common byte decoding is implemented",
    "training-session-summary-protobuf",
    "exercise-summary-protobuf",
    "route-protobuf",
    "route-gzip-protobuf",
    "route-advanced-protobuf",
    "route-advanced-gzip-protobuf",
    "samples-protobuf",
    "samples-gzip-protobuf",
    "samples-advanced-gzip-protobuf",
    "gzip-protobuf"
  ],
  "training-session-readiness.json" => [
    "byte-level-parser-dependency-gate",
    "real byte-level protobuf/gzip decoding remains deferred until common production parser dependencies exist and are compile-verified"
  ],
  "secret-readiness.json" => [
    "AES block-alignment gating",
    "production common AES provider selection remains an explicit implementation gate rather than a test-only shortcut"
  ],
  "rest-event-compression-readiness.json" => [
    "android-gzip-codec-reference-gate",
    "ios-deflate-codec-reference-gate",
    "normalize-or-preserve-codec-decision-gate"
  ],
  "watch-face-readiness.json" => [
    "byte-identical FlatBuffer output remains platform-specific unless production shared FlatBuffer builders are deliberately introduced and compile-verified"
  ]
}.freeze
FAKE_TRANSPORT_REQUIRED_OPERATIONS = %w[read write subscribe unsubscribe].freeze
FAKE_TRANSPORT_REQUIRED_OUTCOMES = %w[Bytes TransportError ResponseError Timeout Complete].freeze
FAKE_TRANSPORT_TEST_REQUIRED_TERMS = [
  "FakeTransportCommand(FakeTransportOperation.READ",
  "FakeTransportCommand(FakeTransportOperation.WRITE",
  "FakeTransportCommand(FakeTransportOperation.SUBSCRIBE",
  "FakeTransportCommand(FakeTransportOperation.UNSUBSCRIBE",
  "FakeTransportOutcome.Bytes",
  "FakeTransportOutcome.ResponseError",
  "FakeTransportOutcome.TransportError",
  "FakeTransportOutcome.Complete",
  "FakeTransportOutcome.Timeout",
  "\"0a0b\""
].freeze
FAKE_TRANSPORT_CLEANUP_REQUIRED_TERMS = [
  "FakeTransportSubscription",
  "activeObserverCount",
  "cancelledStreams",
  "openStream",
  "cancelStream",
  "upstreamCancelled"
].freeze
FAKE_TRANSPORT_CLEANUP_TEST_REQUIRED_TERMS = [
  "stream cancellation removes observer cancels upstream and is idempotent",
  "failed stream subscription does not register observer",
  "transport.activeObserverCount",
  "transport.cancelledStreams",
  "subscription.upstreamCancelled"
].freeze
FAKE_TRANSPORT_COMMON_REQUIRED_TERMS = [
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "CommonFakeTransportOperation",
  "CommonFakeTransportOutcome",
  "CommonFakeTransportSubscription",
  "REMOVE",
  "startConnected",
  "disconnectAfterOperations",
  "isConnected",
  "activeObserverCount",
  "cancelledStreams",
  "cleanupCallbackCount",
  "upstreamCancelled",
  "CommonFakeServiceReadinessGate",
  "awaitReady",
  "CommonFakeVirtualClock",
  "advanceBy",
  "hasTimedOut"
].freeze
FAKE_TRANSPORT_COMMON_TEST_REQUIRED_TERMS = [
  "capturesCommandOrderPayloadsAndScriptedOutcomes",
  "returnsTimeoutForUnscriptedOperations",
  "connectionStateGuardsDisconnectedStartAndDisconnectAfterOperationLimit",
  "disconnected-after-1-operations",
  "streamCancellationRemovesObserverCancelsUpstreamAndIsIdempotent",
  "failedStreamSubscriptionDoesNotRegisterObserver",
  "serviceReadinessGateRecordsAttemptsAndTimesOutDeterministically",
  "virtualClockAdvancesTimeoutsWithoutWallClockSleep",
  "service-readiness",
  "CommonFakeVirtualClock",
  "transport.cleanupCallbackCount",
  "\"0a0b\""
].freeze
FAKE_TRANSPORT_COMMON_COMMAND_RUNTIME_TEST_REQUIRED_TERMS = [
  "resetSyncH10CommandPolicyVectorDefinesExecutableCommonCommandPlanning",
  "resetSyncH10CommandVectorRunsThroughCommonFakeTransportFacadeShape",
  "resetSyncH10CommandReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/command-runtime/reset-sync-h10-command-policy.json",
  "sdk/command-runtime/reset-sync-h10-command-readiness.json",
  "reset-sync-h10-command-policy",
  "reset-sync-h10-command-readiness",
  "h10-start-recording",
  "h10-start-recording-query-failure",
  "REQUEST_START_RECORDING",
  "sampleDataIdentifier=myExercise",
  "start-recording-query-failed",
  "h10-stop-recording",
  "h10-stop-recording-query-failure",
  "REQUEST_STOP_RECORDING",
  "stop-recording-query-failed",
  "h10-recording-status",
  "h10-recording-status-query-failure",
  "REQUEST_RECORDING_STATUS",
  "recording-status-query-failed",
  "queryFailure",
  "transport-error",
  "factory-reset-notification-failure",
  "factory-reset-preserve-pairing",
  "factory-reset-preserve-pairing-notification-failure",
  "warehouse-sleep",
  "warehouse-sleep-notification-failure",
  "turn-device-off",
  "turn-device-off-notification-failure",
  "restart-notification-failure",
  "sync-start-success",
  "sync-start-query-failure",
  "sync-stop-success",
  "sync-stop-notification-failure",
  "platform-split",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "syncStartQueryFailure",
  "syncStopNotificationFailure",
  "h10-recording-start-query",
  "h10-recording-start-query-failure",
  "h10-recording-stop-query",
  "h10-recording-stop-query-failure",
  "h10-recording-status-query",
  "h10-recording-status-query-failure",
  "factory-reset-flags",
  "preserve-pairing-reset-flags",
  "preserve-pairing-reset-notification-failure",
  "sync-start-notification-sequence",
  "restart-reset-notification-failure",
  "warehouse-sleep-reset-notification-failure",
  "turn-device-off-reset-notification-failure",
  "sync-start-query-failure-platform-split",
  "sync-stop-notification-failure-platform-split",
  "facade-error-mapping-gate",
  "compile-verification-gate",
  "PolarRuntimeOrchestration"
].freeze
FAKE_TRANSPORT_COMMON_STORED_DATA_CLEANUP_RUNTIME_TEST_REQUIRED_TERMS = [
  "cleanupWorkflowPolicyVectorDefinesExecutableCommonTraversalAndPlatformSplits",
  "cleanupWorkflowVectorRunsThroughCommonFakeTransportFacadeShape",
  "cleanupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/stored-data-cleanup/cleanup-workflow-policy.json",
  "sdk/stored-data-cleanup/cleanup-workflow-readiness.json",
  "stored-data-cleanup-workflow-policy",
  "telemetry-root-trc-bin-filter",
  "sdlogs-extension-filter",
  "activity-prune-empty-parents",
  "automatic-sample-embedded-day-filter",
  "sdlogs-list-failure-platform-policy",
  "telemetry-list-failure-platform-policy",
  "TRC10.BIN",
  "A.SLG",
  "ACTIVITY.BPB",
  "AUTOS001.BPB",
  "platform-path-split",
  "platform-split",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "sdlogsListFailure",
  "activityEmptyParentRemovePath",
  "telemetry-trc-filter",
  "list-failure-platform-split",
  "empty-parent-path-platform-split",
  "facade-error-mapping-gate",
  "compile-verified",
  "PolarWorkflowRuntimePlanning.planStoredDataCleanup"
].freeze
FAKE_TRANSPORT_COMMON_DISK_TIME_RUNTIME_TEST_REQUIRED_TERMS = [
  "diskTimeQueryPolicyVectorDefinesExecutableCommonQueryPlanning",
  "diskTimeQueryVectorRunsThroughCommonFakeTransportFacadeShape",
  "diskTimeQueryReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/disk-time-runtime/disk-time-query-policy.json",
  "sdk/disk-time-runtime/disk-time-query-readiness.json",
  "disk-time-query-policy",
  "get-disk-space",
  "GET_DISK_SPACE",
  "get-local-time",
  "GET_LOCAL_TIME",
  "set-local-time-v2",
  "SET_SYSTEM_TIME",
  "SET_LOCAL_TIME",
  "systemTimeHour=10",
  "localTimeHour=12",
  "systemTimeTrusted=true",
  "set-local-time-h10",
  "set-local-time-failure",
  "get-local-time-failure",
  "get-local-time-with-zone-failure",
  "get-disk-space-failure",
  "transport-error",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "disk-space-query",
  "local-time-query",
  "local-time-with-zone-query",
  "v2-system-and-local-time-sequence",
  "h10-single-local-time-query",
  "local-time-transport-error",
  "local-time-with-zone-transport-error",
  "filesystem-capability-gate",
  "facade-error-mapping-gate",
  "compile-verified",
  "PolarRuntimeOrchestration"
].freeze
FAKE_TRANSPORT_COMMON_USER_DEVICE_SETTINGS_RUNTIME_TEST_REQUIRED_TERMS = [
  "userDeviceSettingsRuntimePolicyVectorDefinesExecutableCommonReadWritePlanning",
  "userDeviceSettingsRuntimeVectorRunsThroughCommonFakeTransportFacadeShape",
  "userDeviceSettingsRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/user-device-settings-runtime/settings-runtime-policy.json",
  "sdk/user-device-settings-runtime/settings-runtime-readiness.json",
  "user-device-settings-runtime-policy",
  "user-device-settings-runtime-readiness",
  "/U/0/S/UDEVSET.BPB",
  "get-user-device-settings",
  "get-user-device-settings-read-failure",
  "set-telemetry-enabled",
  "set-telemetry-read-failure",
  "set-telemetry-write-failure",
  "set-user-device-location",
  "set-user-device-location-write-failure",
  "set-usb-connection-mode",
  "set-usb-connection-mode-write-failure",
  "set-automatic-training-detection",
  "set-automatic-training-detection-write-failure",
  "set-automatic-ohr-measurement",
  "set-automatic-ohr-measurement-write-failure",
  "set-daylight-saving-time",
  "telemetryEnabled=true",
  "deviceLocation=WRIST_RIGHT",
  "usbConnectionMode=ON",
  "automaticTrainingDetectionMode=ON",
  "automaticTrainingDetectionSensitivity=77",
  "minimumTrainingDurationSeconds=300",
  "automaticOhrMeasurement=ALWAYS_ON",
  "daylightSaving.nextDaylightSavingTime=present",
  "transport-error-after-payload",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "read-failure no-write behavior",
  "settings-read-failure-no-write",
  "telemetry-write-failure-after-payload",
  "usb-connection-mode-write-failure-after-payload",
  "automatic-training-detection-read-then-write",
  "automatic-ohr-measurement-write-failure-after-payload",
  "daylight-saving-payload-shape",
  "protobuf-field-preservation-gate",
  "facade-error-mapping-gate",
  "compile-verification-gate",
  "PolarRuntimeOrchestration",
  "planUserDeviceSettings"
].freeze
FAKE_TRANSPORT_COMMON_REST_RUNTIME_TEST_REQUIRED_TERMS = [
  "restRequestTransportPolicyVectorRunsThroughProductionCommonPlanner",
  "restRequestTransportReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "rest-request-transport-policy",
  "sdk/rest-service/rest-request-transport-policy.json",
  "sdk/rest-service/rest-request-transport-readiness.json",
  "rest-request-transport-readiness",
  "PolarRuntimeOrchestration.planRestRequestTransport",
  "service-list-request-error-payload",
  "service-description-request-error-payload",
  "service-list-empty-transport-response",
  "service-description-empty-transport-response",
  "requires-empty-response-policy",
  "empty-successful-response-policy-gate",
  "response-error-payload-status",
  "response-error-payload-message",
  "facade-error-mapping-deferred",
  "compile-verification-gate",
  "PolarRestRequestTransportOperation"
].freeze
FAKE_TRANSPORT_COMMON_FILE_RUNTIME_TEST_REQUIRED_TERMS = [
  "fileListingGoldenVectorsDefineExecutableCommonTraversalPolicy",
  "sdk/file-utils/list-files-shallow-all.json",
  "sdk/file-utils/list-files-recursive-filtered.json",
  "entry-name-contains-dot",
  "fileReadWriteDeleteGoldenVectorRunsThroughProductionFileFacadePlanner",
  "sdk/file-utils/file-read-write-delete-operations.json",
  "fileRuntimeErrorPolicyVectorRunsThroughProductionCommonPlanner",
  "fileRuntimeErrorReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/file-utils/runtime-error-policy.json",
  "sdk/file-utils/runtime-error-readiness.json",
  "runtime-error-readiness",
  "PolarRuntimeOrchestration.planFileFacade",
  "directory-list-response-error-103",
  "directory-list-malformed-payload",
  "read-file-transport-error",
  "write-file-stream-error-after-header",
  "delete-file-response-error",
  "directory-missing",
  "directory-parse-failure",
  "write-stream-error",
  "capturedPayloadHex",
  "directory-missing-status-103",
  "directory-malformed-payload-parse-failure",
  "write-file-payload-capture-before-stream-error",
  "facade-error-mapping-deferred",
  "compile-verification-gate",
  "PolarRuntimeOrchestration.planFileRuntimeError"
].freeze
FAKE_TRANSPORT_COMMON_FILE_FACADE_RUNTIME_TEST_REQUIRED_TERMS = [
  "fileFacadeRuntimePolicyVectorDefinesExecutableCommonCommandPlanning",
  "fileFacadeRuntimeVectorRunsThroughCommonFakeTransportFacadeShape",
  "fileFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/file-utils/file-facade-runtime-policy.json",
  "sdk/file-utils/file-facade-runtime-readiness.json",
  "file-facade-runtime-policy",
  "file-facade-runtime-readiness",
  "read-low-level-file-success",
  "read-low-level-file-empty-success",
  "read-low-level-file-request-failure",
  "read-low-level-file-response-error",
  "write-low-level-file-success",
  "write-low-level-file-progress-success",
  "write-low-level-file-stream-failure",
  "write-low-level-file-response-error",
  "delete-low-level-file-success",
  "delete-low-level-file-request-failure",
  "delete-low-level-file-response-error",
  "/U/0/CUSTOM.BIN",
  "/U/0/EMPTY.BIN",
  "/U/0/PROGRESS.BIN",
  "GET",
  "PUT",
  "REMOVE",
  "010203",
  "0a0b",
  "1011",
  "progress:0",
  "progress:2",
  "0c0d",
  "0e0f",
  "transport-error",
  "response-error:103:missing",
  "pftp-response-error-name",
  "pftp-response-error-object",
  "pftp-response-error-code",
  "device-error-wrapper",
  "write-stream-error-after-payload",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "file-read-write-delete-operations.json",
  "runtime-error-policy.json",
  "list-files-shallow-all.json",
  "list-files-recursive-filtered.json",
  "low-level-file-path-gate",
  "read-file-empty-success",
  "read-file-request-failure",
  "read-file-response-error",
  "write-file-progress-before-completion",
  "write-file-stream-failure-after-payload",
  "write-file-response-error-after-payload",
  "delete-file-remove-success",
  "delete-file-request-failure",
  "directory-list-recursive-vector-reference-gate",
  "runtime-error-policy-reference-gate",
  "response-error-policy-gate",
  "facade-error-mapping-gate",
  "compile-verification-gate",
  "PolarRuntimeOrchestration",
  "planFileFacade"
].freeze
FAKE_TRANSPORT_COMMON_REST_FACADE_RUNTIME_TEST_REQUIRED_TERMS = [
  "restFacadeRuntimePolicyVectorDefinesExecutableCommonRequestPlanning",
  "restFacadeRuntimeVectorRunsThroughCommonFakeTransportFacadeShape",
  "restFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/rest-service/rest-facade-runtime-policy.json",
  "sdk/rest-service/rest-facade-runtime-readiness.json",
  "rest-facade-runtime-policy",
  "rest-facade-runtime-readiness",
  "list-rest-api-services-success",
  "get-rest-api-description-success",
  "list-rest-api-services-request-failure",
  "get-rest-api-description-request-failure",
  "list-rest-api-services-response-error",
  "get-rest-api-description-response-error",
  "list-rest-api-services-empty-success",
  "get-rest-api-description-empty-success",
  "list-rest-api-services-malformed-success",
  "get-rest-api-description-malformed-success",
  "/REST/SERVICE.API",
  "/REST/SLEEP.API",
  "service-list-json",
  "service-description-json",
  "serviceName=sleep",
  "serviceName=training",
  "servicePath.sleep=/REST/SLEEP.API",
  "event=sleep",
  "endpoint=stop",
  "action.post=/REST/SLEEP.API?cmd=post",
  "detail.sleep=state",
  "trigger.sleep=change",
  "transport-error",
  "responseError",
  "response-error",
  "response-error:103:NO_SUCH_FILE_OR_DIRECTORY",
  "pftp-response-error-name",
  "pftp-response-error-code",
  "NO_SUCH_FILE_OR_DIRECTORY",
  "successEmpty",
  "successMalformedJson",
  "empty-response-parse-failure",
  "malformed-response-parse-failure",
  "malformed-json",
  "json-parse-failure",
  "json-decoder-failure",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "rest-request-transport-policy.json",
  "service-list-request-path",
  "service-description-action-field-mapping",
  "service-list-request-failure",
  "service-description-request-failure",
  "service-list-response-error-platform-mapping",
  "service-description-response-error-platform-mapping",
  "service-list-empty-success-parse-failure",
  "service-description-empty-success-parse-failure",
  "service-list-malformed-success-parse-failure",
  "service-description-malformed-success-parse-failure",
  "model-json-mapping-vector-reference-gate",
  "empty-response-transport-policy-gate",
  "response-error-transport-policy-gate",
  "facade-error-mapping-gate",
  "compile-verification-gate",
  "PolarRuntimeOrchestration",
  "planRestFacade"
].freeze
FAKE_TRANSPORT_COMMON_REST_SERVICE_MAPPING_TEST_REQUIRED_TERMS = [
  "restServiceListGoldenVectorsDefineExecutableCommonMappingPolicy",
  "restServiceDescriptionGoldenVectorsDefineExecutableCommonMappingPolicy",
  "restServiceListWrongTypeGoldenVectorPinsPlatformSplitBeforeCommonDecoderMigration",
  "restServiceMappingReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/rest-service/rest-service-mapping-readiness.json",
  "rest-service-mapping-readiness",
  "compileVerifiedPreMigrationCharacterization",
  "sdk/rest-service/service-list-basic.json",
  "sdk/rest-service/service-list-empty.json",
  "sdk/rest-service/service-description-training.json",
  "sdk/rest-service/service-description-empty.json",
  "sdk/rest-service/service-list-wrong-type-platform-policy.json",
  "service-list-name-path-mapping",
  "service-list-empty-defaults",
  "service-description-action-event-mapping",
  "service-description-empty-defaults",
  "wrong-type-services-platform-split",
  "unknown-field-ignore-policy",
  "platform-rest-service-vector-references",
  "compile-verification-gate",
  "ignore-unknown-fields",
  "return-empty-collections",
  "choose an explicit shared policy"
].freeze
FAKE_TRANSPORT_COMMON_REST_EVENT_COMPRESSION_TEST_REQUIRED_TERMS = [
  "restEventCompressionGoldenVectorDefinesExecutableCommonCodecOwnershipPolicy",
  "restEventCompressionReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/rest-service/rest-event-compression-platform-policy.json",
  "sdk/rest-service/rest-event-compression-readiness.json",
  "rest-event-compression-readiness",
  "uncompressed-batch",
  "empty-uncompressed-batch",
  "compressed-batch",
  "malformed-compressed-payload",
  "android-gzip-codec-reference-gate",
  "ios-deflate-codec-reference-gate",
  "malformed-compressed-payload-platform-split",
  "normalize-or-preserve-codec-decision-gate",
  "compile-verification-gate",
  "GZIPInputStream",
  "normalize or explicitly preserve this platform split"
].freeze
FAKE_TRANSPORT_COMMON_BACKUP_UTILITY_TEST_REQUIRED_TERMS = [
  "backupExpansionAndRestoreWritesGoldenVectorDefinesExecutableCommonPolicy",
  "restoreFailureGoldenVectorPinsPlatformSplitBeforeCommonWorkflowMigration",
  "backupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/backup-utils/backup-expansion-and-restore-writes.json",
  "sdk/backup-utils/restore-failure-platform-policy.json",
  "sdk/backup-utils/backup-workflow-readiness.json",
  "backup-workflow-readiness",
  "BACKUP.TXT",
  "backup-txt-expansion",
  "default-user-file-inclusion",
  "restore-put-command-planning",
  "restore-failure-platform-split",
  "restore-failure-aggregation-decision-gate",
  "compile-verification-gate",
  "choose whether restore failure aggregation belongs in shared code",
  "PolarWorkflowRuntimePlanning.planBackupRestore"
].freeze
FAKE_TRANSPORT_COMMON_OFFLINE_TRIGGER_RUNTIME_TEST_REQUIRED_TERMS = [
  "offlineTriggerRuntimePolicyVectorRunsThroughProductionCommonPlanner",
  "offlineTriggerRuntimeVectorRunsThroughCommonFakeTransportFacadeShape",
  "offlineTriggerRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/offline-recording/trigger-runtime-policy.json",
  "sdk/offline-recording/trigger-runtime-readiness.json",
  "PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime",
  "set-trigger-success-with-secret",
  "set-trigger-mode-error",
  "set-trigger-status-read-error",
  "set-trigger-setting-error",
  "get-trigger-success",
  "get-trigger-transport-error",
  "setMode:TRIGGER_SYSTEM_START",
  "setSetting",
  "control-point-error",
  "transport-error",
  "ScriptedCommonFakeTransport",
  "CommonFakeTransportCommand",
  "enabledFeatures",
  "typed-set-mode",
  "settings-write",
  "optional-secret-attachment",
  "facade-error-mapping-deferred",
  "compile-verified",
  "explicit length byte"
].freeze
FAKE_TRANSPORT_COMMON_FIRMWARE_UTILITY_TEST_REQUIRED_TERMS = [
  "firmwareDeviceInfoGoldenVectorsDefineExecutableCommonMappingPolicy",
  "firmwareVersionComparisonGoldenVectorDefinesExecutableCommonDottedIntegerPolicy",
  "firmwareInvalidVersionGoldenVectorPinsTypedParseFailureBeforePublicWorkflowMigration",
  "firmwareFileOrderingGoldenVectorDefinesExecutableCommonSystemUpdateLastPolicy",
  "firmwareUtilityReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/firmware-update/utility-readiness.json",
  "firmware-utility-readiness",
  "compileVerifiedPreMigrationCharacterization",
  "sdk/firmware-update/device-info-basic.json",
  "sdk/firmware-update/device-info-zero-version.json",
  "sdk/firmware-update/version-comparison.json",
  "sdk/firmware-update/version-comparison-invalid.json",
  "sdk/firmware-update/file-ordering.json",
  "device-info-protobuf-mapping",
  "zero-version-preservation",
  "dotted-integer-version-comparison",
  "invalid-version-typed-parse-failure",
  "system-update-file-ordering-last",
  "platform-firmware-utility-vector-references",
  "compile-verification-gate",
  "preserve-empty-device-info-strings",
  "typed parse failure",
  "SYSUPDAT.IMG"
].freeze
FAKE_TRANSPORT_COMMON_FIRMWARE_WORKFLOW_TEST_REQUIRED_TERMS = [
  "firmwareWorkflowRuntimePolicyVectorRunsThroughProductionCommonPlanner",
  "firmwareWorkflowRuntimeVectorRunsThroughCommonFakeDependencies",
  "firmwareWorkflowRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/firmware-update/workflow-runtime-policy.json",
  "sdk/firmware-update/workflow-runtime-readiness.json",
  "PolarWorkflowRuntimePlanning.planFirmwareWorkflow",
  "check-update-not-available",
  "check-update-available",
  "download-failure",
  "downloadAttempted",
  "empty-or-invalid-zip",
  "zipExtractionAttempted",
  "retryable-server-failure",
  "write-package-success-with-system-update-last",
  "system-update-reboot-response-is-success",
  "SYSUPDAT.IMG",
  "rebooting",
  "battery-too-low-response-is-terminal-failure",
  "battery-too-low",
  "retryable server failure",
  "CommonFirmwareFakeNetwork",
  "CommonFirmwareFakePackageDownloader",
  "CommonFirmwareFakeZipStore",
  "CommonFirmwareFakeBleWriter",
  "CommonFakeRetryScheduler",
  "firmwarePayloadsByPath",
  "firmwareArtifacts",
  "bleWritePayloads",
  "payloadsByPath",
  "bleWriteProgressEvents",
  "progressEvents",
  "payloadHex.length / 2",
  "fake-network-availability",
  "fake-filesystem-zip-extraction",
  "ble-write-progress",
  "cancellation-gate",
  "cancellation-cleanup-after-package-fetch",
  "retryable-server-failure-gate",
  "facade-error-mapping-gate",
  "compile-verified",
  "cancel-after-package-fetch-cleans-up-before-ble-write",
  "cleanupCallbackCount",
  "fwUpdateCancelled",
  "cancelled"
].freeze
FAKE_TRANSPORT_COMMON_PSFTP_RUNTIME_TEST_REQUIRED_TERMS = [
  "commonFakeResponseRuntimeReassemblesRequestResponses",
  "sdk/psftp-response/request-response-reassembly.json",
  "single-frame",
  "multi-frame",
  "sdk/psftp-response/request-response-error-policy.json",
  "known-error-no-such-file",
  "unknown-error-code",
  "sdk/psftp-notifications/notification-reassembly.json",
  "sdk/psftp-notifications/notification-ordering.json",
  "two-single-frame-notifications",
  "commonFakeNotificationRuntimePreservesInitialSilenceAsNoEmissionWithoutBuiltInTimeout",
  "sdk/psftp-notifications/notification-timeout-policy.json",
  "initial-silence",
  "wait-notification-has-no-built-in-initial-silence-timeout",
  "commonFakeNotificationRuntimeConsumerTimeoutCleansObserverWithVirtualClock",
  "PolarWorkflowRuntimePlanning.planConsumerTimeoutObserverCleanup",
  "consumerTimeout",
  "activeObserverCount",
  "cleanupCallbackCount",
  "commonFakePsFtpHarnessPinsInitialSilenceTimeoutCleanupWithoutLeakedOperation",
  "CommonFakePsFtpRuntimeHarness",
  "descriptorEnabled",
  "independentD2hChannelPackets",
  "independentMtuChannelWrites",
  "pendingOperationCount",
  "operationScopeCleanupEvents",
  "scanner-pause",
  "scanner-resume",
  "commonFakeNotificationRuntimePinsRfc76ErrorAndTransportStatusPlatformSplit",
  "sdk/psftp-notifications/notification-error-policy.json",
  "rfc76-error-first-frame",
  "transport-error-first-packet",
  "characterize-current-platform-notification-error-semantics",
  "Nonzero transport status is a current platform split",
  "sdk/psftp-notifications/notification-continuation-timeout-policy.json",
  "missing-last-frame-after-more",
  "commonFakePsFtpHarnessPinsContinuationTimeoutCleanupWithoutLeakedOperation",
  "commonFakeWriteRuntimePinsPlatformProgressSplitBeforeSharedPolicyChoice",
  "sdk/psftp-response/write-success-progress.json",
  "android-currently-emits-negative-header-overhead-progress-before-payload-count-while-ios-emits-initial-zero-header-progress-and-final-payload-count",
  "sdk/psftp-response/write-interruption-error-policy.json",
  "sdk/psftp-response/write-transport-failure-policy.json",
  "sdk/psftp-response/write-ack-timeout-policy.json",
  "commonFakePsFtpHarnessPinsWriteAckTimeoutCleanupWithoutLeakedOperation",
  "writeAckCount",
  "psFtpRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/psftp-response/psftp-runtime-readiness.json",
  "psftp-runtime-readiness",
  "request-response-reassembly",
  "request-response-error-mapping",
  "notification-reassembly",
  "notification-ordering",
  "consumer-timeout-observer-cleanup",
  "notification-transport-status-platform-split",
  "write-progress-platform-split",
  "write-ack-timeout",
  "platform-client-vector-reference-gate",
  "compile-verification-gate",
  "ContinuationTimeout",
  "WriteAckTimeout",
  "TransportWriteFailure"
].freeze
FAKE_TRANSPORT_COMMON_PSFTP_BYTE_CODEC_TEST_REQUIRED_TERMS = [
  "psFtpRfc76FrameGoldenVectorsDecodeHeaderPayloadAndErrorPolicy",
  "sdk/psftp-rfc76/error-frame-ffff.json",
  "sdk/psftp-rfc76/final-last-frame.json",
  "sdk/psftp-rfc76/first-more-frame.json",
  "sdk/psftp-rfc76/header-only-last-frame.json",
  "sdk/psftp-rfc76/header-only-more-frame.json",
  "sdk/psftp-rfc76/middle-more-frame.json",
  "sdk/psftp-rfc76/single-last-frame.json",
  "android-currently-masks-shifted-high-byte-while-ios-uses-little-endian-uint16",
  "psFtpCompleteMessageStreamGoldenVectorDefinesExecutableRfc60EncodingPolicy",
  "sdk/psftp-message-stream/complete-message-streams.json",
  "request-header-only",
  "android-request-with-file-data",
  "query-with-header",
  "notification-with-header",
  "notification-empty-header",
  "encode-rfc60-complete-message-streams",
  "Android makeCompleteMessageStream appends file data",
  "psFtpRfc76FrameSplittingGoldenVectorDefinesExecutableMtuAndSequencePolicy",
  "sdk/psftp-message-stream/rfc76-frame-splitting.json",
  "empty-payload",
  "exactly-one-frame",
  "two-frames",
  "sequence-wraps-after-fifteen",
  "split-rfc76-message-frames",
  "psFtpByteCodecReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/psftp-message-stream/byte-codec-readiness.json",
  "psftp-byte-codec-readiness",
  "rfc76-header-next-bit",
  "rfc76-status-decoding",
  "rfc76-error-frame-platform-split",
  "rfc60-request-stream-encoding",
  "android-request-file-data-append-policy",
  "rfc76-mtu-frame-splitting",
  "rfc76-sequence-wrap",
  "platform-codec-vector-reference-gate",
  "compile-verification-gate"
].freeze
FAKE_TRANSPORT_COMMON_STREAM_RUNTIME_TEST_REQUIRED_TERMS = [
  "orderedEmissionsPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/ordered-emissions-policy.json",
  "terminalErrorPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/terminal-error-policy.json",
  "initialDisconnectedPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/initial-disconnected-policy.json",
  "uncheckedSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/unchecked-subscription-policy.json",
  "consumerCancellationPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/consumer-cancellation-policy.json",
  "consumerCancellationLateEventsPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/consumer-cancellation-late-events-policy.json",
  "disconnectAfterSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/disconnect-after-subscription-policy.json",
  "duplicateCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/duplicate-completion-policy.json",
  "lateEmissionAfterCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime",
  "sdk/stream-runtime/late-emission-after-completion-policy.json",
  "streamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/stream-runtime/stream-runtime-readiness.json",
  "stream-runtime-readiness",
  "genericStreamEmissionPolicy",
  "genericStreamTerminalErrorPolicy",
  "genericStreamConnectionGuardPolicy",
  "genericStreamCancellationPolicy",
  "genericStreamDisconnectPolicy",
  "genericStreamCompletionPolicy",
  "actions",
  "startConnected",
  "checkConnection",
  "connectionChecked",
  "emissions",
  "completionSignals",
  "postCompletionEmissions",
  "completionEventCount",
  "activeObserverCount",
  "emittedValues",
  "errorEventCount",
  "cleanupCallbackCount",
  "cancelledStreams",
  "upstreamCancelled",
  "upstreamStarted",
  "terminalError",
  "lateEventPolicy",
  "suppress-after-consumer-cancellation",
  "preserve-source-order",
  "propagate-error-and-clear-observers",
  "fail-before-observer-registration",
  "skip-connection-check-and-register-observer",
  "idempotent-consumer-cancellation",
  "ignore-after-first-completion",
  "ignore-after-terminal-completion",
  "ordered-emission-before-completion",
  "terminal-error-propagation",
  "checked-disconnected-fails-before-observer",
  "unchecked-subscription-skips-connection-check",
  "consumer-cancellation-upstream-cancel",
  "post-cancellation-late-event-suppression",
  "disconnect-after-subscription-terminal",
  "disconnect-after-subscription-observer-cleanup",
  "disconnect-after-subscription-upstream-cancel",
  "duplicate-completion-idempotence",
  "post-completion-emission-suppression",
  "platform-stream-vector-reference-gate",
  "compile-verification-gate",
  "Stream values emitted before terminal completion must be delivered in source order.",
  "Terminal stream errors must propagate to consumers and clear observers without reporting normal completion.",
  "A checked stream subscription that starts disconnected must fail before observer registration or upstream work starts.",
  "An unchecked stream subscription must register the observer without querying transport connection state.",
  "Consumer cancellation must remove the observer, cancel upstream work once, and remain idempotent.",
  "After consumer cancellation, late stream values, terminal errors, and completion signals must not surface or mutate terminal counters.",
  "A stream that disconnects after observer registration must terminate consumers, clear observers, and cancel upstream work without leaking an active subscription.",
  "Complete or finish signals after the first terminal completion must be idempotent and must not re-register observers.",
  "Values emitted after terminal completion must not surface to consumers and must not re-register observers."
].freeze
D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS = [
  "d2hStreamRuntimeGoldenVectorDefinesExecutableCommonLateErrorAndCancellationPolicy",
  "d2hStreamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
  "sdk/d2h-notifications/stream-runtime-policy.json",
  "sdk/d2h-notifications/stream-runtime-readiness.json",
  "d2h-stream-runtime-readiness",
  "late-error-after-emitted-notification",
  "consumer-cancels-after-first-notification",
  "unknown-notification-between-known-values-is-filtered",
  "failed-subscribe-does-not-register-observer",
  "cancelledStreams",
  "upstreamCancelled",
  "ignoredAfterCancel",
  "mapped-value-before-late-error",
  "consumer-cancellation-upstream-cancel",
  "suppress-notifications-after-cancel",
  "unknown-notification-filtering",
  "failed-subscribe-no-observer",
  "active-observer-cleanup-gate",
  "facade-error-mapping-gate",
  "compile-verification-gate"
].freeze
PLATFORM_OWNED_COVERAGE_ROWS = {
  "BLE device session lifecycle" => ["Partial", "platform-owned", "Keep platform-specific"],
  "GATT clients" => ["Partial", "platform-owned", "Keep transport clients platform-specific"],
  "Android Bluedroid host behavior" => ["Platform-specific", "Do not migrate to common code"],
  "iOS CoreBluetooth host behavior" => ["Platform-specific", "Do not migrate to common code"]
}.freeze
FULL_COVERAGE_EXIT_CRITERIA_TERMS = [
  "Every row marked `Partial` has either new tests, documented platform-specific ownership, or a migration deferral note.",
  "Every parser that moves to KMP has shared golden vectors covering valid, invalid, empty, boundary, and unknown-value cases.",
  "Android and iOS characterization tests use the same vectors or prove equivalent expected behavior.",
  "Public facade tests prove Android and iOS APIs keep current semantics after shared delegation.",
  "Hardware-dependent tests are limited to smoke coverage; deterministic behavior is covered without physical devices.",
  "Validation commands are documented and runnable for the migrated slice."
].freeze
HARDWARE_SMOKE_VALIDATION_TERMS = [
  "## Hardware And Device Smoke Boundary",
  "Physical BLE hardware validation is smoke coverage for adapter wiring, radio availability, and real-device compatibility only",
  "must not replace deterministic golden-vector tests, shared `commonTest` policy tests, Android facade characterization, or iOS XCTest characterization before migration",
  "record the tested device/firmware, feature path, and result in the slice notes"
].freeze
GRADLE_BATCH_VALIDATION_TERMS = [
  "Batch several file, test, vector, and documentation edits before invoking Gradle validation",
  "coverage work does not stall on repeated manual app prompts",
  "Prefer the library and shared-module gates below",
  "do not run broad app or example Gradle tasks unless the slice actually changes app/example surfaces"
].freeze
TDD_MINIMUM_VALIDATION_TERMS = [
  "## Minimum Validation Before Merging a Slice",
  "KMP common tests for the slice pass.",
  "Existing Android tests for the slice pass.",
  "Existing iOS tests for the slice pass or an equivalent Apple-platform command is documented.",
  "New golden vectors are reviewed as API contracts.",
  "No unrelated platform code is refactored in the same slice."
].freeze
TDD_REGRESSION_POLICY_TERMS = [
  "## Regression Policy",
  "current behavior is documented by a characterization test",
  "new behavior is documented by an updated expected output",
  "reason is written in the migration slice notes",
  "consumer-visible only if release notes or a migration guide are updated"
].freeze
TDD_COVERAGE_EXPECTATION_TERMS = [
  "## Coverage Expectations",
  "Line coverage is less important than input coverage.",
  "every supported frame version",
  "every field boundary",
  "malformed payloads",
  "empty payloads",
  "unknown enum values",
  "every state transition and cancellation path"
].freeze
FIRST_RECOMMENDED_TDD_SLICE_TERMS = [
  "## First Recommended TDD Slice",
  "Start with a low-risk deterministic parser",
  "`PolarDeviceUuid`",
  "time utilities",
  "one PMD sensor parser with strong existing tests",
  "Do not start with BLE scanning, connection lifecycle, firmware network calls, or public API redesign."
].freeze
VALIDATION_MINIMUM_TDD_LINK_TERMS = [
  "`KmpTddStrategy.md` defines the minimum validation before merging a migration slice",
  "KMP common tests, existing Android tests, existing iOS tests or an equivalent documented Apple-platform command, reviewed golden vectors as API contracts, and no unrelated platform refactor",
  "current executable way to satisfy that minimum validation set"
].freeze
RELEASE_READINESS_ITEMS = [
  "Android AAR builds and is consumed by the Android example.",
  "Swift Package integration builds and is consumed by the iOS example.",
  "CocoaPods integration is verified or explicitly deprecated.",
  "Generated Android and iOS API docs are regenerated if public APIs changed.",
  "Migration guides are updated for consumer-visible changes.",
  "Known platform differences are documented.",
  "A rollback path exists for every shared module adoption step."
].freeze
MIGRATION_STOP_CONDITION_TERMS = [
  "## Stop Conditions",
  "Android and iOS current behavior disagree without a documented decision",
  "a parser has no golden-vector coverage",
  "a shared implementation requires platform APIs",
  "a platform facade changes public behavior unintentionally",
  "validation requires physical hardware for logic that should be fakeable"
].freeze
PER_SLICE_TDD_CHECKLIST_TERMS = [
  "## Per-Slice TDD Checklist",
  "Choose one behavior slice.",
  "List current Android implementation files.",
  "List current iOS implementation files.",
  "List existing Android tests.",
  "List existing iOS tests.",
  "Add or update Android characterization tests.",
  "Add or update iOS characterization tests.",
  "Add shared golden vectors.",
  "Add failing KMP common tests.",
  "Implement shared code.",
  "Delegate Android implementation to shared code.",
  "Delegate iOS implementation to shared code.",
  "Re-run Android, iOS, and KMP tests.",
  "Remove duplicated platform logic only after both facades pass.",
  "Update docs if public behavior changed."
].freeze
REVIEW_CHECKLIST_TERMS = [
  "## Review Checklist",
  "The pull request moves only one coherent behavior slice.",
  "Tests fail without the shared implementation.",
  "Golden vectors are understandable and minimal.",
  "Android and iOS public APIs remain compatible unless the change is explicitly documented.",
  "Platform-specific BLE behavior remains platform-specific.",
  "New common code has no hidden Android/JVM or Apple-only dependency.",
  "Error mapping is covered by tests.",
  "Cancellation and timeout behavior is covered when streams or suspend functions are involved.",
  "Build metadata does not depend on Android `BuildConfig` in common code."
].freeze
SUGGESTED_SLICE_ORDER_TERMS = [
  "## Suggested Slice Order",
  "1. Device ID and UUID utilities.",
  "2. Time and date utilities.",
  "3. Product capability JSON parsing.",
  "4. PMD settings and control-point response parsing.",
  "5. ECG parser.",
  "6. ACC, GYR, and MAG parsers.",
  "7. PPG and PPI parsers.",
  "8. Pressure, temperature, and skin-temperature parsers.",
  "9. Offline recording metadata and status parsing.",
  "10. Training-session metadata parsing.",
  "11. Firmware/status mapping.",
  "12. Shared fake transport contract.",
  "13. Runtime state machines.",
  "14. Public API compatibility adapters."
].freeze
PLATFORM_OWNED_BACKLOG_REQUIRED_TERMS = [
  "Platform-owned gaps: Android Bluedroid host behavior, iOS CoreBluetooth host behavior, GATT client host interactions, and platform identifier routing should stay platform-specific unless a future slice defines a pure codec or deterministic state machine contract.",
  "BLE device lifecycle and GATT clients: keep platform-owned unless a slice extracts a pure codec or deterministic state machine with common fake-transport tests."
].freeze
STALE_SHARED_RUNTIME_VECTOR_NOTES = ["still need dedicated fake-transport vectors", "future fake-transport vectors"].freeze
COVERED_SENSOR_READINESS_MANIFESTS = {
  "protocol/sensors/acc-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/ecg-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/gyr-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/mag-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/offline-hr-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/ppi-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/pressure-temperature-readiness.json" => "coveredByPreMigrationCharacterization",
  "protocol/sensors/skin-temperature-readiness.json" => "coveredByPreMigrationCharacterization"
}.freeze
LOWERCASE_HEX = /\A([0-9a-f]{2})*\z/
VECTOR_ID = /\A[a-z0-9][a-z0-9-]*\z/
VECTOR_CASE = /\A[a-z0-9][a-z0-9_]*\z/
SCHEMA_LOWERCASE_HEX_PATTERN = "^([0-9a-f]{2})*$"
SCHEMA_VECTOR_ID_PATTERN = "^[a-z0-9][a-z0-9-]*$"
SCHEMA_VECTOR_CASE_PATTERN = "^[a-z0-9][a-z0-9_]*$"
RUNTIME_POLICY_CONSUMER_TEST = /.*(Runtime|FakeWorkflow|FakeRuntime|TransportPolicy).*/
COMMON_TEST_PORTABILITY_FORBIDDEN = /digitToInt|toBooleanStrict|uppercase\(|lowercase\(|replaceFirstChar|ifEmpty|UL|UInt|UByte|ULong|java\.|android\.|com\.google/
COMMON_MAIN_PLATFORM_FORBIDDEN = /android\.|java\.|javax\.|CoreBluetooth|UIKit|Foundation|CryptoKit|SwiftUI|platform\.Core|com\.google|Bluetooth|BluetoothGatt|Context\b|GlobalScope|Dispatchers\.Main/
COMMON_MAIN_PORTABILITY_PLAN_TERMS = [
  "Common code must not depend on Android Bluetooth APIs, CoreBluetooth, UIKit, Swift-only concurrency types, JVM-only classes, Apple-only cryptography, or global mutable platform state.",
  "Shared code should receive byte arrays, timestamps, settings, and command results; platform code should translate those to Android or iOS BLE calls."
].freeze
COMMON_TEST_PORTABILITY_ALLOWED_LINES = [
  ["sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt", "protocol/device-id/identifier-bluetooth-address-android.json"],
  ["sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskSpaceCommonPolicyTest.kt", "Swift UInt32 behavior accidentally"],
  ["sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdControlPointCommonPolicyTest.kt", "ERROR_DISK_FULL"],
  ["sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt", "readUInt16Le"],
  ["sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TypeUtilsCommonPolicyTest.kt", "UInt64 max decimal preservation"]
].freeze
ANDROID_MIN_SDK_DOCS = ["README.md", "documentation/MigrationGuide7.0.0-Android.md"].freeze
KMP_COMMON_VECTOR_HELPER_ARTIFACTS = [
  "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestData.kt",
  "sources/Android/android-communications/shared/src/jvmTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataJvm.kt",
  "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt"
].freeze
SHARED_CONSUMPTION_REQUIRED_TERMS = [
  "implementation project(':shared')",
  "PolarBleSdkShared.framework",
  "current Swift facade",
  ":shared:bundleAndroidMainAar",
  ":shared:linkDebugFrameworkIosX64",
  "may depend on shared code only when a behavior slice",
  "scripts/verify_android_example_aar_consumption.sh",
  "polar-ble-sdk-shared.aar",
  "SwiftPM/watchOS",
  "fallback-only",
  "rollback path for every shared-module adoption step"
].freeze
KOTLIN_DOCUMENT_REQUIRED_TERM_TARGETS = {
  "VALIDATION_NON_GRADLE_GATE_TERMS" => "documentation/KmpValidationCommands.md",
  "HARDWARE_SMOKE_VALIDATION_TERMS" => "documentation/KmpValidationCommands.md",
  "GRADLE_BATCH_VALIDATION_TERMS" => "documentation/KmpValidationCommands.md",
  "VALIDATION_MINIMUM_TDD_LINK_TERMS" => "documentation/KmpValidationCommands.md",
  "KMP_TDD_STRATEGY_VECTOR_EXAMPLE_TERMS" => "documentation/KmpTddStrategy.md",
  "TDD_MINIMUM_VALIDATION_TERMS" => "documentation/KmpTddStrategy.md",
  "TDD_REGRESSION_POLICY_TERMS" => "documentation/KmpTddStrategy.md",
  "TDD_COVERAGE_EXPECTATION_TERMS" => "documentation/KmpTddStrategy.md",
  "FIRST_RECOMMENDED_TDD_SLICE_TERMS" => "documentation/KmpTddStrategy.md",
  "IOS_XCODE_PROBE_REQUIRED_TERMS" => "scripts/ios_xcode_validation_probe.rb",
  "IOS_XCTEST_EXECUTION_GATE_REQUIRED_TERMS" => "documentation/KmpValidationCommands.md",
  "IOS_XCTEST_REMAINING_WORK_REQUIRED_TERMS" => "documentation/KmpPreMigrationRemainingWork.md",
  "SHARED_CONSUMPTION_REQUIRED_TERMS" => "documentation/KmpSharedArtifactConsumption.md"
}.freeze
KOTLIN_DOCUMENT_FORBIDDEN_TERM_TARGETS = {
  "KMP_TDD_STRATEGY_STALE_VECTOR_EXAMPLE_TERMS" => "documentation/KmpTddStrategy.md"
}.freeze
KOTLIN_SHARED_TERM_CONSTANTS = [
  "MIGRATION_README_TERMS"
].freeze
ANDROID_MIN_SDK_VERSION = /minSdk(?:Version)?\s*(?:=)?\s*(\d+)/
ANDROID_MIN_SDK_REFERENCE = /minSdk(?:Version)?\s*(?:=)?\s*\**\s*(\d+)/
PODSPEC_SOURCE_FILES = /s\.source_files\s*=\s*'([^']+)'/
PODSPEC_RESOURCES = /s\.resources\s*=\s*\[(.*)\]/
PODSPEC_RESOURCE_REFERENCE = /'(sources\/iOS\/ios-communications\/Sources\/[A-Za-z0-9_.\/-]+)'/

def relative(path)
  path.delete_prefix("#{ROOT}/")
end

def fail_with(errors)
  return if errors.empty?

  warn errors.join("\n")
  abort "kmp_non_gradle_checks failed with #{errors.size} violation(s)"
end

def json_files
  Dir[File.join(VECTOR_ROOT, "**/*.json")].sort
end

def vector_files
  json_files.reject { |path| path == SCHEMA_PATH }
end

def read_json(path)
  JSON.parse(File.read(path))
rescue JSON::ParserError => e
  raise "#{relative(path)}: invalid JSON: #{e.message}"
end

def each_string_value(value, &block)
  case value
  when String
    yield value
  when Array
    value.each { |item| each_string_value(item, &block) }
  when Hash
    value.each_value { |item| each_string_value(item, &block) }
  end
end

def contains_string_value?(json, expected)
  found = false
  each_string_value(json) { |value| found = true if value == expected }
  found
end

def invalid_hex_fields(value, path = "", invalid = [])
  case value
  when Hash
    value.each { |key, item| invalid_hex_fields(item, path.empty? ? key : "#{path}.#{key}", invalid) }
  when Array
    value.each_with_index { |item, index| invalid_hex_fields(item, "#{path}[#{index}]", invalid) }
  when String
    invalid << "#{path}=#{value}" if path.end_with?("Hex") && !value.match?(LOWERCASE_HEX)
  end
  invalid
end

def kotlin_class_name(path, root)
  relative_path = path.delete_prefix("#{root}/").delete_suffix(".kt")
  relative_path.split("/").join(".")
end

def android_test_file(class_name)
  File.join(ANDROID_TEST_ROOT, "#{class_name.tr(".", "/")}.kt")
end

def common_test_file(class_name)
  shared = File.join(COMMON_TEST_ROOT, "#{class_name.tr(".", "/")}.kt")
  File.file?(shared) ? shared : android_test_file(class_name)
end

def ios_test_files(test_name)
  Dir[File.join(IOS_TEST_ROOT, "**/*.swift")].select do |path|
    File.basename(path, ".swift") == test_name || File.read(path).match?(/\b(class|struct)\s+#{Regexp.escape(test_name)}\b/)
  end
end

def manifests
  @manifests ||= vector_files.map { |path| [path, read_json(path)] }
end

def references_vector_or_manifest?(test_source, vector_path, vector_json)
  vector_id = vector_json.fetch("id")
  vector_filename = File.basename(vector_path)
  vector_directory = relative(File.dirname(vector_path))
  vector_relative_without_root = relative(vector_path).delete_prefix("testdata/golden-vectors/")
  return true if test_source.include?(vector_id) || test_source.include?(vector_filename) || test_source.include?(vector_directory)

  manifests.any? do |manifest_path, manifest_json|
    next false if manifest_path == vector_path
    next false unless contains_string_value?(manifest_json, vector_relative_without_root)

    manifest_id = manifest_json.fetch("id")
    test_source.include?(manifest_id) || test_source.include?(File.basename(manifest_path))
  end
end

def consumer_references(vector_json)
  consumer_tests = vector_json["consumerTests"]
  return [] unless consumer_tests.is_a?(Hash)

  consumer_tests.flat_map do |platform, names|
    next [[platform, "<invalid>"]] unless CONSUMER_PLATFORMS.include?(platform) && names.is_a?(Array) && !names.empty?

    names.map { |name| [platform, name.is_a?(String) ? name : "<invalid>"] }
  end
end

def vector_behavior_ids(json)
  ids = []
  input = json["input"].is_a?(Hash) ? json["input"] : {}
  %w[cases scenarios requests operations].each do |field|
    values = input[field]
    next unless values.is_a?(Array)

    ids.concat(values.filter_map { |item| item["id"] if item.is_a?(Hash) && item["id"].is_a?(String) })
  end
  expected = json["expected"].is_a?(Hash) ? json["expected"] : {}
  %w[commonRuntimePrototype commonParserPrototype commonWorkflowPrototype commonByteCodecPrototype commonPrototype].each do |field|
    [expected[field], json[field]].each do |prototype|
      cases = prototype.is_a?(Hash) ? prototype["cases"] : nil
      next unless cases.is_a?(Array)

      ids.concat(cases.filter_map { |item| item["id"] if item.is_a?(Hash) && item["id"].is_a?(String) })
    end
  end
  ids.uniq
end

def migration_policy_rationale?(json)
  expected = json["expected"].is_a?(Hash) ? json["expected"] : {}
  platform_expectations = json["platformExpectations"].is_a?(Hash) ? json["platformExpectations"] : {}
  expected.key?("commonDecision") ||
    expected.key?("commonRuntimePrototype") ||
    expected.key?("commonWorkflowPrototype") ||
    expected.key?("migrationOwnership") ||
    platform_expectations.key?("commonDecision")
end

def runtime_planning_vector?(json)
  expected = json["expected"].is_a?(Hash) ? json["expected"] : {}
  json.key?("execution") || expected.key?("commonRuntimePrototype") || expected.key?("commonWorkflowPrototype")
end

def runtime_policy_consumer?(json)
  consumer_references(json).any? { |_platform, test_name| test_name.match?(RUNTIME_POLICY_CONSUMER_TEST) }
end

def table_rows(text)
  text.lines.filter_map do |line|
    next unless line.start_with?("|")
    next if line.start_with?("|---") || line.include?("Behavior family")

    line.strip.delete_prefix("|").delete_suffix("|").split("|").map(&:strip)
  end
end

def section_between(text, start_heading, end_heading)
  start_index = text.index(start_heading)
  return "" unless start_index

  end_index = text.index(end_heading, start_index + start_heading.length)
  end_index ? text[start_index...end_index] : text[start_index..]
end

def looks_like_artifact_reference?(reference)
  reference.include?("/") || reference.end_with?(".md", ".json", ".kt", ".swift", ".podspec")
end

def resolve_evidence_reference(reference)
  direct = File.join(ROOT, reference)
  return direct if File.exist?(direct)

  File.join(ROOT, "documentation", reference)
end

def git_status_short(*paths)
  command = ["git", "status", "--short", "--", *paths]
  output = IO.popen(command, chdir: ROOT, err: [:child, :out], &:read)
  return ["git status failed for #{paths.join(", ")}"] unless $?.success?

  output.lines.map(&:strip).reject(&:empty?)
end

def has_migration_gate_language?(text)
  lower = text.downcase
  PARTIAL_ROW_GATE_TERMS.any? { |term| lower.include?(term) }
end

def kotlin_required_term_constants(policy_text)
  constants = {}
  policy_text.scan(/val\s+([A-Z0-9_]+(?:_REQUIRED)?_TERMS)\s*=\s*listOf\(/) do |name_match|
    name = name_match.first
    body = kotlin_call_body_after(policy_text, "val #{name} = listOf")
    constants[name] = body.scan(/"((?:\\"|[^"])*)"/).flatten.map { |term| term.gsub('\"', '"') } if body
  end
  constants
end

def kotlin_call_body_after(policy_text, marker)
  start = policy_text.index(marker)
  return nil unless start

  open_index = policy_text.index("(", start)
  return nil unless open_index

  depth = 0
  in_string = false
  escaped = false
  index = open_index
  while index < policy_text.length
    char = policy_text[index]
    if in_string
      if escaped
        escaped = false
      elsif char == "\\"
        escaped = true
      elsif char == '"'
        in_string = false
      end
    elsif char == '"'
      in_string = true
    elsif char == "("
      depth += 1
    elsif char == ")"
      depth -= 1
      return policy_text[(open_index + 1)...index] if depth.zero?
    end
    index += 1
  end
  nil
end

def kotlin_required_term_map(policy_text, map_name, term_constants)
  body = kotlin_call_body_after(policy_text, "val #{map_name} = mapOf")
  return nil unless body

  result = {}
  body.scan(/"((?:\\"|[^"])*)"\s+to\s+(listOf\((?:.|\n)*?\)|[A-Z0-9_]+_REQUIRED_TERMS)/m) do |key, value|
    key = key.gsub('\"', '"')
    result[key] =
      if value.start_with?("listOf(")
        value.scan(/"((?:\\"|[^"])*)"/).flatten.map { |term| term.gsub('\"', '"') }
      else
        term_constants[value] || []
      end
  end
  result
end

def kotlin_policy_required_term_checks(policy_text)
  checks = []
  policy_text.scan(/@Test\s+fun `.*?`\(\) \{(.*?)(?=\n\s*@Test|\n\s*private fun)/m) do |block_match|
    block = block_match.first
    paths_by_var = {}
    block.scan(/val\s+([A-Za-z0-9]+)\s*=\s*root\.resolve\("([^"]+\.kt)"\)/) do |var_name, path|
      paths_by_var[var_name] = path
    end
    block.scan(/([A-Z0-9_]+_REQUIRED_TERMS)\s*\n\s*\.filterNot\s*\{\s*term\s*->\s*([A-Za-z0-9]+)Text\.contains\(term\)\s*\}/) do |constant_name, text_var|
      file_var = text_var.sub(/Text\z/, "")
      path = paths_by_var[file_var]
      checks << [constant_name, path] if path
    end
  end
  checks
end

def kotlin_policy_text_contains_checks(policy_text)
  checks = []
  policy_text.scan(/@Test\s+fun `.*?`\(\) \{(.*?)(?=\n\s*@Test|\n\s*private fun)/m) do |block_match|
    block = block_match.first
    paths_by_var = {}
    block.scan(/val\s+([A-Za-z0-9]+)\s*=\s*root\.resolve\("([^"]+)"\)\.readText\(\)/) do |var_name, path|
      paths_by_var[var_name] = path
    end
    block.scan(/!\s*([A-Za-z0-9]+)\.contains\("((?:\\"|[^"])*)"\)/) do |var_name, term|
      path = paths_by_var[var_name]
      checks << [path, term.gsub('\"', '"')] if path
    end
  end
  checks
end

errors = []
parsed_vectors = []

json_files.each do |path|
  read_json(path)
rescue RuntimeError => e
  errors << e.message
end
fail_with(errors)

schema = read_json(SCHEMA_PATH)
schema_required = schema.fetch("required")
errors << "#{relative(SCHEMA_PATH)}: required fields #{schema_required.inspect} != #{REQUIRED_FIELDS.inspect}" unless schema_required == REQUIRED_FIELDS
errors << "#{relative(SCHEMA_PATH)}: root additionalProperties must be false" unless schema["additionalProperties"] == false
schema_top_level_fields = schema.dig("properties")&.keys&.sort
errors << "#{relative(SCHEMA_PATH)}: top-level fields #{schema_top_level_fields.inspect} != #{TOP_LEVEL_FIELDS.sort.inspect}" unless schema_top_level_fields == TOP_LEVEL_FIELDS.sort
errors << "#{relative(SCHEMA_PATH)}: id pattern must match executable policy" unless schema.dig("properties", "id", "pattern") == SCHEMA_VECTOR_ID_PATTERN
errors << "#{relative(SCHEMA_PATH)}: case pattern must match executable policy" unless schema.dig("properties", "case", "pattern") == SCHEMA_VECTOR_CASE_PATTERN
errors << "#{relative(SCHEMA_PATH)}: input.hex pattern must match executable policy" unless schema.dig("properties", "input", "properties", "hex", "pattern") == SCHEMA_LOWERCASE_HEX_PATTERN
schema_platform_fields = schema.dig("properties", "platforms", "properties")&.keys&.sort
schema_required_platform_fields = schema.dig("properties", "platforms", "required")
schema_platform_additional_properties = schema.dig("properties", "platforms", "additionalProperties")
errors << "#{relative(SCHEMA_PATH)}: platform fields #{schema_platform_fields.inspect} required #{schema_required_platform_fields.inspect} != #{PLATFORM_FIELDS.inspect}" unless schema_platform_fields == PLATFORM_FIELDS.sort && schema_required_platform_fields == PLATFORM_FIELDS
errors << "#{relative(SCHEMA_PATH)}: platforms additionalProperties must be false" unless schema_platform_additional_properties == false
schema_consumer_platforms = schema.dig("properties", "consumerTests", "properties")&.keys&.sort
schema_consumer_additional_properties = schema.dig("properties", "consumerTests", "additionalProperties")
schema_consumer_any_of = schema.dig("properties", "consumerTests", "anyOf")
expected_consumer_any_of = CONSUMER_PLATFORMS.map { |platform| { "required" => [platform] } }
errors << "#{relative(SCHEMA_PATH)}: consumerTests platforms #{schema_consumer_platforms.inspect} != #{CONSUMER_PLATFORMS.sort.inspect}" unless schema_consumer_platforms == CONSUMER_PLATFORMS.sort
errors << "#{relative(SCHEMA_PATH)}: consumerTests additionalProperties must be false" unless schema_consumer_additional_properties == false
errors << "#{relative(SCHEMA_PATH)}: consumerTests anyOf #{schema_consumer_any_of.inspect} != #{expected_consumer_any_of.inspect}" unless schema_consumer_any_of == expected_consumer_any_of

ids_by_value = Hash.new { |hash, key| hash[key] = [] }
vector_files.each do |path|
  json = read_json(path)
  parsed_vectors << [path, json]
  missing = REQUIRED_FIELDS.reject { |field| json.key?(field) }
  platforms = json["platforms"]
  errors << "#{relative(path)}: missing required fields #{missing.join(", ")}" unless missing.empty?
  if platforms.is_a?(Hash)
    PLATFORM_FIELDS.each do |field|
      errors << "#{relative(path)}: missing platforms.#{field}" unless platforms.key?(field)
    end
  else
    PLATFORM_FIELDS.each do |field|
      errors << "#{relative(path)}: missing platforms.#{field}"
    end
  end
  vector_id = json["id"]
  ids_by_value[vector_id] << relative(path) if vector_id
  errors << "#{relative(path)}: id must use lowercase kebab-case" unless vector_id.is_a?(String) && vector_id.match?(VECTOR_ID)
  vector_case = json["case"]
  errors << "#{relative(path)}: case must use lowercase snake_case" unless vector_case.is_a?(String) && vector_case.match?(VECTOR_CASE)
  vector_source = json["source"]
  unless vector_source.is_a?(String) && SOURCE_PROVENANCE_TERMS.any? { |term| vector_source.include?(term) }
    errors << "#{relative(path)}: source must name traceable characterization, readiness, planning, policy, prototype, migration, Android, iOS, KMP, or shared evidence"
  end
  json.keys.reject { |field| TOP_LEVEL_FIELDS.include?(field) }.each do |field|
    errors << "#{relative(path)}: unknown top-level field #{field}"
  end
  invalid_hex_fields(json).each do |invalid|
    errors << "#{relative(path)}: invalid hex field #{invalid}"
  end
  if platforms.is_a?(Hash) && platforms.key?("common") && platforms["common"] == false && !migration_policy_rationale?(json)
    errors << "#{relative(path)}: platforms.common=false requires common migration policy rationale"
  end
  vector_relative_without_root = relative(path).delete_prefix("testdata/golden-vectors/")
  covered_sensor_readiness = COVERED_SENSOR_READINESS_MANIFESTS[vector_relative_without_root]
  if covered_sensor_readiness
    readiness = json.fetch("expected", {})["migrationReadiness"]
    unless readiness == covered_sensor_readiness
      errors << "#{relative(path)}: expected.migrationReadiness must be #{covered_sensor_readiness.inspect} after Android, iOS, and shared common characterization coverage is executable"
    end
  end

  consumer_tests = json["consumerTests"]
  unless consumer_tests.is_a?(Hash) && !consumer_tests.empty?
    errors << "#{relative(path)}: consumerTests must be a non-empty object"
    next
  end

  consumer_tests.each do |platform, names|
    errors << "#{relative(path)}: unknown consumerTests.#{platform}" unless CONSUMER_PLATFORMS.include?(platform)
    unless names.is_a?(Array) && !names.empty?
      errors << "#{relative(path)}: consumerTests.#{platform} must be a non-empty array"
      next
    end
    names.each_with_index do |name, index|
      errors << "#{relative(path)}: consumerTests.#{platform}[#{index}] must be a non-empty string" unless name.is_a?(String) && !name.strip.empty?
    end
  end

  if runtime_policy_consumer?(json) && !runtime_planning_vector?(json)
    errors << "#{relative(path)}: runtime/fake-transport consumer requires execution or common runtime/workflow metadata"
  end
  if runtime_planning_vector?(json)
    %w[android ios commonPrototype].each do |platform|
      names = consumer_tests.is_a?(Hash) ? consumer_tests[platform] : nil
      errors << "#{relative(path)}: runtime planning vector missing consumerTests.#{platform}" unless names.is_a?(Array) && !names.empty?
    end
    common_names = consumer_tests.is_a?(Hash) && consumer_tests["commonPrototype"].is_a?(Array) ? consumer_tests["commonPrototype"] : []
    unless common_names.any? { |test_name| test_name.is_a?(String) && test_name.start_with?("com.polar.sharedtest.") }
      errors << "#{relative(path)}: runtime planning vector must name executable shared commonTest consumer"
    end
  end
end
ids_by_value.each do |id, paths|
  errors << "duplicate golden vector id #{id}: #{paths.join(", ")}" if paths.size > 1
end

fixture_directories = Dir[File.join(VECTOR_ROOT, "**/")].select do |directory|
  Dir[File.join(directory, "*.json")].any? { |path| File.basename(path) != "golden-vector.schema.json" }
end
root_readme = File.read(File.join(VECTOR_ROOT, "README.md"))
fixture_directories.each do |directory|
  errors << "#{relative(directory).delete_suffix("/")}: missing README.md migration ownership notes" unless File.file?(File.join(directory, "README.md"))
  relative_directory = directory.delete_prefix("#{VECTOR_ROOT}/").delete_suffix("/")
  errors << "testdata/golden-vectors/README.md: missing directory listing #{relative_directory}/" unless root_readme.include?("#{relative_directory}/")
end
actual_fixture_directory_listings = fixture_directories.map { |directory| directory.delete_prefix("#{VECTOR_ROOT}/").delete_suffix("/") + "/" }.sort
readme_fixture_directory_listings = root_readme.scan(ROOT_README_FIXTURE_DIRECTORY_LISTING).flatten.sort
stale_fixture_directory_listings = readme_fixture_directory_listings - actual_fixture_directory_listings
errors << "testdata/golden-vectors/README.md: stale directory listings #{stale_fixture_directory_listings.join(", ")}" unless stale_fixture_directory_listings.empty?
ROOT_GOLDEN_VECTOR_README_SCHEMA_TERMS.each do |term|
  errors << "testdata/golden-vectors/README.md: missing schema/migration term #{term}" unless root_readme.include?(term)
end
non_json_reference_text = Dir[
  File.join(ROOT, "documentation/**/*.md"),
  File.join(ROOT, "testdata/golden-vectors/**/*.md"),
  File.join(ROOT, "sources/Android/android-communications/**/*.kt"),
  File.join(ROOT, "sources/iOS/ios-communications/Tests/**/*.swift")
].select { |path| File.file?(path) }.map { |path| File.read(path) }.join("\n")
vector_files.each do |path|
  relative_vector_path = relative(path).delete_prefix("testdata/golden-vectors/")
  unless non_json_reference_text.include?(relative_vector_path)
    errors << "#{relative(path)}: vector path must be referenced by a test, README, or migration document"
  end
end

fail_with(errors)

shared_common_sources = Dir[File.join(COMMON_TEST_ROOT, "**/*.kt")].map { |path| File.read(path) }.join("\n")

parsed_vectors.each do |path, json|
  vector_relative_without_root = relative(path).delete_prefix("testdata/golden-vectors/")
  platforms = json["platforms"].is_a?(Hash) ? json["platforms"] : {}
  common_decision = json.key?("commonDecision") || json.dig("platformExpectations", "commonDecision") || json.dig("expected", "commonDecision") || json.dig("expected", "policy")
  if (platforms["common"] == true || common_decision) && !shared_common_sources.include?(vector_relative_without_root)
    errors << "#{relative(path)}: common-owned vector is not referenced from shared commonTest sources"
  end

  consumer_references(json).each do |platform, test_name|
    test_files = case platform
                 when "android"
                   [android_test_file(test_name)].select { |candidate| File.file?(candidate) }
                 when "commonPrototype"
                   [common_test_file(test_name)].select { |candidate| File.file?(candidate) }
                 when "ios"
                   ios_test_files(test_name)
                 else
                   []
                 end
    if test_files.empty?
      errors << "#{relative(path)}: #{platform}:#{test_name} does not resolve to a test file"
    elsif test_files.none? { |test_file| references_vector_or_manifest?(File.read(test_file), path, json) }
      errors << "#{relative(path)}: #{platform}:#{test_name} must reference vector id, filename, exact vector directory, or owning readiness manifest"
    end
  end

  shared_common_consumers = consumer_references(json).select { |platform, test_name| platform == "commonPrototype" && test_name.start_with?("com.polar.sharedtest.") }
  readiness_input_kind = json["input"].is_a?(Hash) ? json["input"]["kind"].to_s.downcase : ""
  if readiness_input_kind.include?("readiness") && json["platforms"].is_a?(Hash)
    shared_common_consumers.each do |_, test_name|
      test_file = common_test_file(test_name)
      next unless File.file?(test_file)

      test_source = File.read(test_file)
      PLATFORM_FIELDS.each do |field|
        expected_value = json["platforms"][field]
        next if test_source.include?(field) && test_source.include?(expected_value.inspect)

        errors << "#{relative(path)}: commonPrototype:#{test_name} must assert platforms.#{field}=#{expected_value.inspect}"
      end
    end
  end
  behavior_ids = vector_behavior_ids(json)
  unless behavior_ids.empty?
    shared_common_consumers.each do |_, test_name|
      test_file = common_test_file(test_name)
      next unless File.file?(test_file)

      test_source = File.read(test_file)
      behavior_ids.reject { |id| test_source.include?(id) }.each do |id|
        errors << "#{relative(path)}: commonPrototype:#{test_name} must explicitly reference behavior id #{id}"
      end
    end
  end

  shared_common_consumed = !shared_common_consumers.empty?
  notes = json["notes"]
  if shared_common_consumed && notes.is_a?(String)
    STALE_SHARED_RUNTIME_VECTOR_NOTES.each do |stale_phrase|
      errors << "#{relative(path)}: stale shared-runtime wording #{stale_phrase}" if notes.include?(stale_phrase)
    end
  end
end

validation_doc = File.read(File.join(ROOT, "documentation/KmpValidationCommands.md"))
%w[consumerTests portability\ allowlist].each do |term|
  errors << "documentation/KmpValidationCommands.md: missing #{term}" unless validation_doc.include?(term.tr("\\", ""))
end
["exact vector directory", "owning readiness manifest"].each do |term|
  errors << "documentation/KmpValidationCommands.md: missing #{term}" unless validation_doc.include?(term)
  errors << "testdata/golden-vectors/README.md: missing #{term}" unless File.read(File.join(VECTOR_ROOT, "README.md")).include?(term)
end

strategy = File.read(File.join(ROOT, "documentation/KmpTddStrategy.md"))
REQUIRED_FIELDS.each do |field|
  errors << "documentation/KmpTddStrategy.md: example missing #{field}" unless strategy.include?(%("#{field}"))
end
TDD_MINIMUM_VALIDATION_TERMS.each do |term|
  errors << "documentation/KmpTddStrategy.md: missing minimum validation term #{term}" unless strategy.include?(term)
end
TDD_REGRESSION_POLICY_TERMS.each do |term|
  errors << "documentation/KmpTddStrategy.md: missing regression policy term #{term}" unless strategy.include?(term)
end
TDD_COVERAGE_EXPECTATION_TERMS.each do |term|
  errors << "documentation/KmpTddStrategy.md: missing coverage expectation term #{term}" unless strategy.include?(term)
end
FIRST_RECOMMENDED_TDD_SLICE_TERMS.each do |term|
  errors << "documentation/KmpTddStrategy.md: missing first recommended TDD slice term #{term}" unless strategy.include?(term)
end

policy_test_path = File.join(ROOT, "sources/Android/android-communications/library/src/test/java/com/polar/sdk/api/model/utils/GoldenVectorMigrationPolicyTest.kt")
policy_test_text = File.read(policy_test_path)
required_term_constants = kotlin_required_term_constants(policy_test_text)
policy_required_term_checks = kotlin_policy_required_term_checks(policy_test_text)
policy_required_term_check_names = policy_required_term_checks.map(&:first).to_set
common_policy_required_term_constants = required_term_constants.keys.select { |name| name.match?(/\A(?:[A-Z0-9_]+_COMMON_POLICY|D2H_STREAM_RUNTIME_COMMON_POLICY)_REQUIRED_TERMS\z/) }.sort
common_policy_required_term_constants.each do |constant_name|
  errors << "scripts/kmp_non_gradle_checks.rb: failed to discover Kotlin policy target check for #{constant_name}" unless policy_required_term_check_names.include?(constant_name)
end
KOTLIN_DOCUMENT_REQUIRED_TERM_TARGETS.each do |constant_name, relative_path|
  terms = required_term_constants[constant_name]
  if terms.nil?
    errors << "GoldenVectorMigrationPolicyTest.kt: missing document required-term constant #{constant_name}"
    next
  end

  target_path = File.join(ROOT, relative_path)
  unless File.file?(target_path)
    errors << "#{relative_path}: missing target for #{constant_name}"
    next
  end

  target_text = File.read(target_path)
  terms.each do |term|
    errors << "#{relative_path}: missing #{constant_name} term #{term}" unless target_text.include?(term)
  end
end
KOTLIN_DOCUMENT_FORBIDDEN_TERM_TARGETS.each do |constant_name, relative_path|
  terms = required_term_constants[constant_name]
  if terms.nil?
    errors << "GoldenVectorMigrationPolicyTest.kt: missing forbidden document term constant #{constant_name}"
    next
  end

  target_path = File.join(ROOT, relative_path)
  unless File.file?(target_path)
    errors << "#{relative_path}: missing target for #{constant_name}"
    next
  end

  target_text = File.read(target_path)
  terms.each do |term|
    errors << "#{relative_path}: stale #{constant_name} term #{term}" if target_text.include?(term)
  end
end
KOTLIN_SHARED_TERM_CONSTANTS.each do |constant_name|
  errors << "GoldenVectorMigrationPolicyTest.kt: missing shared term constant #{constant_name}" unless required_term_constants.key?(constant_name)
end
{
  "FACADE_GATE_OPEN_REQUIRED_TERMS" => FACADE_GATE_OPEN_REQUIRED_TERMS,
  "RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS" => RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS,
  "PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS" => PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS,
  "BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS" => BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS,
  "PLATFORM_OWNED_COVERAGE_ROWS" => PLATFORM_OWNED_COVERAGE_ROWS
}.each do |constant_name, ruby_map|
  kotlin_map = kotlin_required_term_map(policy_test_text, constant_name, required_term_constants)
  if kotlin_map.nil?
    errors << "GoldenVectorMigrationPolicyTest.kt: missing map required-term constant #{constant_name}"
    next
  end

  errors << "scripts/kmp_non_gradle_checks.rb: #{constant_name} map must exactly mirror GoldenVectorMigrationPolicyTest.kt" unless ruby_map == kotlin_map
end
fake_transport_common_required_term_constants = required_term_constants.keys.select { |name| name.match?(/\AFAKE_TRANSPORT_COMMON_.*_TEST_REQUIRED_TERMS\z/) }.sort
ruby_fake_transport_common_required_term_constants = Object.constants.map(&:to_s).select { |name| name.match?(/\AFAKE_TRANSPORT_COMMON_.*_TEST_REQUIRED_TERMS\z/) }.sort
(fake_transport_common_required_term_constants - ruby_fake_transport_common_required_term_constants).each do |constant_name|
  errors << "scripts/kmp_non_gradle_checks.rb: missing Ruby mirror for #{constant_name}"
end
(ruby_fake_transport_common_required_term_constants - fake_transport_common_required_term_constants).each do |constant_name|
  errors << "scripts/kmp_non_gradle_checks.rb: stale Ruby fake-transport common mirror #{constant_name}"
end
fake_transport_common_required_term_constants.each do |constant_name|
  next unless Object.const_defined?(constant_name)

  ruby_terms = Object.const_get(constant_name)
  kotlin_terms = required_term_constants[constant_name]
  errors << "scripts/kmp_non_gradle_checks.rb: #{constant_name} terms must exactly mirror GoldenVectorMigrationPolicyTest.kt" unless ruby_terms == kotlin_terms
end
d2h_stream_terms = required_term_constants["D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS"]
if d2h_stream_terms.nil?
  errors << "GoldenVectorMigrationPolicyTest.kt: missing D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS"
elsif D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS != d2h_stream_terms
  errors << "scripts/kmp_non_gradle_checks.rb: D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS terms must exactly mirror GoldenVectorMigrationPolicyTest.kt"
end
policy_required_term_checks.each do |constant_name, relative_path|
  terms = required_term_constants[constant_name]
  if terms.nil?
    errors << "GoldenVectorMigrationPolicyTest.kt: missing required-term constant #{constant_name}"
    next
  end
  target_path = File.join(ROOT, relative_path)
  unless File.file?(target_path)
    errors << "#{relative_path}: missing target for #{constant_name}"
    next
  end
  target_text = File.read(target_path)
  terms.each do |term|
    errors << "#{relative_path}: missing #{constant_name} term #{term}" unless target_text.include?(term)
  end
end
kotlin_policy_text_contains_checks(policy_test_text).uniq.each do |relative_path, term|
  if term.start_with?("No shared KMP module") && !Dir[File.join(ROOT, "**/commonTest")].select { |path| File.directory?(path) }.empty?
    next
  end
  target_path = File.join(ROOT, relative_path)
  unless File.file?(target_path)
    errors << "#{relative_path}: missing target for policy contains check #{term}"
    next
  end
  errors << "#{relative_path}: missing policy-required text #{term}" unless File.read(target_path).include?(term)
end

existing_test_files = Dir[File.join(ROOT, "**/*")].select { |path| File.file?(path) && File.basename(path).match?(TEST_FILE_NAMES) }.map { |path| File.basename(path) }.to_set
KMP_COVERAGE_DOCS.each do |doc_path|
  text = File.read(File.join(ROOT, doc_path))
  text.scan(TEST_REFERENCE).flatten.each do |reference|
    errors << "#{doc_path}: missing #{reference}" unless existing_test_files.include?(File.basename(reference))
  end
end

common_policy_tests = Dir[File.join(COMMON_TEST_ROOT, "**/*Test.kt")].map { |path| File.basename(path) }.reject { |name| SHARED_COMMON_TEST_DOC_EXCLUSIONS.include?(name) }
shared_common_docs_text = KMP_SHARED_COMMON_TEST_DOCS.map { |doc_path| File.read(File.join(ROOT, doc_path)) }.join("\n")
common_policy_tests.each do |test_name|
  errors << "KMP migration documentation: missing shared common test artifact #{test_name}" unless shared_common_docs_text.include?(test_name)
end
common_coverage_section = File.read(File.join(ROOT, "documentation/KmpFullCoverageTddBacklog.md"))[CURRENT_EXECUTABLE_COMMON_COVERAGE_SECTION] || ""
common_policy_tests.each do |test_name|
  errors << "documentation/KmpFullCoverageTddBacklog.md: Current Executable Common Coverage missing #{test_name}" unless common_coverage_section.include?(test_name)
end
backlog = File.read(File.join(ROOT, "documentation/KmpFullCoverageTddBacklog.md"))
PLATFORM_OWNED_BACKLOG_REQUIRED_TERMS.each do |term|
  errors << "documentation/KmpFullCoverageTddBacklog.md: missing platform-owned boundary term #{term}" unless backlog.include?(term)
end

existing_kotlin_tests = Dir[File.join(ROOT, "sources/Android/android-communications/**/*.kt")].select { |path| File.file?(path) && File.basename(path).end_with?("Test.kt") }.map { |path| File.basename(path) }.to_set
migration_readme_terms = required_term_constants["MIGRATION_README_TERMS"] || []
Dir[File.join(VECTOR_ROOT, "**/README.md")].sort.each do |readme_path|
  text = File.read(readme_path)
  lower_text = text.downcase
  unless lower_text.include?("kmp") && migration_readme_terms.any? { |term| lower_text.include?(term) }
    errors << "#{relative(readme_path)}: missing KMP/common migration context"
  end
  text.scan(FIXTURE_README_BARE_SHARED_COMMON_TEST_REFERENCE).flatten.each do |reference|
    errors << "#{relative(readme_path)}: bare shared test reference `#{reference}` must include .kt"
  end
  text.scan(FIXTURE_README_KOTLIN_ARTIFACT_REFERENCE).flatten.each do |reference|
    errors << "#{relative(readme_path)}: missing #{reference}" unless existing_kotlin_tests.include?(reference)
  end
end

coverage_inventory = File.read(File.join(ROOT, "documentation/KmpCoverageInventory.md"))
coverage_rows = table_rows(coverage_inventory)
coverage_rows.each do |row|
  next unless row.size >= 5

  behavior = row[0]
  status = row[3]
  required = row[4]
  errors << "documentation/KmpCoverageInventory.md: #{behavior}: #{status}" if status.start_with?("Missing") || status.start_with?("Not assessed")
  if status.start_with?("Partial") && !has_migration_gate_language?(required)
    errors << "documentation/KmpCoverageInventory.md: #{behavior}: Partial row lacks migration-gate language"
  end
end
ppg_row = coverage_rows.find { |row| row[0] == "PPG parser" && row.size >= 5 }
if ppg_row.nil?
  errors << "documentation/KmpCoverageInventory.md: missing PPG parser row"
else
  errors << "documentation/KmpCoverageInventory.md: PPG parser row must stay Covered after shared compile verification" unless ppg_row[3].start_with?("Covered")
  errors << "documentation/KmpCoverageInventory.md: PPG parser row must keep :shared:jvmTest compile-verification evidence" unless ppg_row[3].include?(":shared:jvmTest")
  errors << "documentation/KmpCoverageInventory.md: PPG parser row must keep compile-verification-gate migration language" unless ppg_row[4].include?("compile-verification-gate")
end
errors << "documentation/KmpCoverageInventory.md: missing Platform-Owned Migration Boundary section" unless coverage_inventory.include?("## Platform-Owned Migration Boundary")
PLATFORM_OWNED_COVERAGE_ROWS.each do |behavior, terms|
  row = coverage_rows.find { |candidate| candidate[0] == behavior && candidate.size >= 5 }
  if row.nil?
    errors << "documentation/KmpCoverageInventory.md: missing platform-owned row #{behavior}"
    next
  end
  combined = "#{row[3]} #{row[4]} #{coverage_inventory}"
  terms.each do |term|
    errors << "documentation/KmpCoverageInventory.md: #{behavior}: missing platform-owned boundary term #{term}" unless combined.include?(term)
  end
end
full_coverage_exit_section = coverage_inventory.split("## Full-Coverage Exit Criteria Before Migration", 2)[1] || ""
FULL_COVERAGE_EXIT_CRITERIA_TERMS.each do |term|
  errors << "documentation/KmpCoverageInventory.md: missing full-coverage exit criterion #{term}" unless full_coverage_exit_section.include?(term)
end

checklist = File.read(File.join(ROOT, "documentation/KmpMigrationChecklist.md"))
completed_items = checklist.scan(CHECKED_CHECKLIST_ITEM).flatten.map { |item| item.delete_suffix(".") }.to_set
completed_section = checklist[COMPLETED_ITEM_EVIDENCE_SECTION] || ""
MIGRATION_STOP_CONDITION_TERMS.each do |term|
  errors << "documentation/KmpMigrationChecklist.md: missing migration stop condition #{term}" unless checklist.include?(term)
end
PER_SLICE_TDD_CHECKLIST_TERMS.each do |term|
  errors << "documentation/KmpMigrationChecklist.md: missing per-slice TDD checklist term #{term}" unless checklist.include?(term)
end
REVIEW_CHECKLIST_TERMS.each do |term|
  errors << "documentation/KmpMigrationChecklist.md: missing review checklist term #{term}" unless checklist.include?(term)
end
SUGGESTED_SLICE_ORDER_TERMS.each do |term|
  errors << "documentation/KmpMigrationChecklist.md: missing suggested slice order term #{term}" unless checklist.include?(term)
end
evidence_rows = completed_section.lines.filter_map { |line| line.match(CHECKLIST_EVIDENCE_ROW) }
evidence_items = evidence_rows.map { |match| match[1].delete_suffix(".") }.reject { |item| item == "Completed checklist item" || item == "---" }.to_set
completed_items.each do |item|
  errors << "documentation/KmpMigrationChecklist.md: completed item lacks evidence row: #{item}" unless evidence_items.include?(item)
end
errors << "documentation/KmpMigrationChecklist.md: local validation evidence must cite scripts/ios_xcode_validation_probe.rb" unless completed_section.include?("scripts/ios_xcode_validation_probe.rb")
evidence_rows.each do |match|
  item = match[1].delete_suffix(".")
  next if item == "Completed checklist item" || item == "---"

  references = match[2].scan(BACKTICK_REFERENCE).flatten.select { |reference| looks_like_artifact_reference?(reference) }
  if references.empty?
    errors << "documentation/KmpMigrationChecklist.md: #{item}: no artifact reference"
  else
    references.each do |reference|
      errors << "documentation/KmpMigrationChecklist.md: #{item}: missing #{reference}" unless File.exist?(resolve_evidence_reference(reference))
    end
  end
end

android_vector_loader_test_path = File.join(ROOT, "sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestDataTest.kt")
android_vector_loader_test = File.file?(android_vector_loader_test_path) ? File.read(android_vector_loader_test_path) : ""
if completed_items.include?("Add vector-loading helpers for Android tests")
  errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestDataTest.kt: missing Android vector loader test" unless File.file?(android_vector_loader_test_path)
  ["GoldenVectorTestData.loadObjects", "GoldenVectorTestData.loadObject", "does-not-exist.json", "FileNotFoundException"].each do |term|
    errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestDataTest.kt: missing #{term}" unless android_vector_loader_test.include?(term)
  end
end

ios_vector_loader_test_path = File.join(ROOT, "sources/iOS/ios-communications/Tests/iOSCommunicationsTests/GoldenVectorTestDataTest.swift")
ios_vector_loader_test = File.file?(ios_vector_loader_test_path) ? File.read(ios_vector_loader_test_path) : ""
ios_vector_loader_path = File.join(ROOT, "sources/iOS/ios-communications/Tests/GoldenVectorTestData.swift")
ios_vector_loader = File.file?(ios_vector_loader_path) ? File.read(ios_vector_loader_path) : ""
xcode_project_for_loader = File.join(ROOT, "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj")
xcode_project_for_loader_text = File.file?(xcode_project_for_loader) ? File.read(xcode_project_for_loader) : ""
if completed_items.include?("Add vector-loading helpers for iOS tests")
  errors << "sources/iOS/ios-communications/Tests/iOSCommunicationsTests/GoldenVectorTestDataTest.swift: missing iOS vector loader test" unless File.file?(ios_vector_loader_test_path)
  ["GoldenVectorTestData.loadObjects", "GoldenVectorTestData.loadObject", "XCTAssertThrowsError", "does-not-exist.json"].each do |term|
    errors << "sources/iOS/ios-communications/Tests/iOSCommunicationsTests/GoldenVectorTestDataTest.swift: missing #{term}" unless ios_vector_loader_test.include?(term)
  end
  ["Bundle(for: GoldenVectorBundleMarker.self)", "url(forResource: \"golden-vectors\"", "subdirectory: \"testdata\""].each do |term|
    errors << "sources/iOS/ios-communications/Tests/GoldenVectorTestData.swift: missing bundled XCTest vector resource lookup term #{term}" unless ios_vector_loader.include?(term)
  end
  errors << "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj: missing GoldenVectorTestDataTest.swift file reference" unless xcode_project_for_loader_text.include?("GoldenVectorTestDataTest.swift")
  errors << "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj: missing GoldenVectorTestDataTest.swift source build phase" unless xcode_project_for_loader_text.include?("GoldenVectorTestDataTest.swift in Sources")
  errors << "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj: missing bundled testdata folder reference for XCTest vector resources" unless xcode_project_for_loader_text.include?("/* testdata */") && xcode_project_for_loader_text.include?("path = ../../../testdata")
  errors << "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj: testdata must be copied into both XCTest bundle resource phases" unless xcode_project_for_loader_text.scan("testdata in Resources").size >= 2
end

android_library_gradle_path = File.join(ROOT, "sources/Android/android-communications/library/build.gradle")
android_library_gradle = File.read(android_library_gradle_path)
gradle_min_sdk = android_library_gradle[ANDROID_MIN_SDK_VERSION, 1]
if gradle_min_sdk.nil?
  errors << "sources/Android/android-communications/library/build.gradle: missing minSdk"
else
  ANDROID_MIN_SDK_DOCS.each do |doc_path|
    documented_values = File.read(File.join(ROOT, doc_path)).scan(ANDROID_MIN_SDK_REFERENCE).flatten.to_set
    if documented_values.empty?
      errors << "#{doc_path}: missing Android minSdk #{gradle_min_sdk}"
    elsif documented_values != [gradle_min_sdk].to_set
      errors << "#{doc_path}: documented minSdk values #{documented_values.to_a.sort.inspect} but Gradle declares #{gradle_min_sdk}"
    end
  end
end

migration_plan = File.read(File.join(ROOT, "documentation/KmpMigrationPlan.md"))
unless android_library_gradle.include?("new ProcessBuilder('git', 'describe', '--tags', '--always')")
  errors << "sources/Android/android-communications/library/build.gradle: version helper must use git describe --tags --always"
end
unless android_library_gradle.include?("def exitValue = process.waitFor()") && android_library_gradle.include?("exitValue == 0")
  errors << "sources/Android/android-communications/library/build.gradle: version helper must handle nonzero git describe exits"
end
errors << "sources/Android/android-communications/library/build.gradle: version helper must extract semver with matcher.find()" unless android_library_gradle.include?("matcher.find()")
errors << "sources/Android/android-communications/library/build.gradle: version helper must fall back to parseable 0.0.0" unless android_library_gradle.include?('def VERSION = "0.0.0"')
errors << "documentation/KmpMigrationChecklist.md: missing completed tagless Gradle readiness item" unless checklist.include?("- [x] Android Gradle configuration works in a tagless checkout or clearly documents the tag requirement.")
errors << "documentation/KmpMigrationPlan.md: missing tagless-safe Android Gradle readiness note" unless migration_plan.include?("Android Gradle version helper must remain tagless-safe")

podspec = File.read(File.join(ROOT, "PolarBleSdk.podspec"))
ios_readme = File.read(File.join(ROOT, "sources/iOS/ios-communications/README.md"))
source_files_path = podspec[PODSPEC_SOURCE_FILES, 1]
resources_declaration = podspec[PODSPEC_RESOURCES, 1]
resources = resources_declaration ? resources_declaration.scan(PODSPEC_RESOURCE_REFERENCE).flatten : []
errors << "PolarBleSdk.podspec: source_files must point to sources/iOS/ios-communications/Sources/**/*.{swift,h}, found #{source_files_path.inspect}" unless source_files_path == "sources/iOS/ios-communications/Sources/**/*.{swift,h}"
ios_source_root = File.join(ROOT, "sources/iOS/ios-communications/Sources")
if !Dir.exist?(ios_source_root)
  errors << "sources/iOS/ios-communications/Sources: missing iOS source root"
elsif Dir[File.join(ios_source_root, "**/*.swift")].empty?
  errors << "sources/iOS/ios-communications/Sources: must contain Swift sources"
end
resources.each do |resource|
  errors << "PolarBleSdk.podspec: resource does not exist: #{resource}" unless File.file?(File.join(ROOT, resource))
end
errors << "PolarBleSdk.podspec: must declare the iOS capability resource" if resources.empty?
if ios_readme.include?("<relative_path_to_cloned_repo>/ios-communications/") || ios_readme.include?("`/ios-communications/`")
  errors << "sources/iOS/ios-communications/README.md: must not reference nonexistent top-level ios-communications path"
end

validation_commands = File.read(File.join(ROOT, "documentation/KmpValidationCommands.md"))
ios_probe_path = File.join(ROOT, "scripts/ios_xcode_validation_probe.rb")
ios_probe = File.file?(ios_probe_path) ? File.read(ios_probe_path) : ""
android_docs_index = File.join(ROOT, "docs/polar-sdk-android/index.html")
ios_docs_index = File.join(ROOT, "docs/polar-sdk-ios/index.html")
errors << "docs/polar-sdk-android/index.html: missing or not recognizable as Dokka output" unless File.file?(android_docs_index) && File.read(android_docs_index).include?("dokka-javadoc-stylesheet.css")
errors << "docs/polar-sdk-ios/index.html: missing or not recognizable as Jazzy output" unless File.file?(ios_docs_index) && File.read(ios_docs_index).include?("css/jazzy.css")
unless android_library_gradle.include?("org.jetbrains.dokka") && android_library_gradle.include?("tasks.dokkaJavadoc.configure")
  errors << "sources/Android/android-communications/library/build.gradle: Android API doc generator must remain visible"
end
errors << "documentation/KmpValidationCommands.md: missing generated API documentation ownership section" unless validation_commands.include?("## Generated API Documentation Ownership")
errors << "documentation/KmpValidationCommands.md: missing generated docs cleanliness command" unless validation_commands.include?("git diff --name-only -- docs/polar-sdk-android docs/polar-sdk-ios")
errors << "documentation/KmpValidationCommands.md: must document broad Android SDK debug unit-test gate" unless validation_commands.include?("./gradlew :library:testSdkDebugUnitTest --no-daemon")
errors << "documentation/KmpValidationCommands.md: must document whole iOS test-tree Swift syntax parse" unless validation_commands.include?("swiftc -parse sources/iOS/ios-communications/Tests/**/*.swift")
errors << "documentation/KmpValidationCommands.md: must document stable iOS Xcode infrastructure probe" unless validation_commands.include?("ruby scripts/ios_xcode_validation_probe.rb")
errors << "scripts/ios_xcode_validation_probe.rb: missing stable iOS Xcode infrastructure probe" unless File.file?(File.join(ROOT, "scripts/ios_xcode_validation_probe.rb"))
errors << "scripts/ios_xcode_validation_probe.rb: must verify expected iOS targets and schemes" unless ios_probe.include?("EXPECTED_TARGETS") && ios_probe.include?("EXPECTED_SCHEMES")
errors << "scripts/ios_xcode_validation_probe.rb: must classify current XCTest infrastructure blockers" unless ios_probe.include?("workspace-not-valid-to-xcodebuild") && ios_probe.include?("pods-absent") && ios_probe.include?("coresimulator-unavailable") && ios_probe.include?("no known local XCTest infrastructure blockers")
errors << "documentation/KmpValidationCommands.md: must document iOS xcodeproj discovery probe" unless validation_commands.include?("xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj")
errors << "documentation/KmpValidationCommands.md: must document current iOS workspace and Pods state" unless validation_commands.include?("workspace discovery succeeds") && validation_commands.include?("sources/iOS/ios-communications/Pods") && validation_commands.include?("is present")
errors << "documentation/KmpValidationCommands.md: must document current iOS probe state" unless validation_commands.include?("no known local XCTest infrastructure blockers")
HARDWARE_SMOKE_VALIDATION_TERMS.each do |term|
  errors << "documentation/KmpValidationCommands.md: missing hardware/device smoke boundary term #{term}" unless validation_commands.include?(term)
end
GRADLE_BATCH_VALIDATION_TERMS.each do |term|
  errors << "documentation/KmpValidationCommands.md: missing Gradle batching boundary term #{term}" unless validation_commands.include?(term)
end
VALIDATION_MINIMUM_TDD_LINK_TERMS.each do |term|
  errors << "documentation/KmpValidationCommands.md: missing TDD minimum validation link term #{term}" unless validation_commands.include?(term)
end
errors << "documentation/KmpMigrationChecklist.md: missing completed generated API documentation ownership item" unless checklist.include?("- [x] Generated API documentation is not edited by hand during migration slices.")
generated_doc_diffs = git_status_short("docs/polar-sdk-android", "docs/polar-sdk-ios")
errors << "generated API documentation must stay clean during migration slices: #{generated_doc_diffs.join(", ")}" unless generated_doc_diffs.empty?

completed_items = completed_items.to_a.to_set
common_test_source_sets = Dir[File.join(ROOT, "**/commonTest")].select { |path| File.directory?(path) }.map { |path| relative(path) }
if common_test_source_sets.empty?
  errors << "documentation/KmpMigrationChecklist.md: KMP common vector-loading helpers cannot be completed before commonTest exists" if completed_items.include?("Add vector-loading helpers for KMP common tests")
  errors << "documentation/KmpValidationCommands.md: must state no shared KMP module exists yet" unless validation_commands.include?("No shared KMP module exists yet")
  errors << "documentation/KmpMigrationChecklist.md: must explain missing shared KMP/commonTest source set" unless checklist.include?("No shared KMP module or `commonTest` source set exists yet")
else
  errors << "documentation/KmpMigrationChecklist.md: commonTest exists but KMP common vector-loading helpers are not completed" unless completed_items.include?("Add vector-loading helpers for KMP common tests")
  unless validation_commands.include?(":shared:jvmTest") && validation_commands.include?("commonTest")
    errors << "documentation/KmpValidationCommands.md: must name executable shared commonTest command"
  end
  KMP_COMMON_VECTOR_HELPER_ARTIFACTS.each do |relative_path|
    errors << "missing KMP common vector helper artifact #{relative_path}" unless File.file?(File.join(ROOT, relative_path))
  end
  common_vector_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt")
  common_vector_test_text = File.file?(common_vector_test) ? File.read(common_vector_test) : ""
  if File.file?(common_vector_test) && !common_vector_test_text.include?("polar-device-uuid-valid")
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt: must load a shared golden vector"
  end
  if File.file?(common_vector_test) && (!common_vector_test_text.include?("does-not-exist.json") || !common_vector_test_text.include?("assertFailsWith"))
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt: must prove missing fixture paths fail fast"
  end
end

common_fake_transport = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContract.kt")
common_fake_transport_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContractCommonTest.kt")
android_fake_transport = File.join(ROOT, "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt")
android_fake_transport_test = File.join(ROOT, "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContractTest.kt")
if completed_items.include?("Add fake BLE transport interfaces for runtime tests before moving runtime code")
  if File.file?(android_fake_transport)
    android_fake_transport_text = File.read(android_fake_transport)
    FAKE_TRANSPORT_REQUIRED_OPERATIONS.each do |operation|
      errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt: missing #{operation} operation" unless android_fake_transport_text.include?("fun #{operation}(")
    end
    FAKE_TRANSPORT_REQUIRED_OUTCOMES.each do |outcome|
      errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt: missing #{outcome} outcome" unless android_fake_transport_text.include?(outcome)
    end
    errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt: write operations must capture payload bytes as hex" unless android_fake_transport_text.include?("payload.toHex()")
    FAKE_TRANSPORT_CLEANUP_REQUIRED_TERMS.each do |term|
      errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt: missing stream cleanup control #{term}" unless android_fake_transport_text.include?(term)
    end
  else
    errors << "missing Android fake transport contract"
  end
  if File.file?(android_fake_transport_test)
    android_fake_transport_test_text = File.read(android_fake_transport_test)
    FAKE_TRANSPORT_TEST_REQUIRED_TERMS.each do |term|
      errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContractTest.kt: missing assertion coverage for #{term}" unless android_fake_transport_test_text.include?(term)
    end
    FAKE_TRANSPORT_CLEANUP_TEST_REQUIRED_TERMS.each do |term|
      errors << "sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContractTest.kt: missing stream cleanup assertion for #{term}" unless android_fake_transport_test_text.include?(term)
    end
  else
    errors << "missing Android fake transport contract test"
  end
end
if File.file?(common_fake_transport)
  common_fake_transport_text = File.read(common_fake_transport)
  FAKE_TRANSPORT_COMMON_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContract.kt: missing #{term}" unless common_fake_transport_text.include?(term)
  end
else
  errors << "missing shared fake transport contract"
end
if File.file?(common_fake_transport_test)
  common_fake_transport_test_text = File.read(common_fake_transport_test)
  FAKE_TRANSPORT_COMMON_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContractCommonTest.kt: missing #{term}" unless common_fake_transport_test_text.include?(term)
  end
else
  errors << "missing shared fake transport contract test"
end
command_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/CommandRuntimePolicyCommonTest.kt")
runtime_orchestration_common = File.join(ROOT, "sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/runtime/PolarRuntimeOrchestration.kt")
if File.file?(command_runtime_common_test)
  command_runtime_common_test_text = File.read(command_runtime_common_test)
  command_runtime_policy_text = command_runtime_common_test_text + (File.file?(runtime_orchestration_common) ? File.read(runtime_orchestration_common) : "")
  FAKE_TRANSPORT_COMMON_COMMAND_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/CommandRuntimePolicyCommonTest.kt: missing common command runtime assertion for #{term}" unless command_runtime_policy_text.include?(term)
  end
else
  errors << "missing shared command runtime policy test"
end
stored_data_cleanup_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StoredDataCleanupRuntimePolicyCommonTest.kt")
if File.file?(stored_data_cleanup_runtime_common_test)
  stored_data_cleanup_runtime_common_test_text = File.read(stored_data_cleanup_runtime_common_test)
  FAKE_TRANSPORT_COMMON_STORED_DATA_CLEANUP_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StoredDataCleanupRuntimePolicyCommonTest.kt: missing common stored-data cleanup runtime assertion for #{term}" unless stored_data_cleanup_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared stored-data cleanup runtime policy test"
end
disk_time_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskTimeRuntimePolicyCommonTest.kt")
if File.file?(disk_time_runtime_common_test)
  disk_time_runtime_common_test_text = File.read(disk_time_runtime_common_test)
  disk_time_runtime_policy_text = disk_time_runtime_common_test_text + (File.file?(runtime_orchestration_common) ? File.read(runtime_orchestration_common) : "")
  FAKE_TRANSPORT_COMMON_DISK_TIME_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskTimeRuntimePolicyCommonTest.kt: missing common disk/time runtime assertion for #{term}" unless disk_time_runtime_policy_text.include?(term)
  end
else
  errors << "missing shared disk/time runtime policy test"
end
user_device_settings_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/UserDeviceSettingsRuntimePolicyCommonTest.kt")
if File.file?(user_device_settings_runtime_common_test)
  user_device_settings_runtime_common_test_text = File.read(user_device_settings_runtime_common_test)
  user_device_settings_runtime_policy_text = user_device_settings_runtime_common_test_text + (File.file?(runtime_orchestration_common) ? File.read(runtime_orchestration_common) : "")
  FAKE_TRANSPORT_COMMON_USER_DEVICE_SETTINGS_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/UserDeviceSettingsRuntimePolicyCommonTest.kt: missing common user-device-settings runtime assertion for #{term}" unless user_device_settings_runtime_policy_text.include?(term)
  end
else
  errors << "missing shared user-device-settings runtime policy test"
end
rest_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestRequestTransportPolicyCommonTest.kt")
if File.file?(rest_runtime_common_test)
  rest_runtime_common_test_text = File.read(rest_runtime_common_test)
  FAKE_TRANSPORT_COMMON_REST_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestRequestTransportPolicyCommonTest.kt: missing common REST runtime assertion for #{term}" unless rest_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared REST request transport policy test"
end
file_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileRuntimeErrorPolicyCommonTest.kt")
if File.file?(file_runtime_common_test)
  file_runtime_common_test_text = File.read(file_runtime_common_test)
  FAKE_TRANSPORT_COMMON_FILE_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileRuntimeErrorPolicyCommonTest.kt: missing common file runtime assertion for #{term}" unless file_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared file runtime policy test"
end
file_facade_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileFacadeRuntimePolicyCommonTest.kt")
if File.file?(file_facade_runtime_common_test)
  file_facade_runtime_common_test_text = File.read(file_facade_runtime_common_test)
  file_facade_runtime_policy_text = file_facade_runtime_common_test_text + (File.file?(runtime_orchestration_common) ? File.read(runtime_orchestration_common) : "")
  FAKE_TRANSPORT_COMMON_FILE_FACADE_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileFacadeRuntimePolicyCommonTest.kt: missing common file facade runtime assertion for #{term}" unless file_facade_runtime_policy_text.include?(term)
  end
else
  errors << "missing shared file facade runtime policy test"
end
rest_facade_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestFacadeRuntimePolicyCommonTest.kt")
if File.file?(rest_facade_runtime_common_test)
  rest_facade_runtime_common_test_text = File.read(rest_facade_runtime_common_test)
  rest_facade_runtime_policy_text = rest_facade_runtime_common_test_text + (File.file?(runtime_orchestration_common) ? File.read(runtime_orchestration_common) : "")
  FAKE_TRANSPORT_COMMON_REST_FACADE_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestFacadeRuntimePolicyCommonTest.kt: missing common REST facade runtime assertion for #{term}" unless rest_facade_runtime_policy_text.include?(term)
  end
else
  errors << "missing shared REST facade runtime policy test"
end
rest_service_mapping_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestServiceMappingCommonPolicyTest.kt")
if File.file?(rest_service_mapping_common_test)
  rest_service_mapping_common_test_text = File.read(rest_service_mapping_common_test)
  FAKE_TRANSPORT_COMMON_REST_SERVICE_MAPPING_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestServiceMappingCommonPolicyTest.kt: missing common REST service mapping assertion for #{term}" unless rest_service_mapping_common_test_text.include?(term)
  end
else
  errors << "missing shared REST service mapping policy test"
end
rest_event_compression_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestEventCompressionPolicyCommonTest.kt")
if File.file?(rest_event_compression_common_test)
  rest_event_compression_common_test_text = File.read(rest_event_compression_common_test)
  FAKE_TRANSPORT_COMMON_REST_EVENT_COMPRESSION_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestEventCompressionPolicyCommonTest.kt: missing common REST event compression assertion for #{term}" unless rest_event_compression_common_test_text.include?(term)
  end
else
  errors << "missing shared REST event compression policy test"
end
gnss_location_ownership_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GnssLocationOwnershipCommonPolicyTest.kt")
if File.file?(gnss_location_ownership_common_test)
  gnss_location_ownership_common_test_text = File.read(gnss_location_ownership_common_test)
  GNSS_LOCATION_OWNERSHIP_COMMON_POLICY_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GnssLocationOwnershipCommonPolicyTest.kt: missing common GNSS ownership assertion for #{term}" unless gnss_location_ownership_common_test_text.include?(term)
  end
else
  errors << "missing shared GNSS ownership policy test"
end
backup_utility_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/BackupUtilityCommonPolicyTest.kt")
if File.file?(backup_utility_common_test)
  backup_utility_common_test_text = File.read(backup_utility_common_test)
  FAKE_TRANSPORT_COMMON_BACKUP_UTILITY_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/BackupUtilityCommonPolicyTest.kt: missing common backup utility assertion for #{term}" unless backup_utility_common_test_text.include?(term)
  end
else
  errors << "missing shared backup utility policy test"
end
offline_trigger_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineTriggerRuntimePolicyCommonTest.kt")
if File.file?(offline_trigger_runtime_common_test)
  offline_trigger_runtime_common_test_text = File.read(offline_trigger_runtime_common_test)
  FAKE_TRANSPORT_COMMON_OFFLINE_TRIGGER_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineTriggerRuntimePolicyCommonTest.kt: missing common offline trigger runtime assertion for #{term}" unless offline_trigger_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared offline-trigger runtime policy test"
end
firmware_utility_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareUpdateUtilityCommonPolicyTest.kt")
if File.file?(firmware_utility_common_test)
  firmware_utility_common_test_text = File.read(firmware_utility_common_test)
  FAKE_TRANSPORT_COMMON_FIRMWARE_UTILITY_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareUpdateUtilityCommonPolicyTest.kt: missing common firmware utility assertion for #{term}" unless firmware_utility_common_test_text.include?(term)
  end
else
  errors << "missing shared firmware utility policy test"
end
firmware_workflow_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareWorkflowRuntimePolicyCommonTest.kt")
if File.file?(firmware_workflow_common_test)
  firmware_workflow_common_test_text = File.read(firmware_workflow_common_test)
  FAKE_TRANSPORT_COMMON_FIRMWARE_WORKFLOW_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareWorkflowRuntimePolicyCommonTest.kt: missing common firmware workflow assertion for #{term}" unless firmware_workflow_common_test_text.include?(term)
  end
else
  errors << "missing shared firmware workflow runtime policy test"
end
psftp_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt")
if File.file?(psftp_runtime_common_test)
  psftp_runtime_common_test_text = File.read(psftp_runtime_common_test)
  FAKE_TRANSPORT_COMMON_PSFTP_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt: missing common PSFTP runtime assertion for #{term}" unless psftp_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared PSFTP runtime policy test"
end
psftp_byte_codec_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpByteCodecCommonPolicyTest.kt")
if File.file?(psftp_byte_codec_common_test)
  psftp_byte_codec_common_test_text = File.read(psftp_byte_codec_common_test)
  FAKE_TRANSPORT_COMMON_PSFTP_BYTE_CODEC_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpByteCodecCommonPolicyTest.kt: missing common PSFTP byte codec assertion for #{term}" unless psftp_byte_codec_common_test_text.include?(term)
  end
else
  errors << "missing shared PSFTP byte codec policy test"
end
stream_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StreamRuntimePolicyCommonTest.kt")
if File.file?(stream_runtime_common_test)
  stream_runtime_common_test_text = File.read(stream_runtime_common_test)
  FAKE_TRANSPORT_COMMON_STREAM_RUNTIME_TEST_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StreamRuntimePolicyCommonTest.kt: missing common stream runtime assertion for #{term}" unless stream_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared stream runtime policy test"
end
d2h_stream_runtime_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hStreamRuntimePolicyCommonTest.kt")
if File.file?(d2h_stream_runtime_common_test)
  d2h_stream_runtime_common_test_text = File.read(d2h_stream_runtime_common_test)
  D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS.each do |term|
    errors << "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hStreamRuntimePolicyCommonTest.kt: missing D2H stream runtime assertion for #{term}" unless d2h_stream_runtime_common_test_text.include?(term)
  end
else
  errors << "missing shared D2H stream runtime policy test"
end
fake_transport_plan_for_contract = File.read(File.join(ROOT, "documentation/KmpFakeTransportTestPlan.md"))
unless fake_transport_plan_for_contract.include?("service-readiness gate") && fake_transport_plan_for_contract.include?("virtual clock")
  errors << "documentation/KmpFakeTransportTestPlan.md: missing shared service-readiness and virtual-clock contract note"
end

android_settings = File.read(File.join(ROOT, "sources/Android/android-communications/settings.gradle"))
shared_build_path = File.join(ROOT, "sources/Android/android-communications/shared/build.gradle")
shared_build = File.read(shared_build_path)
shared_marker = File.join(ROOT, "sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt")
device_id_common_main = File.join(ROOT, "sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/device/PolarDeviceId.kt")
device_id_common_test = File.join(ROOT, "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt")
android_device_id_utility = File.join(ROOT, "sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/polar/BlePolarDeviceIdUtility.kt")
android_device_uuid = File.join(ROOT, "sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/model/PolarDeviceUuid.kt")
ios_shared_bridge = File.join(ROOT, "sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/ios/PolarIosSharedBridge.kt")
ios_device_id_utility = File.join(ROOT, "sources/iOS/ios-communications/Sources/iOSCommunications/ble/api/model/polar/BlePolarDeviceIdUtility.swift")
ios_device_uuid = File.join(ROOT, "sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/model/PolarDeviceUuid.swift")
ios_time_utils = File.join(ROOT, "sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/utils/PolarTimeUtils.swift")
ios_kmp_script = File.join(ROOT, "sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh")
device_id_slice_migrated = File.file?(device_id_common_main) &&
                           File.read(device_id_common_main).include?("object PolarDeviceId") &&
                           File.file?(device_id_common_test) &&
                           File.read(device_id_common_test).include?("PolarDeviceId.uuidFromDeviceId") &&
                           File.file?(android_device_id_utility) &&
                           File.read(android_device_id_utility).include?("PolarDeviceId.assembleFull") &&
                           File.file?(android_device_uuid) &&
                           File.read(android_device_uuid).include?("PolarDeviceId.uuidFromDeviceId")
ios_shared_consumption_migrated = File.file?(ios_shared_bridge) &&
                                  File.read(ios_shared_bridge).include?("object PolarIosSharedBridge") &&
                                  File.read(ios_shared_bridge).include?("PolarDeviceId.uuidFromDeviceId") &&
                                  File.read(ios_shared_bridge).include?("PolarTimeUtils.nanosToMillis") &&
                                  File.file?(ios_device_id_utility) &&
                                  File.read(ios_device_id_utility).include?("PolarIosSharedBridge.shared.isValidDeviceId") &&
                                  File.file?(ios_device_uuid) &&
                                  File.read(ios_device_uuid).include?("PolarIosSharedBridge.shared.uuidFromDeviceId") &&
                                  File.file?(ios_time_utils) &&
                                  File.read(ios_time_utils).include?("PolarIosSharedBridge.shared.durationToMillis") &&
                                  File.file?(ios_kmp_script)
errors << "sources/Android/android-communications/settings.gradle: must include :shared" unless android_settings.include?("include ':shared'")
errors << "sources/Android/android-communications/shared/build.gradle: must apply Kotlin Multiplatform" unless shared_build.include?("org.jetbrains.kotlin.multiplatform")
errors << "sources/Android/android-communications/shared/build.gradle: must keep JVM target" unless shared_build.include?("jvm()")
unless File.file?(shared_marker) && File.read(shared_marker).include?("object SharedModule")
  errors << "sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt: shared commonMain must retain the module marker"
end
[
  "Add a minimal shared KMP module without moving behavior",
  "Add `commonMain`, `commonTest`, and platform-specific test source sets only as needed",
  "Add a trivial common test and run it in local validation"
].each do |item|
  errors << "documentation/KmpMigrationChecklist.md: missing completed item #{item}" unless completed_items.include?(item)
end
errors << "documentation/KmpValidationCommands.md: must document :shared:jvmTest" unless validation_commands.include?(":shared:jvmTest")

errors << "sources/Android/android-communications/shared/build.gradle: must apply com.android.kotlin.multiplatform.library" unless shared_build.include?("apply plugin: 'com.android.kotlin.multiplatform.library'")
errors << "sources/Android/android-communications/shared/build.gradle: must declare AGP 9 Android KMP target" unless shared_build.include?("android {")
unless shared_build.include?("iosX64()") && shared_build.include?("iosArm64()") && shared_build.include?("iosSimulatorArm64()")
  errors << "sources/Android/android-communications/shared/build.gradle: must declare iosX64, iosArm64, and iosSimulatorArm64"
end
unless shared_build.include?("namespace = 'com.polar.shared'") && shared_build.match?(/minSdk(?:Version)?\s*(?:=)?\s*26/)
  errors << "sources/Android/android-communications/shared/build.gradle: must declare Android namespace and minSdk 26"
end
errors << "sources/Android/android-communications/shared/src/androidMain/AndroidManifest.xml: missing minimal Android manifest" unless File.file?(File.join(ROOT, "sources/Android/android-communications/shared/src/androidMain/AndroidManifest.xml"))
errors << "documentation/KmpMigrationChecklist.md: missing completed Android and Apple target configuration item" unless completed_items.include?("Configure Android and Apple targets")
errors << "documentation/KmpValidationCommands.md: must document shared target shape" unless validation_commands.include?("JVM, Android, and Apple targets")
unless validation_commands.include?(":shared:compileAndroidMain") && validation_commands.include?(":shared:compileAndroidHostTest") && validation_commands.include?(":shared:compileKotlinIosX64")
  errors << "documentation/KmpValidationCommands.md: must document shared Android and iOS target compile gates"
end
if android_library_gradle.include?("project(':shared')") && !device_id_slice_migrated
  errors << "sources/Android/android-communications/library/build.gradle: Android library must not consume :shared without concrete migrated behavior evidence"
end
package_swift = File.join(ROOT, "sources/iOS/ios-communications/Package.swift")
if File.file?(package_swift) && File.read(package_swift).include?("shared")
  errors << "sources/iOS/ios-communications/Package.swift: iOS package must not consume shared before behavior migration"
end

consumption_doc_path = File.join(ROOT, "documentation/KmpSharedArtifactConsumption.md")
if !File.file?(consumption_doc_path)
  errors << "documentation/KmpSharedArtifactConsumption.md: missing shared artifact consumption contract"
else
  consumption_doc = File.read(consumption_doc_path)
  SHARED_CONSUMPTION_REQUIRED_TERMS.each do |term|
    errors << "documentation/KmpSharedArtifactConsumption.md: missing #{term}" unless consumption_doc.include?(term)
  end
end
unless shared_build.include?("baseName = 'PolarBleSdkShared'") && shared_build.include?("isStatic = true")
  errors << "sources/Android/android-communications/shared/build.gradle: must define static PolarBleSdkShared framework artifact"
end
unless validation_commands.include?(":shared:bundleAndroidMainAar") && validation_commands.include?(":shared:linkDebugFrameworkIosX64")
  errors << "documentation/KmpValidationCommands.md: must document shared artifact smoke gates"
end
errors << "documentation/KmpMigrationChecklist.md: missing completed shared artifact consumption documentation item" unless completed_items.include?("Document how shared artifacts are consumed by Android and iOS modules")
if android_library_gradle.include?("implementation project(':shared')") && !device_id_slice_migrated
  errors << "sources/Android/android-communications/library/build.gradle: Android production consumption must name a migrated shared behavior slice"
end
xcode_project = File.join(ROOT, "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj")
if File.file?(xcode_project) && File.read(xcode_project).include?("PolarBleSdkShared.framework") && !ios_shared_consumption_migrated
  errors << "sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj: iOS production consumption must name a migrated shared behavior slice"
end
SHARED_COMMON_PRODUCTION_CODEC_DEPENDENCY_TERMS.each do |term|
  errors << "sources/Android/android-communications/shared/build.gradle: declares #{term} before production common codec ownership evidence is added" if shared_build.include?(term)
end
BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS.each do |artifact, required_terms|
  artifact_path = case artifact
                  when "KmpFullCoverageTddBacklog.md"
                    File.join(ROOT, "documentation/KmpFullCoverageTddBacklog.md")
                  when "KmpCoverageInventory.md"
                    File.join(ROOT, "documentation/KmpCoverageInventory.md")
                  when "KmpPreMigrationRemainingWork.md"
                    File.join(ROOT, "documentation/KmpPreMigrationRemainingWork.md")
                  when "payload-read-policy.json"
                    File.join(ROOT, "testdata/golden-vectors/sdk/training-session/payload-read-policy.json")
                  when "payload-parser-policy.json"
                    File.join(ROOT, "testdata/golden-vectors/sdk/training-session/payload-parser-policy.json")
                  when "training-session-readiness.json"
                    File.join(ROOT, "testdata/golden-vectors/sdk/training-session/training-session-readiness.json")
                  when "secret-readiness.json"
                    File.join(ROOT, "testdata/golden-vectors/protocol/pmd/secret-readiness.json")
                  when "rest-event-compression-readiness.json"
                    File.join(ROOT, "testdata/golden-vectors/sdk/rest-service/rest-event-compression-readiness.json")
                  when "watch-face-readiness.json"
                    File.join(ROOT, "testdata/golden-vectors/sdk/watch-face/watch-face-readiness.json")
                  end
  artifact_text = artifact_path && File.file?(artifact_path) ? File.read(artifact_path) : ""
  required_terms.each do |term|
    errors << "#{artifact}: missing byte-level common dependency deferral term #{term}" unless artifact_text.include?(term)
  end
end

fake_transport_plan = File.read(File.join(ROOT, "documentation/KmpFakeTransportTestPlan.md"))
known_file_names = Dir[File.join(ROOT, "**/*")].select { |path| File.file?(path) }.map { |path| File.basename(path) }.to_set
matrix_rows = table_rows(section_between(fake_transport_plan, "## Required Runtime Test Matrix", "## Runtime Matrix Coverage Ledger")).select { |row| row.size >= 5 && row[0] != "Behavior" }
ledger_rows = table_rows(section_between(fake_transport_plan, "## Runtime Matrix Coverage Ledger", "## Public Facade Operation Coverage Ledger")).select { |row| row.size >= 4 && row[0] != "Behavior" }
matrix_behaviors = matrix_rows.map(&:first)
ledger_by_behavior = ledger_rows.to_h { |row| [row[0], row] }
matrix_behaviors.reject { |behavior| ledger_by_behavior.key?(behavior) }.each do |behavior|
  errors << "documentation/KmpFakeTransportTestPlan.md: missing runtime ledger row for #{behavior}"
end
ledger_by_behavior.keys.reject { |behavior| matrix_behaviors.include?(behavior) }.each do |behavior|
  errors << "documentation/KmpFakeTransportTestPlan.md: extra runtime ledger row for #{behavior}"
end
ledger_rows.each do |row|
  behavior = row[0]
  status = row[1]
  evidence = row[2]
  gate = row[3]
  errors << "documentation/KmpFakeTransportTestPlan.md: #{behavior}: missing status" if status.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{behavior}: missing evidence" if evidence.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{behavior}: migration gate lacks concrete before/add/keep language" unless has_migration_gate_language?(gate)
  "#{evidence} #{gate}".scan(BACKTICK_REFERENCE).flatten.select { |reference| reference.end_with?(".json", ".kt", ".swift", ".md") }.each do |reference|
    errors << "documentation/KmpFakeTransportTestPlan.md: #{behavior}: missing artifact reference #{reference}" unless known_file_names.include?(File.basename(reference))
  end
end
PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS.each do |behavior, required_terms|
  row = ledger_by_behavior[behavior]
  if row.nil?
    errors << "documentation/KmpFakeTransportTestPlan.md: missing PSFTP timeout runtime ledger row for #{behavior}"
    next
  end

  row_text = row.join(" | ")
  required_terms.each do |term|
    errors << "documentation/KmpFakeTransportTestPlan.md: #{behavior}: missing PSFTP timeout ledger term #{term}" unless row_text.include?(term)
  end
end
fake_transport_gate_section = section_between(fake_transport_plan, "## Pre-Migration Gates", "## PSFTP Runtime Harness Requirements")
FAKE_TRANSPORT_PRE_MIGRATION_GATE_REQUIRED_TERMS.each do |term|
  errors << "documentation/KmpFakeTransportTestPlan.md: missing pre-migration gate term #{term}" unless fake_transport_gate_section.include?(term)
end
FAKE_TRANSPORT_HARNESS_DESCRIPTION_REQUIRED_TERMS.each do |term|
  errors << "documentation/KmpFakeTransportTestPlan.md: missing fake-transport harness term #{term}" unless fake_transport_gate_section.include?(term)
end

facade_rows = table_rows(section_between(fake_transport_plan, "## Public Facade Operation Coverage Ledger", "## Pre-Migration Gates")).select { |row| row.size >= 6 && row[0] != "Operation family" }
facade_by_family = facade_rows.to_h { |row| [row[0], row] }
PUBLIC_FACADE_OPERATION_FAMILIES.reject { |family| facade_by_family.key?(family) }.each do |family|
  errors << "documentation/KmpFakeTransportTestPlan.md: missing public facade ledger row for #{family}"
end
facade_rows.each do |row|
  family = row[0]
  status = row[1]
  android_evidence = row[2]
  ios_evidence = row[3]
  shared_evidence = row[4]
  gate = row[5]
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing status" if status.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing Android evidence" if android_evidence.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing iOS evidence" if ios_evidence.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing shared/runtime evidence" if shared_evidence.strip.empty?
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: migration gate lacks concrete before/add/keep language" unless has_migration_gate_language?(gate)
  "#{android_evidence} #{ios_evidence} #{shared_evidence} #{gate}".scan(BACKTICK_REFERENCE).flatten.select { |reference| looks_like_artifact_reference?(reference) }.each do |reference|
    errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing artifact reference #{reference}" unless known_file_names.include?(File.basename(reference))
  end
end
FACADE_GATE_OPEN_REQUIRED_TERMS.each do |family, required_terms|
  row = facade_by_family[family]
  if row.nil?
    errors << "documentation/KmpFakeTransportTestPlan.md: missing facade-gated public operation row for #{family}"
    next
  end

  row_text = row.join(" | ")
  required_terms.each do |term|
    errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing facade gate term #{term}" unless row_text.include?(term)
  end
  errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: must stay facade gate open until concrete platform/shared compatibility evidence exists" unless row[1].include?("facade gate open")
end
RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS.each do |family, required_terms|
  row = facade_by_family[family]
  if row.nil?
    errors << "documentation/KmpFakeTransportTestPlan.md: missing runtime-pinned public operation row for #{family}"
    next
  end

  row_text = row.join(" | ")
  required_terms.each do |term|
    errors << "documentation/KmpFakeTransportTestPlan.md: #{family}: missing runtime-pinned facade term #{term}" unless row_text.include?(term)
  end
end

Dir[File.join(COMMON_TEST_ROOT, "**/*.kt")].sort.each do |path|
  relative_path = relative(path)
  File.readlines(path).each_with_index do |line, index|
    next unless line.match?(COMMON_TEST_PORTABILITY_FORBIDDEN)
    next if COMMON_TEST_PORTABILITY_ALLOWED_LINES.any? { |allowed_path, allowed_text| relative_path == allowed_path && line.include?(allowed_text) }

    errors << "#{relative_path}:#{index + 1}: shared commonTest portability violation: #{line.strip}"
  end
end
common_main_root = File.join(ROOT, "sources/Android/android-communications/shared/src/commonMain/kotlin")
Dir[File.join(common_main_root, "**/*.kt")].sort.each do |path|
  relative_path = relative(path)
  File.readlines(path).each_with_index do |line, index|
    next unless line.match?(COMMON_MAIN_PLATFORM_FORBIDDEN)

    errors << "#{relative_path}:#{index + 1}: shared commonMain platform-only API violation: #{line.strip}"
  end
end
COMMON_MAIN_PORTABILITY_PLAN_TERMS.each do |term|
  errors << "documentation/KmpMigrationPlan.md: missing commonMain portability term #{term}" unless migration_plan.include?(term)
end

fail_with(errors)
puts "kmp_non_gradle_checks OK: #{parsed_vectors.size} vectors, consumerTests schema, shared references, non-orphan vector paths, policy-required terms and doc strings, docs mirrors, environment guardrails, and shared-module gates passed"
