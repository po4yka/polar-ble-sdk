package com.polar.sharedtest

import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoredDataCleanupRuntimePolicyCommonTest {
    @Test
    fun cleanupWorkflowPolicyVectorDefinesExecutableCommonTraversalAndPlatformSplits() {
        val vector = loadGoldenVectorText("sdk/stored-data-cleanup/cleanup-workflow-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val scenarioList = input.objectArray("scenarios")
        val scenarios = scenarioList.map { scenario ->
            CleanupScenario(
                id = scenario.stringValue("id"),
                kind = scenario.stringValue("kind"),
                rootPath = scenario.optionalStringValue("rootPath"),
                includePrefixes = scenario.optionalStringArrayValue("includePrefixes") ?: emptyList(),
                includeSuffixes = scenario.optionalStringArrayValue("includeSuffixes") ?: emptyList(),
                entries = scenario.optionalStringArrayValue("entries") ?: emptyList(),
                cutoffDate = scenario.optionalStringValue("cutoffDate"),
                dateFolders = scenario.optionalObjectArray("dateFolders"),
                sampleFiles = scenario.optionalObjectArray("sampleFiles")
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }
        val runtime = FakeStoredDataCleanupRuntime()

        assertEquals("stored-data-cleanup-workflow-policy", vector.stringValue("id"))
        assertEquals("cleanup_workflow_policy", vector.stringValue("case"))
        assertEquals(requiredCleanupScenarioIds, scenarios.map { it.id })
        assertEquals(requiredCleanupScenarioIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-cleanup-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("directory-list-and-remove-command-capture", vector.objectValue("execution").stringValue("transport"))
        assertCleanupPolicyFields(scenarioList.associateBy { it.stringValue("id") })

        scenarios.forEach { scenario ->
            val outcome = runtime.run(scenario)
            val expected = expectedCases.getValue(scenario.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, scenario.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, scenario.id)
        }

        val platformExpectations = vector.objectValue("platformExpectations")
        assertEquals(STORED_DATA_CLEANUP_ANDROID_SDLOGS_LIST_FAILURE, platformExpectations.objectValue("android").stringValue("sdlogsListFailure"))
        assertEquals(STORED_DATA_CLEANUP_ANDROID_TELEMETRY_LIST_FAILURE, platformExpectations.objectValue("android").stringValue("telemetryListFailure"))
        assertEquals(STORED_DATA_CLEANUP_ANDROID_ACTIVITY_EMPTY_PARENT_REMOVE_PATH, platformExpectations.objectValue("android").stringValue("activityEmptyParentRemovePath"))
        assertEquals(STORED_DATA_CLEANUP_IOS_SDLOGS_LIST_FAILURE, platformExpectations.objectValue("ios").stringValue("sdlogsListFailure"))
        assertEquals(STORED_DATA_CLEANUP_IOS_TELEMETRY_LIST_FAILURE, platformExpectations.objectValue("ios").stringValue("telemetryListFailure"))
        assertEquals(STORED_DATA_CLEANUP_IOS_ACTIVITY_EMPTY_PARENT_REMOVE_PATH, platformExpectations.objectValue("ios").stringValue("activityEmptyParentRemovePath"))
        assertEquals(STORED_DATA_CLEANUP_PLATFORM_SPLIT_DECISION, platformExpectations.stringValue("commonDecision"))
        assertEquals(STORED_DATA_CLEANUP_POLICY_COMMON_DECISION, vector.stringValue("commonDecision"))
    }

    @Test
    fun cleanupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/stored-data-cleanup/cleanup-workflow-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("stored-data-cleanup-workflow-readiness", manifest.stringValue("id"))
        assertEquals("storedDataCleanupWorkflowReadiness", input.stringValue("kind"))
        assertEquals("sdk/stored-data-cleanup/cleanup-workflow-policy.json", policyVectorPath)
        assertEquals(requiredCleanupFamilies, requiredFamilies)
        assertEquals(requiredCleanupFamilies, coveredFamilies)
        assertEquals("stored-data-cleanup-workflow-policy", policy.stringValue("id"))
        assertEquals("cleanup_workflow_policy", policy.stringValue("case"))
        assertEquals(requiredCleanupScenarioIds, policy.objectValue("input").objectArray("scenarios").map { it.stringValue("id") })
        assertEquals(requiredCleanupScenarioIds, policy.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").map { it.stringValue("id") })
        assertCleanupPolicyFields(policy.objectValue("input").objectArray("scenarios").associateBy { it.stringValue("id") })
        assertEquals(STORED_DATA_CLEANUP_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.StoredDataCleanupRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    @Test
    fun cleanupFilterHelpersExposeProductionSharedPolicy() {
        assertEquals(true, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("TRC10.BIN", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        assertEquals(false, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("ABC10.BIN", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        assertEquals(false, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("TRC10.TXT", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        assertEquals(true, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("A.SLG", includeSuffixes = listOf(".SLG", ".TXT")))
        assertEquals(true, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("B.TXT", includeSuffixes = listOf(".SLG", ".TXT")))
        assertEquals(false, PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter("C.BPB", includeSuffixes = listOf(".SLG", ".TXT")))
        assertEquals(true, PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents("ACT"))
        assertEquals(false, PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents("AUTOS"))
        assertEquals(false, PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents("SDLOGS"))
        assertEquals(false, PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents("UNDEFINED"))
    }

    private val requiredCleanupScenarioIds = listOf(
        "telemetry-root-trc-bin-filter",
        "sdlogs-extension-filter",
        "activity-prune-empty-parents",
        "automatic-sample-embedded-day-filter",
        "sdlogs-list-failure-platform-policy",
        "telemetry-list-failure-platform-policy"
    )

    private val requiredCleanupFamilies = listOf(
        "telemetry-trc-filter",
        "sdlogs-extension-filter",
        "activity-prune-empty-parents",
        "automatic-sample-embedded-day-filter",
        "list-failure-platform-split",
        "empty-parent-path-platform-split",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val STORED_DATA_CLEANUP_ANDROID_SDLOGS_LIST_FAILURE = "swallows list failure after GET:/SDLOGS/"
    private val STORED_DATA_CLEANUP_ANDROID_TELEMETRY_LIST_FAILURE = "swallows list failure after GET:/"
    private val STORED_DATA_CLEANUP_ANDROID_ACTIVITY_EMPTY_PARENT_REMOVE_PATH = "removes empty parent directories without a trailing slash"
    private val STORED_DATA_CLEANUP_IOS_SDLOGS_LIST_FAILURE = "propagates list failure after GET:/SDLOGS/"
    private val STORED_DATA_CLEANUP_IOS_TELEMETRY_LIST_FAILURE = "propagates list failure after GET:/"
    private val STORED_DATA_CLEANUP_IOS_ACTIVITY_EMPTY_PARENT_REMOVE_PATH = "removes empty parent directories with a trailing slash"
    private val STORED_DATA_CLEANUP_PLATFORM_SPLIT_DECISION = "Keep cleanup failure and empty-directory path policy platform-specific until public facade compatibility is resolved."
    private val STORED_DATA_CLEANUP_POLICY_COMMON_DECISION = "Promote cleanup traversal and filtering before platform-specific public error/path adapters; do not normalize Android/iOS cleanup failure behavior implicitly."

    private val STORED_DATA_CLEANUP_READINESS_COMMON_DECISION = "Stored-data cleanup migration may proceed only after cleanup-workflow-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, cleanup list-failure and empty-parent remove-path splits are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified."

    private fun assertCleanupPolicyFields(scenariosById: Map<String, String>) {
        val telemetry = scenariosById.getValue("telemetry-root-trc-bin-filter")
        assertEquals("/", telemetry.stringValue("rootPath"))
        assertEquals(listOf("TRC"), telemetry.stringArrayValue("includePrefixes"))
        assertEquals(listOf(".BIN"), telemetry.stringArrayValue("includeSuffixes"))
        assertEquals(listOf("TRC10.BIN", "ABC10.BIN", "TRC10.TXT"), telemetry.stringArrayValue("entries"))
        val sdlogs = scenariosById.getValue("sdlogs-extension-filter")
        assertEquals("/SDLOGS/", sdlogs.stringValue("rootPath"))
        assertEquals(listOf(".SLG", ".TXT"), sdlogs.stringArrayValue("includeSuffixes"))
        assertEquals(listOf("A.SLG", "B.TXT", "C.BPB"), sdlogs.stringArrayValue("entries"))
        val activity = scenariosById.getValue("activity-prune-empty-parents")
        assertEquals("2026-05-31", activity.stringValue("cutoffDate"))
        val activityDateFolder = activity.objectArray("dateFolders").single()
        assertEquals("/U/0/20260530/", activityDateFolder.stringValue("path"))
        assertEquals(listOf("ACTIVITY.BPB", "HIST.BPB"), activityDateFolder.stringArrayValue("activityFiles"))
        assertEquals(listOf("ACTIVITY.BPB"), activityDateFolder.stringArrayValue("removeFiles"))
        val automaticSample = scenariosById.getValue("automatic-sample-embedded-day-filter")
        assertEquals("2026-05-31", automaticSample.stringValue("cutoffDate"))
        val sampleFiles = automaticSample.objectArray("sampleFiles")
        assertEquals("/U/0/AUTOS/20260530/AUTOS001.BPB", sampleFiles[0].stringValue("path"))
        assertEquals("2026-05-30", sampleFiles[0].stringValue("embeddedDay"))
        assertEquals("/U/0/AUTOS/20260601/AUTOS002.BPB", sampleFiles[1].stringValue("path"))
        assertEquals("2026-06-01", sampleFiles[1].stringValue("embeddedDay"))
        assertEquals("/SDLOGS/", scenariosById.getValue("sdlogs-list-failure-platform-policy").stringValue("rootPath"))
        assertEquals("/", scenariosById.getValue("telemetry-list-failure-platform-policy").stringValue("rootPath"))
    }

    private class FakeStoredDataCleanupRuntime {
        fun run(scenario: CleanupScenario): CleanupRuntimeOutcome {
            return when (scenario.kind) {
                "filterDirectoryEntries" -> CleanupRuntimeOutcome(filterDirectoryEntries(scenario), "success")
                "activityPrune" -> CleanupRuntimeOutcome(activityPrune(scenario), "platform-path-split")
                "automaticSamplePrune" -> CleanupRuntimeOutcome(automaticSamplePrune(scenario), "success")
                "listFailure" -> CleanupRuntimeOutcome(listOf("GET:${requireNotNull(scenario.rootPath)}"), "platform-split")
                else -> error("Unsupported cleanup scenario ${scenario.kind}")
            }
        }

        private fun filterDirectoryEntries(scenario: CleanupScenario): List<String> {
            val root = requireNotNull(scenario.rootPath)
            val commands = mutableListOf("GET:$root")
            scenario.entries
                .filter { entry ->
                    (scenario.includePrefixes.isEmpty() || scenario.includePrefixes.any { prefix -> entry.startsWith(prefix) }) &&
                        (scenario.includeSuffixes.isEmpty() || scenario.includeSuffixes.any { suffix -> entry.endsWith(suffix) })
                }
                .mapTo(commands) { entry -> "REMOVE:${root.withTrailingSlash()}$entry" }
            return commands
        }

        private fun activityPrune(scenario: CleanupScenario): List<String> {
            val commands = mutableListOf("GET:/U/0/")
            scenario.dateFolders
                .filter { folder -> folder.booleanValue("beforeCutoff") }
                .forEach { folder ->
                    val folderPath = folder.stringValue("path")
                    val actPath = "${folderPath}ACT/"
                    commands += "GET:$folderPath"
                    commands += "GET:$actPath"
                    folder.stringArrayValue("removeFiles").forEach { fileName ->
                        commands += "REMOVE:$actPath$fileName"
                    }
                    if (folder.booleanValue("pruneEmptyParents")) {
                        commands += "GET:$actPath"
                        commands += "REMOVE_EMPTY_DIRECTORY:${actPath.removeSuffix("/")}"
                        commands += "GET:$folderPath"
                        commands += "REMOVE_EMPTY_DIRECTORY:${folderPath.removeSuffix("/")}"
                    }
                }
            return commands
        }

        private fun automaticSamplePrune(scenario: CleanupScenario): List<String> {
            val cutoff = requireNotNull(scenario.cutoffDate)
            val commands = mutableListOf("GET:/U/0/AUTOS/")
            scenario.sampleFiles.forEach { sample ->
                val path = sample.stringValue("path")
                val parent = path.substringBeforeLast('/') + "/"
                commands += "GET:$parent"
                commands += "GET:$path"
                if (sample.stringValue("embeddedDay") < cutoff) {
                    commands += "REMOVE:$path"
                }
            }
            return commands
        }
    }

    private data class CleanupScenario(
        val id: String,
        val kind: String,
        val rootPath: String?,
        val includePrefixes: List<String>,
        val includeSuffixes: List<String>,
        val entries: List<String>,
        val cutoffDate: String?,
        val dateFolders: List<String>,
        val sampleFiles: List<String>
    )

    private data class CleanupRuntimeOutcome(
        val commands: List<String>,
        val terminal: String
    )

    private fun String.optionalObjectArray(field: String): List<String> {
        return if (contains("\"$field\"")) objectArray(field) else emptyList()
    }

    private fun String.withTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
