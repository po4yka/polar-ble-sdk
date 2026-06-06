package com.polar.sharedtest

import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.runtime.PolarStoredDataCleanupDateFolder
import com.polar.shared.runtime.PolarStoredDataCleanupSampleFile
import com.polar.shared.runtime.PolarStoredDataCleanupScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertEquals("stored-data-cleanup-workflow-policy", vector.stringValue("id"))
        assertEquals("cleanup_workflow_policy", vector.stringValue("case"))
        assertEquals(requiredCleanupScenarioIds, scenarios.map { it.id })
        assertEquals(requiredCleanupScenarioIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-cleanup-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("directory-list-and-remove-command-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals("platform-path-split", expectedCases.getValue("activity-prune-empty-parents").stringValue("terminal"))
        assertCleanupPolicyFields(scenarioList.associateBy { it.stringValue("id") })

        scenarios.forEach { scenario ->
            val outcome = PolarWorkflowRuntimePlanning.planStoredDataCleanup(scenario.toSharedScenario())
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
    fun cleanupWorkflowVectorRunsThroughCommonFakeTransportFacadeShape() {
        val vector = loadGoldenVectorText("sdk/stored-data-cleanup/cleanup-workflow-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        input.objectArray("scenarios").forEach { scenarioJson ->
            val scenario = CleanupScenario(
                id = scenarioJson.stringValue("id"),
                kind = scenarioJson.stringValue("kind"),
                rootPath = scenarioJson.optionalStringValue("rootPath"),
                includePrefixes = scenarioJson.optionalStringArrayValue("includePrefixes") ?: emptyList(),
                includeSuffixes = scenarioJson.optionalStringArrayValue("includeSuffixes") ?: emptyList(),
                entries = scenarioJson.optionalStringArrayValue("entries") ?: emptyList(),
                cutoffDate = scenarioJson.optionalStringValue("cutoffDate"),
                dateFolders = scenarioJson.optionalObjectArray("dateFolders"),
                sampleFiles = scenarioJson.optionalObjectArray("sampleFiles")
            )
            val planned = PolarWorkflowRuntimePlanning.planStoredDataCleanup(scenario.toSharedScenario())
            val expected = expectedCases.getValue(scenario.id)
            val transport = ScriptedCommonFakeTransport(outcomesForCleanupCommands(expected.stringArrayValue("commands"), expected.stringValue("terminal")))
            val terminal = executePlannedCleanup(planned.commands, expected.stringValue("terminal"), transport)

            assertEquals(expected.stringArrayValue("commands"), planned.commands, scenario.id)
            assertEquals(expected.stringValue("terminal"), terminal, scenario.id)
            assertEquals(expectedCleanupTransportCommands(expected.stringArrayValue("commands")), transport.commands, scenario.id)
        }
    }

    @Test
    fun cleanupWorkflowVectorRunsThroughCommonFakeTraversalRuntime() {
        val vector = loadGoldenVectorText("sdk/stored-data-cleanup/cleanup-workflow-policy.json")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }
        val fakeRuntime = CommonStoredDataCleanupFakeTraversalRuntime()

        vector.objectValue("input").objectArray("scenarios").forEach { scenarioJson ->
            val scenario = CleanupScenario(
                id = scenarioJson.stringValue("id"),
                kind = scenarioJson.stringValue("kind"),
                rootPath = scenarioJson.optionalStringValue("rootPath"),
                includePrefixes = scenarioJson.optionalStringArrayValue("includePrefixes") ?: emptyList(),
                includeSuffixes = scenarioJson.optionalStringArrayValue("includeSuffixes") ?: emptyList(),
                entries = scenarioJson.optionalStringArrayValue("entries") ?: emptyList(),
                cutoffDate = scenarioJson.optionalStringValue("cutoffDate"),
                dateFolders = scenarioJson.optionalObjectArray("dateFolders"),
                sampleFiles = scenarioJson.optionalObjectArray("sampleFiles")
            )
            val expected = expectedCases.getValue(scenario.id)
            val execution = fakeRuntime.execute(scenario)

            assertEquals(expected.stringArrayValue("commands"), execution.commands, scenario.id)
            assertEquals(expected.stringValue("terminal"), execution.terminal, scenario.id)
            assertEquals(expected.stringArrayValue("commands").filter { command -> command.startsWith("GET:") }, execution.readCommands, scenario.id)
            assertEquals(expected.stringArrayValue("commands").filter { command -> command.startsWith("REMOVE") }, execution.removeCommands, scenario.id)
            if (scenario.id == "activity-prune-empty-parents") {
                assertEquals(true, execution.removeCommands.contains("REMOVE_EMPTY_DIRECTORY:/U/0/20260530/ACT"))
            }
        }
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
        assertEquals(true, PolarWorkflowRuntimePlanning.storedDataDateIsOnOrBefore("2026-05-30", "2026-05-31"))
        assertEquals(true, PolarWorkflowRuntimePlanning.storedDataDateIsOnOrBefore("2026-05-31", "2026-05-31"))
        assertEquals(false, PolarWorkflowRuntimePlanning.storedDataDateIsOnOrBefore("2026-06-01", "2026-05-31"))
        assertEquals(
            listOf("/U/0/20260530/ACT", "/U/0/20260530"),
            PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories("/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash = false)
        )
        assertEquals(
            listOf("/U/0/20260530/ACT/", "/U/0/20260530/"),
            PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories("/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash = true)
        )
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

    private fun outcomesForCleanupCommands(commands: List<String>, terminal: String): List<CommonFakeTransportOutcome> {
        if (terminal == "platform-split") {
            return listOf(CommonFakeTransportOutcome.TransportError("cleanup-list-failed"))
        }
        return commands.map { command ->
            when {
                command.startsWith("GET:") -> CommonFakeTransportOutcome.Bytes(byteArrayOf(0x01))
                command.startsWith("REMOVE:") || command.startsWith("REMOVE_EMPTY_DIRECTORY:") -> CommonFakeTransportOutcome.Complete
                else -> error("Unsupported cleanup command $command")
            }
        }
    }

    private fun executePlannedCleanup(
        commands: List<String>,
        expectedTerminal: String,
        transport: ScriptedCommonFakeTransport
    ): String {
        commands.forEach { command ->
            when {
                command.startsWith("GET:") -> {
                    val outcome = transport.read(command.removePrefix("GET:"))
                    if (outcome is CommonFakeTransportOutcome.TransportError) {
                        return expectedTerminal
                    }
                    assertIs<CommonFakeTransportOutcome.Bytes>(outcome, command)
                }
                command.startsWith("REMOVE:") -> {
                    assertIs<CommonFakeTransportOutcome.Complete>(transport.remove(command.removePrefix("REMOVE:")), command)
                }
                command.startsWith("REMOVE_EMPTY_DIRECTORY:") -> {
                    assertIs<CommonFakeTransportOutcome.Complete>(transport.remove(command.removePrefix("REMOVE_EMPTY_DIRECTORY:")), command)
                }
            }
        }
        return expectedTerminal
    }

    private fun expectedCleanupTransportCommands(commands: List<String>): List<CommonFakeTransportCommand> {
        return commands.mapNotNull { command ->
            when {
                command.startsWith("GET:") -> CommonFakeTransportCommand(CommonFakeTransportOperation.READ, command.removePrefix("GET:"))
                command.startsWith("REMOVE:") -> CommonFakeTransportCommand(CommonFakeTransportOperation.REMOVE, command.removePrefix("REMOVE:"))
                command.startsWith("REMOVE_EMPTY_DIRECTORY:") -> CommonFakeTransportCommand(CommonFakeTransportOperation.REMOVE, command.removePrefix("REMOVE_EMPTY_DIRECTORY:"))
                else -> null
            }
        }
    }

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
    ) {
        fun toSharedScenario(): PolarStoredDataCleanupScenario {
            return PolarStoredDataCleanupScenario(
                id = id,
                kind = kind,
                rootPath = rootPath,
                includePrefixes = includePrefixes,
                includeSuffixes = includeSuffixes,
                entries = entries,
                cutoffDate = cutoffDate,
                dateFolders = dateFolders.map { folder ->
                    PolarStoredDataCleanupDateFolder(
                        path = folder.stringValue("path"),
                        beforeCutoff = folder.booleanValue("beforeCutoff"),
                        removeFiles = folder.stringArrayValue("removeFiles"),
                        pruneEmptyParents = folder.booleanValue("pruneEmptyParents")
                    )
                },
                sampleFiles = sampleFiles.map { sample ->
                    PolarStoredDataCleanupSampleFile(
                        path = sample.stringValue("path"),
                        embeddedDay = sample.stringValue("embeddedDay")
                    )
                }
            )
        }
    }

    private class CommonStoredDataCleanupFakeTraversalRuntime {
        fun execute(scenario: CleanupScenario): CleanupExecution {
            val commands = mutableListOf<String>()
            val terminal = when (scenario.kind) {
                "filterDirectoryEntries" -> {
                    val root = requireNotNull(scenario.rootPath)
                    listDirectory(commands, root)
                    scenario.entries
                        .filter { entry -> PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter(entry, scenario.includePrefixes, scenario.includeSuffixes) }
                        .forEach { entry -> remove(commands, root.withTrailingSlash() + entry) }
                    "success"
                }
                "activityPrune" -> {
                    listDirectory(commands, "/U/0/")
                    scenario.toSharedScenario().dateFolders
                        .filter { folder -> folder.beforeCutoff }
                        .forEach { folder ->
                            val activityPath = folder.path + "ACT/"
                            listDirectory(commands, folder.path)
                            listDirectory(commands, activityPath)
                            folder.removeFiles.forEach { fileName -> remove(commands, activityPath + fileName) }
                            if (folder.pruneEmptyParents) {
                                listDirectory(commands, activityPath)
                                PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories(activityPath + folder.removeFiles.first(), trailingSlash = false).forEach { emptyParent ->
                                    removeEmptyDirectory(commands, emptyParent)
                                    val parent = emptyParent.substringBeforeLast('/', missingDelimiterValue = "")
                                    listDirectory(commands, (if (parent.isEmpty()) "/" else parent).withTrailingSlash())
                                }
                                commands.removeLast()
                            }
                        }
                    "platform-path-split"
                }
                "automaticSamplePrune" -> {
                    listDirectory(commands, "/U/0/AUTOS/")
                    scenario.toSharedScenario().sampleFiles.forEach { sample ->
                        val parent = sample.path.substringBeforeLast('/') + "/"
                        listDirectory(commands, parent)
                        readFile(commands, sample.path)
                        if (sample.embeddedDay < requireNotNull(scenario.cutoffDate)) {
                            remove(commands, sample.path)
                        }
                    }
                    "success"
                }
                "listFailure" -> {
                    listDirectory(commands, requireNotNull(scenario.rootPath))
                    "platform-split"
                }
                else -> error("Unsupported cleanup scenario ${scenario.kind}")
            }
            return CleanupExecution(
                commands = commands,
                terminal = terminal,
                readCommands = commands.filter { command -> command.startsWith("GET:") },
                removeCommands = commands.filter { command -> command.startsWith("REMOVE") }
            )
        }

        private fun listDirectory(commands: MutableList<String>, path: String) {
            commands += "GET:$path"
        }

        private fun readFile(commands: MutableList<String>, path: String) {
            commands += "GET:$path"
        }

        private fun remove(commands: MutableList<String>, path: String) {
            commands += "REMOVE:$path"
        }

        private fun removeEmptyDirectory(commands: MutableList<String>, path: String) {
            commands += "REMOVE_EMPTY_DIRECTORY:$path"
        }

        private fun String.withTrailingSlash(): String {
            return if (endsWith("/")) this else "$this/"
        }
    }

    private data class CleanupExecution(
        val commands: List<String>,
        val terminal: String,
        val readCommands: List<String>,
        val removeCommands: List<String>
    )

    private fun String.optionalObjectArray(field: String): List<String> {
        return if (contains("\"$field\"")) objectArray(field) else emptyList()
    }

}
