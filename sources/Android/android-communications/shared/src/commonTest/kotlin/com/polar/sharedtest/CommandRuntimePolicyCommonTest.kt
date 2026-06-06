package com.polar.sharedtest

import com.polar.shared.runtime.PolarFacadeCommandOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandRuntimePolicyCommonTest {
    @Test
    fun resetSyncH10CommandPolicyVectorDefinesExecutableCommonCommandPlanning() {
        val vector = loadGoldenVectorText("sdk/command-runtime/reset-sync-h10-command-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val operations = input.objectArray("operations").map { operation ->
            PolarFacadeCommandOperation(
                id = operation.stringValue("id"),
                kind = operation.stringValue("kind"),
                query = operation.optionalStringValue("query"),
                parameters = operation.optionalStringArrayValue("parameters") ?: emptyList(),
                notifications = operation.optionalStringArrayValue("notifications") ?: emptyList(),
                sleep = operation.optionalBooleanValue("sleep"),
                factoryDefaults = operation.optionalBooleanValue("factoryDefaults"),
                otaFirmwareUpdate = operation.optionalBooleanValue("otaFirmwareUpdate")
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }

        assertEquals("reset-sync-h10-command-policy", vector.stringValue("id"))
        assertEquals("reset_sync_h10_command_policy", vector.stringValue("case"))
        assertEquals(requiredCommandRuntimeOperationIds, operations.map { it.id })
        assertEquals(requiredCommandRuntimeOperationIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-command-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-command-capture", vector.objectValue("execution").stringValue("transport"))
        assertCommandRuntimePolicyFields(input.objectArray("operations").associateBy { it.stringValue("id") })

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planCommand(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
        }

        val platformExpectations = vector.objectValue("platformExpectations")
        assertEquals(COMMAND_POLICY_ANDROID_SYNC_START_QUERY_FAILURE, platformExpectations.objectValue("android").stringValue("syncStartQueryFailure"))
        assertEquals(COMMAND_POLICY_ANDROID_SYNC_STOP_NOTIFICATION_FAILURE, platformExpectations.objectValue("android").stringValue("syncStopNotificationFailure"))
        assertEquals(COMMAND_POLICY_IOS_SYNC_START_QUERY_FAILURE, platformExpectations.objectValue("ios").stringValue("syncStartQueryFailure"))
        assertEquals(COMMAND_POLICY_IOS_SYNC_STOP_NOTIFICATION_FAILURE, platformExpectations.objectValue("ios").stringValue("syncStopNotificationFailure"))
        assertEquals(COMMAND_POLICY_PLATFORM_SPLIT_DECISION, platformExpectations.stringValue("commonDecision"))
        assertEquals(COMMAND_POLICY_COMMON_DECISION, vector.stringValue("commonDecision"))
    }

    @Test
    fun resetSyncH10CommandReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/command-runtime/reset-sync-h10-command-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("reset-sync-h10-command-readiness", manifest.stringValue("id"))
        assertEquals("resetSyncH10CommandReadiness", input.stringValue("kind"))
        assertEquals("sdk/command-runtime/reset-sync-h10-command-policy.json", policyVectorPath)
        assertEquals(requiredCommandRuntimeFamilies, requiredFamilies)
        assertEquals(requiredCommandRuntimeFamilies, coveredFamilies)
        assertEquals("reset-sync-h10-command-policy", policy.stringValue("id"))
        assertEquals("reset_sync_h10_command_policy", policy.stringValue("case"))
        assertEquals(requiredCommandRuntimeOperationIds, policy.objectValue("input").objectArray("operations").map { it.stringValue("id") })
        assertEquals(requiredCommandRuntimeOperationIds, policy.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").map { it.stringValue("id") })
        assertCommandRuntimePolicyFields(policy.objectValue("input").objectArray("operations").associateBy { it.stringValue("id") })
        assertEquals(COMMAND_RUNTIME_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.CommandRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    @Test
    fun resetNotificationFieldProjectionMatchesSharedCommandFlags() {
        val factoryResetFields = PolarRuntimeOrchestration.resetNotificationFields(
            PolarFacadeCommandOperation(
                id = "factory-reset",
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = false,
                factoryDefaults = true,
                otaFirmwareUpdate = false
            )
        )
        val warehouseSleepFields = PolarRuntimeOrchestration.resetNotificationFields(
            PolarFacadeCommandOperation(
                id = "warehouse-sleep",
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = true,
                factoryDefaults = true,
                otaFirmwareUpdate = false
            )
        )

        assertEquals(false, factoryResetFields.sleep)
        assertEquals(true, factoryResetFields.factoryDefaults)
        assertEquals(false, factoryResetFields.otaFirmwareUpdate)
        assertEquals(true, warehouseSleepFields.sleep)
        assertEquals(true, warehouseSleepFields.factoryDefaults)
        assertEquals(false, warehouseSleepFields.otaFirmwareUpdate)
    }

    @Test
    fun resetSyncH10CommandVectorRunsThroughCommonFakeTransportFacadeShape() {
        val vector = loadGoldenVectorText("sdk/command-runtime/reset-sync-h10-command-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        input.objectArray("operations").forEach { operationJson ->
            val operation = PolarFacadeCommandOperation(
                id = operationJson.stringValue("id"),
                kind = operationJson.stringValue("kind"),
                query = operationJson.optionalStringValue("query"),
                parameters = operationJson.optionalStringArrayValue("parameters") ?: emptyList(),
                notifications = operationJson.optionalStringArrayValue("notifications") ?: emptyList(),
                sleep = operationJson.optionalBooleanValue("sleep"),
                factoryDefaults = operationJson.optionalBooleanValue("factoryDefaults"),
                otaFirmwareUpdate = operationJson.optionalBooleanValue("otaFirmwareUpdate")
            )
            val planned = PolarRuntimeOrchestration.planCommand(operation)
            val expected = expectedCases.getValue(operation.id)
            val transportCommands = commandTransportCommands(expected.stringArrayValue("commands"))
            val transport = ScriptedCommonFakeTransport(outcomesForCommandTransport(transportCommands, expected.stringValue("terminal")))
            val terminal = executePlannedCommand(transportCommands, expected.stringValue("terminal"), transport)

            assertEquals(expected.stringArrayValue("commands"), planned.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), terminal, operation.id)
            assertEquals(transportCommands, transport.commands, operation.id)
        }
    }

    private val requiredCommandRuntimeOperationIds = listOf(
        "h10-start-recording",
        "h10-start-recording-query-failure",
        "h10-stop-recording",
        "h10-stop-recording-query-failure",
        "h10-recording-status",
        "h10-recording-status-query-failure",
        "factory-reset",
        "factory-reset-notification-failure",
        "factory-reset-preserve-pairing",
        "factory-reset-preserve-pairing-notification-failure",
        "restart",
        "restart-notification-failure",
        "warehouse-sleep",
        "warehouse-sleep-notification-failure",
        "turn-device-off",
        "turn-device-off-notification-failure",
        "sync-start-success",
        "sync-start-query-failure",
        "sync-stop-success",
        "sync-stop-notification-failure"
    )

    private val requiredCommandRuntimeFamilies = listOf(
        "h10-recording-start-query",
        "h10-recording-start-query-failure",
        "h10-recording-stop-query",
        "h10-recording-stop-query-failure",
        "h10-recording-status-query",
        "h10-recording-status-query-failure",
        "factory-reset-flags",
        "factory-reset-notification-failure",
        "preserve-pairing-reset-flags",
        "preserve-pairing-reset-notification-failure",
        "restart-reset-flags",
        "restart-reset-notification-failure",
        "warehouse-sleep-reset-flags",
        "warehouse-sleep-reset-notification-failure",
        "turn-device-off-reset-flags",
        "turn-device-off-reset-notification-failure",
        "sync-start-notification-sequence",
        "sync-start-query-failure-platform-split",
        "sync-stop-complete-terminate-sequence",
        "sync-stop-notification-failure-platform-split",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val COMMAND_POLICY_ANDROID_SYNC_START_QUERY_FAILURE = "returns false and sends no notifications"
    private val COMMAND_POLICY_ANDROID_SYNC_STOP_NOTIFICATION_FAILURE = "swallows the notification error after STOP_SYNC is attempted"
    private val COMMAND_POLICY_IOS_SYNC_START_QUERY_FAILURE = "propagates the query error and sends no notifications"
    private val COMMAND_POLICY_IOS_SYNC_STOP_NOTIFICATION_FAILURE = "propagates the notification error after STOP_SYNC is attempted"
    private val COMMAND_POLICY_PLATFORM_SPLIT_DECISION = "Keep sync failure policy platform-specific until the public API migration has an approved compatibility decision."
    private val COMMAND_POLICY_COMMON_DECISION = "Promote reset/H10 command planning before sync error handling; H10 query failures and reset notification failures are shared transport-error propagation, while sync failure terminals remain platform compatibility gates."

    private val COMMAND_RUNTIME_READINESS_COMMON_DECISION = "Command runtime migration may proceed only after reset-sync-h10-command-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, H10 query failure propagation, every reset-style notification failure propagation, and public facade error mapping are pinned, sync-start and sync-stop platform splits are preserved or explicitly reconciled, and the shared tests are compile-verified."

    private fun outcomesForCommandTransport(commands: List<CommonFakeTransportCommand>, terminal: String): List<CommonFakeTransportOutcome> {
        if (terminal == "transport-error" || terminal == "platform-split") {
            return if (commands.size == 1) {
                listOf(CommonFakeTransportOutcome.TransportError("command-failed"))
            } else {
                commands.dropLast(1).map { CommonFakeTransportOutcome.Complete } + CommonFakeTransportOutcome.TransportError("command-failed")
            }
        }
        return commands.map { CommonFakeTransportOutcome.Complete }
    }

    private fun executePlannedCommand(
        commands: List<CommonFakeTransportCommand>,
        expectedTerminal: String,
        transport: ScriptedCommonFakeTransport
    ): String {
        commands.forEach { command ->
            val payload = command.payloadHex?.let(::hexToBytes) ?: byteArrayOf()
            val outcome = transport.write(command.target, payload)
            if (outcome is CommonFakeTransportOutcome.TransportError) {
                return expectedTerminal
            }
            assertIs<CommonFakeTransportOutcome.Complete>(outcome, command.target)
        }
        return expectedTerminal
    }

    private fun commandTransportCommands(commands: List<String>): List<CommonFakeTransportCommand> {
        val transportCommands = mutableListOf<CommonFakeTransportCommand>()
        var index = 0
        while (index < commands.size) {
            val command = commands[index]
            when {
                command.startsWith("query:") -> {
                    val payloadFields = mutableListOf<String>()
                    index += 1
                    while (index < commands.size && (commands[index].startsWith("parameter:") || commands[index] == "parameters:none")) {
                        payloadFields += commands[index]
                        index += 1
                    }
                    transportCommands += CommonFakeTransportCommand(
                        operation = CommonFakeTransportOperation.WRITE,
                        target = command,
                        payloadHex = payloadFields.joinToString(separator = "|").encodeToByteArray().toHexString()
                    )
                }
                command.startsWith("notification:") -> {
                    val payloadFields = mutableListOf<String>()
                    index += 1
                    while (index < commands.size && commands[index].startsWith("flag:")) {
                        payloadFields += commands[index]
                        index += 1
                    }
                    transportCommands += CommonFakeTransportCommand(
                        operation = CommonFakeTransportOperation.WRITE,
                        target = command,
                        payloadHex = payloadFields.joinToString(separator = "|").encodeToByteArray().toHexString()
                    )
                }
                else -> index += 1
            }
        }
        return transportCommands
    }

    private fun assertCommandRuntimePolicyFields(operationsById: Map<String, String>) {
        assertEquals("REQUEST_START_RECORDING", operationsById.getValue("h10-start-recording").stringValue("query"))
        assertEquals(listOf("sampleDataIdentifier=myExercise", "sampleType=SAMPLE_TYPE_HEART_RATE", "recordingIntervalSeconds=1"), operationsById.getValue("h10-start-recording").stringArrayValue("parameters"))
        assertEquals("queryFailure", operationsById.getValue("h10-start-recording-query-failure").stringValue("kind"))
        assertEquals("REQUEST_START_RECORDING", operationsById.getValue("h10-start-recording-query-failure").stringValue("query"))
        assertEquals("start-recording-query-failed", operationsById.getValue("h10-start-recording-query-failure").stringValue("error"))
        assertEquals("REQUEST_STOP_RECORDING", operationsById.getValue("h10-stop-recording").stringValue("query"))
        assertEquals("queryFailure", operationsById.getValue("h10-stop-recording-query-failure").stringValue("kind"))
        assertEquals("REQUEST_STOP_RECORDING", operationsById.getValue("h10-stop-recording-query-failure").stringValue("query"))
        assertEquals("stop-recording-query-failed", operationsById.getValue("h10-stop-recording-query-failure").stringValue("error"))
        assertEquals("REQUEST_RECORDING_STATUS", operationsById.getValue("h10-recording-status").stringValue("query"))
        assertEquals("queryFailure", operationsById.getValue("h10-recording-status-query-failure").stringValue("kind"))
        assertEquals("REQUEST_RECORDING_STATUS", operationsById.getValue("h10-recording-status-query-failure").stringValue("query"))
        assertEquals("recording-status-query-failed", operationsById.getValue("h10-recording-status-query-failure").stringValue("error"))
    }
}
