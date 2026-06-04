package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineTriggerRuntimePolicyCommonTest {
    @Test
    fun offlineTriggerRuntimePolicyVectorRunsThroughCommonFakeTransport() {
        val vector = loadGoldenVectorText("sdk/offline-recording/trigger-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val expectedPrototype = vector.objectValue("commonRuntimePrototype")
        val consumerTests = vector.objectValue("consumerTests")
        val scenarios = input.objectArray("scenarios").map { scenario ->
            OfflineTriggerRuntimeScenario(
                id = scenario.stringValue("id"),
                operation = scenario.stringValue("operation"),
                transport = scenario.objectValue("transport").toTriggerTransportScript()
            )
        }
        val expectedCases = expectedPrototype.objectArray("cases").associateBy { it.stringValue("id") }

        assertEquals("offlineTriggerRuntimePolicy", input.stringValue("kind"))
        assertEquals(REQUIRED_TRIGGER_RUNTIME_SCENARIOS, scenarios.map { it.id })
        assertEquals("offline-trigger-runtime-matrix", expected.stringValue("policy"))
        assertEquals("executable shared commonTest plus Android-hosted prototype", expectedPrototype.stringValue("status"))
        assertEquals(REQUIRED_TRIGGER_RUNTIME_SCENARIOS, expectedCases.keys.toList())
        assertEquals(TRIGGER_RUNTIME_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", vector.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers typed trigger runtime steps before platform facade behavior moves", vector.objectValue("platformExpectations").stringValue("common"))
        val cleanupEvidence = expected.objectValue("platformCleanupEvidence")
        assertEquals("android-stale-wrong-command-response-discard", cleanupEvidence.objectValue("android").stringValue("id"))
        assertEquals("ios-pre-command-response-queue-clear", cleanupEvidence.objectValue("ios").stringValue("id"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientTest", "com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest", "com.polar.sdk.impl.utils.OfflineTriggerCommonFakeRuntimeTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("BlePmdClientTest", "PolarBleApiImplTests", "PolarDataUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sdk.impl.utils.OfflineTriggerCommonFakeRuntimeTest", "com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        scenarios.forEach { scenario ->
            val outcome = FakeOfflineTriggerRuntime(input, scenario.transport).run(scenario.operation)
            val expected = expectedCases.getValue(scenario.id)

            assertEquals(expected.stringArrayValue("operations"), outcome.operations, scenario.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, scenario.id)
            expected.optionalStringArrayValue("enabledFeatures")?.let { enabledFeatures ->
                assertEquals(enabledFeatures, outcome.enabledFeatures, scenario.id)
            }
            expected.optionalStringArrayValue("excludedFeatures")?.let { excludedFeatures ->
                assertEquals(excludedFeatures, outcome.excludedFeatures, scenario.id)
            }
        }
    }

    @Test
    fun offlineTriggerRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/offline-recording/trigger-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("trigger-runtime-readiness", manifest.stringValue("id"))
        assertEquals("offlineTriggerRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/offline-recording/trigger-runtime-policy.json", policyVectorPath)
        assertEquals(REQUIRED_TRIGGER_RUNTIME_FAMILIES, requiredFamilies)
        assertEquals(REQUIRED_TRIGGER_RUNTIME_FAMILIES, coveredFamilies)
        assertTriggerRuntimePolicyVectorShape(policy)
        assertEquals(TRIGGER_RUNTIME_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.impl.utils.PolarDataUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDataUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun assertTriggerRuntimePolicyVectorShape(policy: String) {
        val input = policy.objectValue("input")
        val expected = policy.objectValue("expected")
        val commonRuntimePrototype = policy.objectValue("commonRuntimePrototype")
        val scenarios = input.objectArray("scenarios").associateBy { it.stringValue("id") }
        val expectedCases = commonRuntimePrototype.objectArray("cases").associateBy { it.stringValue("id") }
        assertEquals("trigger-runtime-policy", policy.stringValue("id"))
        assertEquals("trigger_runtime_policy", policy.stringValue("case"))
        assertEquals("offlineTriggerRuntimePolicy", input.stringValue("kind"))
        assertEquals(REQUIRED_TRIGGER_RUNTIME_SCENARIOS, scenarios.keys.toList())
        assertEquals(REQUIRED_TRIGGER_RUNTIME_SCENARIOS, expectedCases.keys.toList())
        assertEquals("offline-trigger-runtime-matrix", expected.stringValue("policy"))
        assertEquals(TRIGGER_RUNTIME_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", policy.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers typed trigger runtime steps before platform facade behavior moves", policy.objectValue("platformExpectations").stringValue("common"))
        assertEquals("BDBleApiImplTest pins public facade get/set mapping, secret propagation, and set/get error propagation; BlePmdClientTest pins Android byte-level PMD packet framing with length-prefixed enabled trigger settings and stale wrong-command response discard during offline-trigger status reads", policy.objectValue("platformExpectations").stringValue("android"))
        assertEquals("PolarBleApiImplTests pins public facade get/set mapping, secret propagation, set-mode error propagation, and get-status error propagation; BlePmdClientTest pins iOS byte-level PMD packet framing without Android's enabled-trigger length prefix and pre-command PMD response queue clearing", policy.objectValue("platformExpectations").stringValue("ios"))
        assertEquals(TRIGGER_RUNTIME_NOTES, policy.stringValue("notes"))
        assertEquals("TRIGGER_SYSTEM_START", input.objectValue("desiredTrigger").stringValue("mode"))
        assertEquals(true, input.objectValue("desiredTrigger").objectValue("secret").booleanValue("present"))
        assertEquals(listOf("ACC", "GYRO", "OFFLINE_HR"), input.objectArray("currentDeviceTriggers").map { it.stringValue("type") })
        assertEquals(listOf("ACC", "HR"), input.objectValue("desiredTrigger").objectArray("features").map { it.stringValue("type") })
        assertEquals("controlPointError", scenarios.getValue("set-trigger-mode-error").objectValue("transport").stringValue("setMode"))
        assertEquals("transportError", scenarios.getValue("set-trigger-status-read-error").objectValue("transport").stringValue("getStatus"))
        assertEquals("controlPointError", scenarios.getValue("set-trigger-setting-error").objectValue("transport").stringValue("setSettings"))
        assertEquals("transportError", scenarios.getValue("get-trigger-transport-error").objectValue("transport").stringValue("getStatus"))
        assertEquals(listOf("setMode:TRIGGER_SYSTEM_START", "getStatus", "setSetting:ACC:enabled:settings:secret", "setSetting:GYRO:disabled", "setSetting:OFFLINE_HR:enabled:no-settings:secret"), expectedCases.getValue("set-trigger-success-with-secret").stringArrayValue("operations"))
        assertEquals(listOf("ACC", "HR"), expectedCases.getValue("get-trigger-success").stringArrayValue("enabledFeatures"))
        assertEquals(listOf("GYRO"), expectedCases.getValue("get-trigger-success").stringArrayValue("excludedFeatures"))
        val cleanupEvidence = expected.objectValue("platformCleanupEvidence")
        assertEquals("android-stale-wrong-command-response-discard", cleanupEvidence.objectValue("android").stringValue("id"))
        assertEquals("ios-pre-command-response-queue-clear", cleanupEvidence.objectValue("ios").stringValue("id"))
    }

    private inner class FakeOfflineTriggerRuntime(
        input: String,
        private val transportScript: TriggerTransportScript
    ) {
        private val currentDeviceTriggers = input.objectArray("currentDeviceTriggers").map { trigger ->
            DeviceTrigger(
                type = trigger.stringValue("type"),
                status = trigger.stringValue("status")
            )
        }
        private val desiredTrigger = input.objectValue("desiredTrigger")
        private val desiredFeatures = desiredTrigger.objectArray("features").associateBy { feature -> feature.stringValue("type") }
        private val desiredMode = desiredTrigger.stringValue("mode")
        private val secretPresent = desiredTrigger.objectValue("secret").contains("\"present\"\\s*:\\s*true".toRegex())

        fun run(operation: String): TriggerRuntimeOutcome {
            return when (operation) {
                "setOfflineRecordingTrigger" -> setTrigger()
                "getOfflineRecordingTriggerSetup" -> getTrigger()
                else -> error("Unsupported offline trigger operation $operation")
            }
        }

        private fun setTrigger(): TriggerRuntimeOutcome {
            val operations = mutableListOf<String>()
            val modeTransport = ScriptedCommonFakeTransport(listOf(transportScript.setMode.toTransportOutcome()))

            val setModeOutcome = modeTransport.write(PMD_CONTROL_POINT, modePayload())
            operations += modeTransport.operationLabels()
            if (setModeOutcome !is CommonFakeTransportOutcome.Complete) {
                return TriggerRuntimeOutcome(operations, setModeOutcome.toTerminal())
            }

            val statusTransport = ScriptedCommonFakeTransport(listOf(transportScript.getStatus.toTransportOutcome(statusPayload())))
            val statusOutcome = statusTransport.read(PMD_CONTROL_POINT)
            operations += statusTransport.operationLabels()
            if (statusOutcome !is CommonFakeTransportOutcome.Bytes) {
                return TriggerRuntimeOutcome(operations, statusOutcome.toTerminal())
            }

            for (current in currentDeviceTriggers) {
                val desired = desiredFeatures[current.type] ?: desiredFeatures[current.type.toPublicFeature()]
                val payload = desired?.settingPayload(current.type) ?: disabledPayload(current.type)
                val settingTransport = ScriptedCommonFakeTransport(listOf(transportScript.setSettings.toTransportOutcome()))
                val settingOutcome = settingTransport.write(PMD_CONTROL_POINT, payload)
                operations += settingTransport.operationLabels(current, desired)
                if (settingOutcome !is CommonFakeTransportOutcome.Complete) {
                    return TriggerRuntimeOutcome(operations, settingOutcome.toTerminal())
                }
            }

            return TriggerRuntimeOutcome(operations, "success")
        }

        private fun getTrigger(): TriggerRuntimeOutcome {
            val transport = ScriptedCommonFakeTransport(listOf(transportScript.getStatus.toTransportOutcome(statusPayload())))
            val statusOutcome = transport.read(PMD_CONTROL_POINT)
            if (statusOutcome !is CommonFakeTransportOutcome.Bytes) {
                return TriggerRuntimeOutcome(transport.operationLabels(), statusOutcome.toTerminal())
            }
            val enabled = currentDeviceTriggers
                .filter { trigger -> trigger.status == "enabled" && trigger.type != "GYRO" }
                .map { trigger -> trigger.type.toPublicFeature() }
            return TriggerRuntimeOutcome(
                operations = transport.operationLabels(),
                terminal = "success",
                enabledFeatures = enabled,
                excludedFeatures = listOf("GYRO")
            )
        }

        private fun modePayload(): ByteArray = byteArrayOf(0x01, desiredMode.modeByte())

        private fun String.settingPayload(currentType: String): ByteArray {
            val settingMarker = if (contains("\"selectedSettings\"\\s*:\\s*\\{".toRegex())) 0x01 else 0x00
            val secretMarker = if (secretPresent) 0x01 else 0x00
            return byteArrayOf(currentType.featureByte(), 0x01, settingMarker.toByte(), secretMarker.toByte())
        }

        private fun disabledPayload(currentType: String): ByteArray = byteArrayOf(currentType.featureByte(), 0x00)

        private fun statusPayload(): ByteArray {
            return ByteArray(currentDeviceTriggers.size) { index ->
                currentDeviceTriggers[index].type.featureByte()
            }
        }
    }

    private fun ScriptedCommonFakeTransport.operationLabels(): List<String> {
        return commands.map { command ->
            when (command.operation) {
                CommonFakeTransportOperation.WRITE -> if (command.payloadHex?.startsWith("01") == true) {
                    "setMode:TRIGGER_SYSTEM_START"
                } else {
                    "write:${command.target}:${command.payloadHex}"
                }
                CommonFakeTransportOperation.READ -> "getStatus"
                CommonFakeTransportOperation.REMOVE -> "remove:${command.target}"
                CommonFakeTransportOperation.SUBSCRIBE -> "subscribe:${command.target}"
                CommonFakeTransportOperation.UNSUBSCRIBE -> "unsubscribe:${command.target}"
                CommonFakeTransportOperation.RECONNECT -> "reconnect"
            }
        }
    }

    private fun ScriptedCommonFakeTransport.operationLabels(current: DeviceTrigger, desired: String?): List<String> {
        return commands.map { command ->
            when (command.operation) {
                CommonFakeTransportOperation.WRITE -> {
                    if (desired != null) {
                        val hasSettings = desired.contains("\"selectedSettings\"\\s*:\\s*\\{".toRegex())
                        val secretMarker = if (command.payloadHex?.endsWith("01") == true) "secret" else "no-secret"
                        "setSetting:${current.type}:enabled:${if (hasSettings) "settings" else "no-settings"}:$secretMarker"
                    } else {
                        "setSetting:${current.type}:disabled"
                    }
                }
                CommonFakeTransportOperation.READ -> "getStatus"
                CommonFakeTransportOperation.REMOVE -> "remove:${command.target}"
                CommonFakeTransportOperation.SUBSCRIBE -> "subscribe:${command.target}"
                CommonFakeTransportOperation.UNSUBSCRIBE -> "unsubscribe:${command.target}"
                CommonFakeTransportOperation.RECONNECT -> "reconnect"
            }
        }
    }

    private fun String.toTriggerTransportScript(): TriggerTransportScript {
        return TriggerTransportScript(
            setMode = optionalStringValue("setMode")?.toTriggerTransportResult() ?: TriggerTransportResult.Success,
            getStatus = optionalStringValue("getStatus")?.toTriggerTransportResult() ?: TriggerTransportResult.Success,
            setSettings = optionalStringValue("setSettings")?.toTriggerTransportResult() ?: TriggerTransportResult.Success
        )
    }

    private fun String.toTriggerTransportResult(): TriggerTransportResult {
        return when (this) {
            "success" -> TriggerTransportResult.Success
            "controlPointError" -> TriggerTransportResult.ControlPointError
            "transportError" -> TriggerTransportResult.TransportError
            else -> error("Unsupported trigger transport result $this")
        }
    }

    private fun TriggerTransportResult.toTransportOutcome(bytes: ByteArray = ByteArray(0)): CommonFakeTransportOutcome {
        return when (this) {
            TriggerTransportResult.Success -> if (bytes.isEmpty()) CommonFakeTransportOutcome.Complete else CommonFakeTransportOutcome.Bytes(bytes)
            TriggerTransportResult.ControlPointError -> CommonFakeTransportOutcome.ResponseError(1, "control-point-error")
            TriggerTransportResult.TransportError -> CommonFakeTransportOutcome.TransportError("transport-error")
        }
    }

    private fun CommonFakeTransportOutcome.toTerminal(): String {
        return when (this) {
            is CommonFakeTransportOutcome.ResponseError -> "control-point-error"
            is CommonFakeTransportOutcome.TransportError -> "transport-error"
            is CommonFakeTransportOutcome.Timeout -> "transport-error"
            is CommonFakeTransportOutcome.Bytes,
            CommonFakeTransportOutcome.Complete -> "success"
        }
    }

    private fun String.modeByte(): Byte {
        return when (this) {
            "TRIGGER_SYSTEM_START" -> 0x02
            else -> error("Unsupported trigger mode $this")
        }
    }

    private fun String.featureByte(): Byte {
        return when (this) {
            "ACC" -> 0x01
            "GYRO" -> 0x02
            "OFFLINE_HR",
            "HR" -> 0x03
            else -> error("Unsupported feature type $this")
        }
    }

    private fun String.toPublicFeature(): String {
        return if (this == "OFFLINE_HR") "HR" else this
    }

    private data class OfflineTriggerRuntimeScenario(
        val id: String,
        val operation: String,
        val transport: TriggerTransportScript
    )

    private data class TriggerTransportScript(
        val setMode: TriggerTransportResult,
        val getStatus: TriggerTransportResult,
        val setSettings: TriggerTransportResult
    )

    private enum class TriggerTransportResult {
        Success,
        ControlPointError,
        TransportError
    }

    private data class DeviceTrigger(
        val type: String,
        val status: String
    )

    private data class TriggerRuntimeOutcome(
        val operations: List<String>,
        val terminal: String,
        val enabledFeatures: List<String> = emptyList(),
        val excludedFeatures: List<String> = emptyList()
    )

    private companion object {
        const val PMD_CONTROL_POINT = "pmd-control-point"
        val REQUIRED_TRIGGER_RUNTIME_SCENARIOS = listOf(
            "set-trigger-success-with-secret",
            "set-trigger-mode-error",
            "set-trigger-status-read-error",
            "set-trigger-setting-error",
            "get-trigger-success",
            "get-trigger-transport-error"
        )
        val REQUIRED_TRIGGER_RUNTIME_FAMILIES = listOf(
            "typed-set-mode",
            "status-read",
            "settings-write",
            "optional-secret-attachment",
            "get-transport-error",
            "set-mode-control-point-error",
            "status-read-transport-error",
            "settings-control-point-error",
            "enabled-feature-projection",
            "excluded-feature-projection",
            "platform-packet-split",
            "facade-error-mapping-deferred",
            "compile-verification-gate"
        )
        const val TRIGGER_RUNTIME_COMMON_DECISION = "Shared offline trigger runtime code should model set-mode, status-read, per-feature setting writes, optional secret attachment, and get/set transport failures as typed steps before mapping them back to Android and iOS public errors."
        const val TRIGGER_RUNTIME_READINESS_COMMON_DECISION = "Offline trigger runtime migration may proceed only after trigger-runtime-policy.json and this readiness manifest are executable from shared commonTest, platform facade tests continue to reference the same policy vector, packet-framing differences are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified."
        const val TRIGGER_RUNTIME_NOTES = "Android serializes enabled trigger settings with an explicit length byte before setting and secret bytes, while current iOS appends setting and secret bytes directly. Android discards stale PMD control-point responses with an unexpected command byte during offline-trigger status reads; iOS clears the response queue before transmitting a new control-point command. KMP must choose a shared packet and queue-cleanup contract or preserve these platform splits behind platform adapters."
    }
}
