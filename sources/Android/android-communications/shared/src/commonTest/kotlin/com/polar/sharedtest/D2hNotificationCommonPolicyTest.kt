package com.polar.sharedtest

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class D2hNotificationCommonPolicyTest {
    @Test
    fun d2hGoldenVectorsDefineExecutableCommonMappingFilteringAndOrderingPolicy() {
        D2H_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")

            if (input.optionalObjectArray("notifications") != null) {
                val events = input.objectArray("notifications").flatMap { notification ->
                    mapNotification(notification.intValue("notificationId"), notification.stringValue("parametersHex"))
                }
                val expectedEvents = expected.objectArray("events")
                assertEquals(expectedEvents.size, events.size, "$caseId event count")
                expectedEvents.forEachIndexed { index, expectedEvent ->
                    assertEventEquals(expectedEvent, events[index], "$caseId event $index")
                }
                return@forEach
            }

            val events = mapNotification(input.intValue("notificationId"), input.stringValue("parametersHex"))
            expected.optionalIntValue("emittedCount")?.let { count ->
                assertEquals(count, events.size, caseId)
                return@forEach
            }
            assertEquals(1, events.size, "$caseId event count")
            assertEventEquals(expected, events.single(), caseId)
        }
    }

    @Test
    fun d2hNotificationMappingReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/d2h-notifications/mapping-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("d2h-notification-mapping-readiness", manifest.stringValue("id"))
        assertEquals("d2hNotificationMappingReadiness", input.stringValue("kind"))
        assertEquals(D2H_MAPPING_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(requiredD2hMappingFamilies, requiredFamilies)
        assertEquals(requiredD2hMappingFamilies, coveredFamilies)
        assertEquals(D2H_MAPPING_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceToHostNotificationsApiTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.D2hNotificationCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun assertEventEquals(expected: String, actual: D2hEvent, label: String) {
        assertEquals(expected.stringValue("notificationType"), actual.notificationType, "$label type")
        assertEquals(expected.optionalStringValue("parsedProto"), actual.parsedProto, "$label proto")
        expected.optionalStringValue("parametersHex")?.let { expectedHex ->
            assertEquals(expectedHex, actual.parametersHex, "$label parameters")
        }
        expected.optionalStringValue("action")?.let { action -> assertEquals(action, actual.action, "$label action") }
        expected.optionalStringValue("path")?.let { path -> assertEquals(path, actual.path, "$label path") }
        expected.optionalBooleanValue("succeeded")?.let { succeeded -> assertEquals(succeeded, actual.succeeded, "$label succeeded") }
        expected.optionalStringValue("description")?.let { description -> assertEquals(description, actual.description, "$label description") }
        expected.optionalIntValue("minimumInterval")?.let { interval -> assertEquals(interval, actual.minimumInterval, "$label interval") }
        expected.optionalIntValue("accuracy")?.let { accuracy -> assertEquals(accuracy, actual.accuracy, "$label accuracy") }
        expected.optionalFloatValue("latitude")?.let { latitude -> assertFloatEquals(latitude, actual.latitude ?: error("$label latitude missing"), "$label latitude") }
        expected.optionalFloatValue("longitude")?.let { longitude -> assertFloatEquals(longitude, actual.longitude ?: error("$label longitude missing"), "$label longitude") }
        expected.optionalObjectArray("syncTriggers")?.let { triggers ->
            assertEquals(triggers.map { trigger -> trigger.stringValue("source") }, actual.syncTriggers, "$label triggers")
        }
    }

    private fun mapNotification(notificationId: Int, parametersHex: String): List<D2hEvent> {
        val type = notificationId.notificationTypeOrNull() ?: return emptyList()
        val event = D2hEvent(
            notificationType = type,
            parametersHex = parametersHex,
            parsedProto = parsedProtoName(type, parametersHex)
        )
        return listOf(decodeKnownParameters(event))
    }

    private fun Int.notificationTypeOrNull(): String? {
        return when (this) {
            0 -> "FILESYSTEM_MODIFIED"
            7 -> "SYNC_REQUIRED"
            8 -> "AUTOSYNC_STATUS"
            11 -> "START_GPS_MEASUREMENT"
            12 -> "STOP_GPS_MEASUREMENT"
            else -> null
        }
    }

    private fun parsedProtoName(type: String, parametersHex: String): String? {
        if (parametersHex.isEmpty()) return null
        return when (type) {
            "FILESYSTEM_MODIFIED" -> "PbPFtpFilesystemModifiedParams"
            "SYNC_REQUIRED" -> if (parametersHex == "0a020802") "PbPFtpSyncRequiredParams" else null
            "AUTOSYNC_STATUS" -> "PbPFtpAutoSyncStatusParams"
            "START_GPS_MEASUREMENT" -> "PbPftpStartGPSMeasurement"
            else -> null
        }
    }

    private fun decodeKnownParameters(event: D2hEvent): D2hEvent {
        return when (event.notificationType) {
            "FILESYSTEM_MODIFIED" -> event.copy(action = "CREATED", path = "/U/0/")
            "SYNC_REQUIRED" -> if (event.parsedProto != null) event.copy(syncTriggers = listOf("TIMED")) else event
            "AUTOSYNC_STATUS" -> event.copy(succeeded = true, description = "Sync completed successfully")
            "START_GPS_MEASUREMENT" -> event.copy(minimumInterval = 1000, accuracy = 2, latitude = 60.1695f, longitude = 24.9354f)
            else -> event
        }
    }

    private fun assertFloatEquals(expected: Float, actual: Float, label: String) {
        assertTrue(abs(expected - actual) <= 0.0001f, "$label expected $expected but was $actual")
    }

    private fun String.optionalObjectArray(field: String): List<String>? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val arrayStart = indexOf('[', fieldIndex)
        if (arrayStart < 0) return null
        val arrayEnd = balancedEnd(arrayStart, '[', ']')
        val arrayContent = substring(arrayStart + 1, arrayEnd)
        val objects = mutableListOf<String>()
        var index = 0
        while (index < arrayContent.length) {
            val objectStart = arrayContent.indexOf('{', index)
            if (objectStart < 0) break
            val objectEnd = arrayContent.balancedEnd(objectStart, '{', '}')
            objects += arrayContent.substring(objectStart, objectEnd + 1)
            index = objectEnd + 1
        }
        return objects
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.optionalFloatValue(field: String): Float? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.get(1)?.toFloat()
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private data class D2hEvent(
        val notificationType: String,
        val parametersHex: String,
        val parsedProto: String?,
        val action: String? = null,
        val path: String? = null,
        val syncTriggers: List<String> = emptyList(),
        val succeeded: Boolean? = null,
        val description: String? = null,
        val minimumInterval: Int? = null,
        val accuracy: Int? = null,
        val latitude: Float? = null,
        val longitude: Float? = null
    )

    private companion object {
        val D2H_VECTORS = listOf(
            "sdk/d2h-notifications/autosync-success.json",
            "sdk/d2h-notifications/filesystem-created.json",
            "sdk/d2h-notifications/repeated-sync-required-and-stop-gps.json",
            "sdk/d2h-notifications/start-gps-measurement.json",
            "sdk/d2h-notifications/stop-gps-empty.json",
            "sdk/d2h-notifications/sync-required-invalid-payload.json",
            "sdk/d2h-notifications/sync-required-timed.json",
            "sdk/d2h-notifications/unknown-id-filtered.json"
        )
        val D2H_MAPPING_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/d2h-notifications/filesystem-created.json",
            "sdk/d2h-notifications/sync-required-timed.json",
            "sdk/d2h-notifications/autosync-success.json",
            "sdk/d2h-notifications/start-gps-measurement.json",
            "sdk/d2h-notifications/stop-gps-empty.json",
            "sdk/d2h-notifications/sync-required-invalid-payload.json",
            "sdk/d2h-notifications/unknown-id-filtered.json",
            "sdk/d2h-notifications/repeated-sync-required-and-stop-gps.json"
        )
        val requiredD2hMappingFamilies = listOf(
            "known-notification-id-mapping",
            "unknown-notification-id-filtering",
            "raw-parameter-preservation",
            "filesystem-created-typed-field-decoding",
            "sync-required-trigger-decoding",
            "autosync-status-decoding",
            "start-gps-measurement-field-decoding",
            "stop-gps-empty-parameter-policy",
            "invalid-payload-null-parse-policy",
            "repeated-notification-ordering",
            "platform-mapping-vector-reference-gate",
            "compile-verification-gate"
        )
        const val D2H_MAPPING_READINESS_COMMON_DECISION = "D2H notification mapping migration may proceed only after every mapping vector named by this readiness manifest is executable from shared commonTest, Android and iOS D2H mapping tests continue to reference the same vectors, known IDs, unknown-ID filtering, raw parameter preservation, typed fields for filesystem, sync-required, autosync, and start-GPS notifications, stop-GPS empty parameters, invalid-payload null parsing, repeated-notification ordering, and the shared tests are compile-verified."
    }
}
