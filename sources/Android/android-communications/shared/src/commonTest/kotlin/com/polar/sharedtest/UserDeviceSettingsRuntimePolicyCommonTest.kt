package com.polar.sharedtest

import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserDeviceSettingsRuntimePolicyCommonTest {
    @Test
    fun userDeviceSettingsRuntimePolicyVectorDefinesExecutableCommonReadWritePlanning() {
        val vector = loadGoldenVectorText("sdk/user-device-settings-runtime/settings-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val operationList = input.objectArray("operations")
        val operations = operationList.map { operation ->
            PolarUserDeviceSettingsOperation(
                id = operation.stringValue("id"),
                kind = operation.stringValue("kind"),
                path = operation.stringValue("path"),
                payloadFields = operation.optionalStringArrayValue("payloadFields") ?: emptyList()
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }

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
        assertEquals("transport-error-after-payload", expectedCases.getValue("set-telemetry-write-failure").stringValue("terminal"))

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planUserDeviceSettings(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
        }
    }

    @Test
    fun userDeviceSettingsRuntimePlannerSupportsDirectWriteFacadePlanning() {
        val outcome = PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = "set-user-device-settings",
                kind = "write",
                path = "/U/0/S/UDEVSET.BPB",
                payloadFields = listOf("protobufPayload=platform-built")
            )
        )

        assertEquals(listOf("write:/U/0/S/UDEVSET.BPB", "field:protobufPayload=platform-built"), outcome.commands)
        assertEquals("success", outcome.terminal)
        assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimeOrchestration.userDeviceSettingsPath("POLAR_FILE_SYSTEM_V2"))
        assertEquals("/UDEVSET.BPB", PolarRuntimeOrchestration.userDeviceSettingsPath("H10_FILE_SYSTEM"))
        assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimeOrchestration.userDeviceSettingsPath("UNKNOWN_FILE_SYSTEM", unknownSettingsPath = "/U/0/S/UDEVSET.BPB"))
        assertEquals("/UDEVSET.BPB", PolarRuntimeOrchestration.userDeviceSettingsPath("unknownFileSystem", unknownSettingsPath = "/UDEVSET.BPB"))
        assertEquals(null, PolarRuntimeOrchestration.userDeviceSettingsPath("UNKNOWN_FILE_SYSTEM", unknownSettingsPath = null))
    }

    @Test
    fun userDeviceSettingsRuntimeVectorRunsThroughCommonFakeTransportFacadeShape() {
        val vector = loadGoldenVectorText("sdk/user-device-settings-runtime/settings-runtime-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        input.objectArray("operations").forEach { operationJson ->
            val operation = PolarUserDeviceSettingsOperation(
                id = operationJson.stringValue("id"),
                kind = operationJson.stringValue("kind"),
                path = operationJson.stringValue("path"),
                payloadFields = operationJson.optionalStringArrayValue("payloadFields") ?: emptyList()
            )
            val planned = PolarRuntimeOrchestration.planUserDeviceSettings(operation)
            val expected = expectedCases.getValue(operation.id)
            val transport = ScriptedCommonFakeTransport(outcomesForTerminal(expected.stringValue("terminal")))
            val terminal = executePlannedSettingsOperation(planned.commands, operation.payloadFields, transport)

            assertEquals(expected.stringArrayValue("commands"), planned.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), terminal, operation.id)
            assertEquals(expectedTransportCommands(expected.stringArrayValue("commands"), operation.payloadFields), transport.commands, operation.id)
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

    private fun outcomesForTerminal(terminal: String): List<CommonFakeTransportOutcome> {
        return when (terminal) {
            "success" -> listOf(
                CommonFakeTransportOutcome.Bytes(byteArrayOf(0x01)),
                CommonFakeTransportOutcome.Complete
            )
            "transport-error" -> listOf(CommonFakeTransportOutcome.TransportError("settings-read-failed"))
            "transport-error-after-payload" -> listOf(
                CommonFakeTransportOutcome.Bytes(byteArrayOf(0x01)),
                CommonFakeTransportOutcome.TransportError("settings-write-failed")
            )
            else -> error("Unsupported settings runtime terminal $terminal")
        }
    }

    private fun executePlannedSettingsOperation(
        commands: List<String>,
        payloadFields: List<String>,
        transport: ScriptedCommonFakeTransport
    ): String {
        var terminal = "success"
        commands.forEach { command ->
            when {
                command.startsWith("read:") -> {
                    val outcome = transport.read(command.removePrefix("read:"))
                    if (outcome is CommonFakeTransportOutcome.TransportError) {
                        terminal = "transport-error"
                        return terminal
                    }
                    assertIs<CommonFakeTransportOutcome.Bytes>(outcome, command)
                }
                command.startsWith("write:") -> {
                    val outcome = transport.write(command.removePrefix("write:"), payloadFields.joinToString(separator = "|").encodeToByteArray())
                    if (outcome is CommonFakeTransportOutcome.TransportError) {
                        terminal = "transport-error-after-payload"
                        return terminal
                    }
                    assertIs<CommonFakeTransportOutcome.Complete>(outcome, command)
                }
            }
        }
        return terminal
    }

    private fun expectedTransportCommands(commands: List<String>, payloadFields: List<String>): List<CommonFakeTransportCommand> {
        val payloadHex = payloadFields.joinToString(separator = "|").encodeToByteArray().toHexString()
        return commands.mapNotNull { command ->
            when {
                command.startsWith("read:") -> CommonFakeTransportCommand(CommonFakeTransportOperation.READ, command.removePrefix("read:"))
                command.startsWith("write:") -> CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, command.removePrefix("write:"), payloadHex)
                else -> null
            }
        }
    }

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

}
