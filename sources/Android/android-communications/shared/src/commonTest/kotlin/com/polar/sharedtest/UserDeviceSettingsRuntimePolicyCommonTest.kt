package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class UserDeviceSettingsRuntimePolicyCommonTest {
    @Test
    fun userDeviceSettingsRuntimePolicyVectorDefinesExecutableCommonReadWritePlanning() {
        val vector = loadGoldenVectorText("sdk/user-device-settings-runtime/settings-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val operationList = input.objectArray("operations")
        val operations = operationList.map { operation ->
            UserDeviceSettingsOperation(
                id = operation.stringValue("id"),
                kind = operation.stringValue("kind"),
                path = operation.stringValue("path"),
                payloadFields = operation.optionalStringArrayValue("payloadFields") ?: emptyList()
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }
        val runtime = FakeUserDeviceSettingsRuntime()

        assertEquals("user-device-settings-runtime-policy", vector.stringValue("id"))
        assertEquals("user_device_settings_runtime_policy", vector.stringValue("case"))
        assertEquals("/U/0/S/UDEVSET.BPB", input.stringValue("settingsPath"))
        assertEquals(requiredUserDeviceSettingsRuntimeOperationIds, operations.map { it.id })
        assertEquals(requiredUserDeviceSettingsRuntimeOperationIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-user-device-settings-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-psftp-read-write-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals(userDeviceSettingsRuntimePolicyCommonDecision, vector.stringValue("commonDecision"))
        assertUserDeviceSettingsPolicyFields(operationList.associateBy { it.stringValue("id") })
        assertReadFailureNoWriteBehavior(expectedCases)

        operations.forEach { operation ->
            val outcome = runtime.run(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
        }
    }

    @Test
    fun userDeviceSettingsRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/user-device-settings-runtime/settings-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("user-device-settings-runtime-readiness", manifest.stringValue("id"))
        assertEquals("userDeviceSettingsRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/user-device-settings-runtime/settings-runtime-policy.json", policyVectorPath)
        assertEquals(requiredUserDeviceSettingsRuntimeFamilies, requiredFamilies)
        assertEquals(requiredUserDeviceSettingsRuntimeFamilies, coveredFamilies)
        assertEquals("user-device-settings-runtime-policy", policy.stringValue("id"))
        assertEquals("user_device_settings_runtime_policy", policy.stringValue("case"))
        assertEquals("/U/0/S/UDEVSET.BPB", policy.objectValue("input").stringValue("settingsPath"))
        assertEquals(requiredUserDeviceSettingsRuntimeOperationIds, policy.objectValue("input").objectArray("operations").map { it.stringValue("id") })
        assertEquals(requiredUserDeviceSettingsRuntimeOperationIds, policy.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").map { it.stringValue("id") })
        assertUserDeviceSettingsPolicyFields(policy.objectValue("input").objectArray("operations").associateBy { it.stringValue("id") })
        assertReadFailureNoWriteBehavior(policy.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") })
        assertEquals(userDeviceSettingsRuntimeReadinessCommonDecision, expected.stringValue("commonDecision"))
        val commonRuntimePrototype = expected.objectValue("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.stringValue("reason"))
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.UserDeviceSettingsRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredUserDeviceSettingsRuntimeOperationIds = listOf(
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
        "set-daylight-saving-time"
    )

    private val requiredUserDeviceSettingsRuntimeFamilies = listOf(
        "settings-path-gate",
        "settings-read-success",
        "settings-read-failure-no-write",
        "telemetry-read-then-write",
        "telemetry-write-failure-after-payload",
        "device-location-read-then-write",
        "device-location-write-failure-after-payload",
        "usb-connection-mode-read-then-write",
        "usb-connection-mode-write-failure-after-payload",
        "automatic-training-detection-read-then-write",
        "automatic-training-detection-write-failure-after-payload",
        "automatic-ohr-measurement-read-then-write",
        "automatic-ohr-measurement-write-failure-after-payload",
        "daylight-saving-payload-shape",
        "protobuf-field-preservation-gate",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val userDeviceSettingsRuntimeReadinessCommonDecision = "User-device-settings runtime migration may proceed only after settings-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, protobuf field preservation and public facade error mapping are pinned, read-failure no-write and write-failure-after-payload behavior for telemetry, location, USB, automatic-training-detection, and automatic-OHR writes remain covered, daylight-saving payload shape is preserved, and the shared tests are compile-verified."

    private val userDeviceSettingsRuntimePolicyCommonDecision = "Promote user-device-settings runtime only after read/write sequencing, no-write read failures, write-failure payload preservation, and platform protobuf serializer differences remain covered by executable facade and model vectors."

    private val readFailureNoWriteBehavior = "read-failure no-write behavior"

    private fun assertUserDeviceSettingsPolicyFields(operationsById: Map<String, String>) {
        assertEquals("read", operationsById.getValue("get-user-device-settings").stringValue("kind"))
        assertEquals("readFailure", operationsById.getValue("get-user-device-settings-read-failure").stringValue("kind"))
        assertEquals(listOf("telemetryEnabled=true", "preserve:deviceLocation=WRIST_LEFT"), operationsById.getValue("set-telemetry-enabled").stringArrayValue("payloadFields"))
        assertEquals("readFailure", operationsById.getValue("set-telemetry-read-failure").stringValue("kind"))
        assertEquals(listOf("telemetryEnabled=true"), operationsById.getValue("set-telemetry-write-failure").stringArrayValue("payloadFields"))
        assertEquals(listOf("deviceLocation=WRIST_RIGHT", "preserve:telemetryEnabled=true"), operationsById.getValue("set-user-device-location").stringArrayValue("payloadFields"))
        assertEquals(listOf("deviceLocation=WRIST_LEFT", "preserve:telemetryEnabled=true"), operationsById.getValue("set-user-device-location-write-failure").stringArrayValue("payloadFields"))
        assertEquals(listOf("usbConnectionMode=ON", "preserve:deviceLocation=WRIST_LEFT", "preserve:telemetryEnabled=true"), operationsById.getValue("set-usb-connection-mode").stringArrayValue("payloadFields"))
        assertEquals(listOf("automaticTrainingDetectionMode=ON", "automaticTrainingDetectionSensitivity=77", "minimumTrainingDurationSeconds=300", "preserve:telemetryEnabled=true"), operationsById.getValue("set-automatic-training-detection").stringArrayValue("payloadFields"))
        assertEquals(listOf("automaticOhrMeasurement=ALWAYS_ON", "preserve:automaticTrainingDetectionSettings"), operationsById.getValue("set-automatic-ohr-measurement").stringArrayValue("payloadFields"))
        assertEquals(listOf("daylightSaving.nextDaylightSavingTime=present", "daylightSaving.offset=nonzero"), operationsById.getValue("set-daylight-saving-time").stringArrayValue("payloadFields"))
    }

    private fun assertReadFailureNoWriteBehavior(expectedCasesById: Map<String, String>) {
        assertEquals("transport-error", expectedCasesById.getValue("get-user-device-settings-read-failure").stringValue("terminal"), readFailureNoWriteBehavior)
        assertEquals(listOf("read:/U/0/S/UDEVSET.BPB"), expectedCasesById.getValue("get-user-device-settings-read-failure").stringArrayValue("commands"), readFailureNoWriteBehavior)
        assertEquals("transport-error", expectedCasesById.getValue("set-telemetry-read-failure").stringValue("terminal"), readFailureNoWriteBehavior)
        assertEquals(listOf("read:/U/0/S/UDEVSET.BPB"), expectedCasesById.getValue("set-telemetry-read-failure").stringArrayValue("commands"), readFailureNoWriteBehavior)
    }

    private class FakeUserDeviceSettingsRuntime {
        fun run(operation: UserDeviceSettingsOperation): UserDeviceSettingsOutcome {
            return when (operation.kind) {
                "read" -> UserDeviceSettingsOutcome(operation.readCommands(), "success")
                "readFailure" -> UserDeviceSettingsOutcome(operation.readCommands(), "transport-error")
                "readThenWrite" -> UserDeviceSettingsOutcome(operation.readWriteCommands(), "success")
                "readThenWriteFailure" -> UserDeviceSettingsOutcome(operation.readWriteCommands(), "transport-error-after-payload")
                else -> error("Unsupported user-device-settings operation ${operation.kind}")
            }
        }

        private fun UserDeviceSettingsOperation.readCommands(): List<String> {
            return listOf("read:$path")
        }

        private fun UserDeviceSettingsOperation.readWriteCommands(): List<String> {
            return listOf("read:$path", "write:$path") + payloadFields.map { field -> "field:$field" }
        }
    }

    private data class UserDeviceSettingsOperation(
        val id: String,
        val kind: String,
        val path: String,
        val payloadFields: List<String>
    )

    private data class UserDeviceSettingsOutcome(
        val commands: List<String>,
        val terminal: String
    )
}
