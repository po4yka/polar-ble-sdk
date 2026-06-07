
///  Copyright © 2024 Polar. All rights reserved.

import XCTest

@testable import PolarBleSdk

class PolarDeviceRestApiServiceTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testConvertsServicesListExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "services": {
             "ui_states":"/REST/UISTATES.API",
             "training":"/REST/TRAINING.API",
            }
        }
        """
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: jsonData.data(using: .utf8)!)
        
        // Assert
        XCTAssertEqual(result.serviceNames.count, 2)
        XCTAssertTrue(result.serviceNames.contains("ui_states"))
        XCTAssertTrue(result.serviceNames.contains("training"))
        
        XCTAssertEqual(result.servicePaths.count, 2)
        XCTAssertTrue(result.servicePaths.contains("/REST/UISTATES.API"))
        XCTAssertTrue(result.servicePaths.contains("/REST/TRAINING.API"))
        
        XCTAssertEqual(result.pathsForServices?["ui_states"], "/REST/UISTATES.API")
        XCTAssertEqual(result.pathsForServices?["training"], "/REST/TRAINING.API")
    }

    func testRestServiceProjectionUsesSharedKmpWhenLinked() throws {
        let serviceJson = #"{"services":{"ui_states":"/REST/UISTATES.API","training":"/REST/TRAINING.API"}}"#
        let serviceList = try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: try XCTUnwrap(serviceJson.data(using: .utf8)))

        XCTAssertEqual(Set(["ui_states", "training"]), Set(serviceList.serviceNames))
        XCTAssertEqual(Set(["/REST/UISTATES.API", "/REST/TRAINING.API"]), Set(serviceList.servicePaths))

        let descriptionJson = #"{"events":["lap_data"],"cmd":{"subscribe":"./REST/TRAINING.API?cmd=subscribe&event="},"lap_data":{"details":["sport","duration"],"triggers":["manual"]}}"#
        let description = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: try XCTUnwrap(descriptionJson.data(using: .utf8)))
        XCTAssertEqual(["subscribe"], description.actionNames)
        XCTAssertEqual(["./REST/TRAINING.API?cmd=subscribe&event="], description.actionPaths)
        XCTAssertEqual(["sport", "duration"], description.eventDetails(for: "lap_data"))
        XCTAssertEqual(["manual"], description.eventTriggers(for: "lap_data"))
    }
    
    func testConvertsLapSummaryExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["lap_data","exercise_summary"],
            "cmd" : {
                "subscribe"   : "./REST/TRAINING.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]",
                "unsubscribe" : "./REST/TRAINING.API?cmd=unsubscribe&event="
            },
           "lap_data": {
               "details": ["lap_hr_bpm_avg","lap_speed_avg","lap_time"],
               "triggers": ["default","distance","time","end_of_music_track"]
           },
           "exercise_summary": {
               "details": ["duration","distance","hr_bpm_avg"]
           },
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 2)
        XCTAssertEqual(result.events.first, "lap_data")
        XCTAssertEqual(result.events.last, "exercise_summary")
        
        XCTAssertEqual(result.actions.count, 2)
        XCTAssertEqual(result.actionNames.count, 2)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        
        XCTAssertEqual(result.actionPaths.count, 2)
        XCTAssertTrue(result.actionPaths.contains( "./REST/TRAINING.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]"))
        XCTAssertTrue(result.actionPaths.contains("./REST/TRAINING.API?cmd=unsubscribe&event="))
        
        let lapDataEventDetails = result.eventDetails(for: "lap_data")
        XCTAssertEqual(lapDataEventDetails.count, 3)
        XCTAssertTrue(lapDataEventDetails.contains("lap_hr_bpm_avg"))
        XCTAssertTrue(lapDataEventDetails.contains("lap_time"))
        XCTAssertTrue(lapDataEventDetails.contains("lap_speed_avg"))
        
        let lapDAtaEventTriggers = result.eventTriggers(for: "lap_data")
        XCTAssertEqual(lapDAtaEventTriggers.count, 4)
        XCTAssertTrue(lapDAtaEventTriggers.contains("default"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("end_of_music_track"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("distance"))
        XCTAssertTrue(lapDAtaEventTriggers.contains("time"))
        
        let exerciseSummaryEventDetails = result.eventDetails(for: "exercise_summary")
        XCTAssertEqual(exerciseSummaryEventDetails.count, 3)
        XCTAssertTrue(exerciseSummaryEventDetails.contains("duration"))
        XCTAssertTrue(exerciseSummaryEventDetails.contains("hr_bpm_avg"))
        XCTAssertTrue(exerciseSummaryEventDetails.contains("distance"))
        
        let exerciseSummaryEventTriggers = result.eventTriggers(for: "exercise_summary")
        XCTAssertEqual(exerciseSummaryEventTriggers.count, 0)
    }
    
    func testConvertsDeviceUIStatesExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["training_app_state"],
            "cmd" : {
                "subscribe"   : "./REST/UISTATES.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]",
                "unsubscribe" : "./REST/UISTATES.API?cmd=unsubscribe&event="
            },
            "training_app_state": {
                "details": ["state", "sport_id"]
            }
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 1)
        XCTAssertEqual(result.events.first, "training_app_state")
        
        XCTAssertEqual(result.actions.count, 2)
        XCTAssertEqual(result.actionNames.count, 2)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        
        XCTAssertEqual(result.actionPaths.count, 2)
        XCTAssertTrue(result.actionPaths.contains( "./REST/UISTATES.API?cmd=subscribe&event=&resend=&details=[]&triggers=[]"))
        XCTAssertTrue(result.actionPaths.contains("./REST/UISTATES.API?cmd=unsubscribe&event="))
        
        let lapDataEventDetails = result.eventDetails(for: "training_app_state")
        XCTAssertEqual(lapDataEventDetails.count, 2)
        XCTAssertTrue(lapDataEventDetails.contains("state"))
        XCTAssertTrue(lapDataEventDetails.contains("sport_id"))
    }
    
    func testConvertsSleepAPIDescriptionExampleJson() throws {
        
        // Arrange
        let jsonData = """
        {
            "events": ["sleep_recording_state"],
            "endpoints": ["stop_sleep_recording"],
            "cmd": {
                "subscribe"   : "./REST/SLEEP.API?cmd=subscribe&event=&details=[]",
                "unsubscribe" : "./REST/SLEEP.API?cmd=unsubscribe&event=",
                "post"  : "./REST/SLEEP.API?cmd=post&endpoint="
            },
            "sleep_recording_state": {
                "details": ["enabled"]
            },
           "stop_sleep_recording": {
           }
        }
        """.data(using: .utf8)!
        
        // Act
        let result = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        
        // Assert
        XCTAssertEqual(result.events.count, 1)
        XCTAssertTrue(result.events.contains("sleep_recording_state"))
        XCTAssertEqual(result.endpoints.count, 1)
        XCTAssertTrue(result.endpoints.contains("stop_sleep_recording"))
        XCTAssertEqual(result.actions.count, 3)
        XCTAssertTrue(result.actionNames.contains("subscribe"))
        XCTAssertTrue(result.actionNames.contains("unsubscribe"))
        XCTAssertTrue(result.actionNames.contains("post"))
        XCTAssertEqual(result.actions["subscribe"], "./REST/SLEEP.API?cmd=subscribe&event=&details=[]")
        XCTAssertEqual(result.actions["unsubscribe"], "./REST/SLEEP.API?cmd=unsubscribe&event=")
        XCTAssertEqual(result.actions["post"], "./REST/SLEEP.API?cmd=post&endpoint=")
        let sleepRecordingStateDetails = result.eventDetails(for: "sleep_recording_state")
        XCTAssertEqual(sleepRecordingStateDetails.count, 1)
        XCTAssertTrue(sleepRecordingStateDetails.contains("enabled"))
        let sleepRecordingStateTriggers = result.eventTriggers(for: "sleep_recording_state")
        XCTAssertTrue(sleepRecordingStateTriggers.isEmpty)
        let stopSleepRecordingDetails = result.eventDetails(for: "stop_sleep_recording")
        XCTAssertTrue(stopSleepRecordingDetails.isEmpty)
        let stopSleepRecordingTriggers = result.eventTriggers(for: "stop_sleep_recording")
        XCTAssertTrue(stopSleepRecordingTriggers.isEmpty)
    }
    
    // Helpers
    
    let restApiEventNotifiationId = Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue
    
    func testNotificationParameters(compressed: Bool) -> [Data] {
        
        let jsonDataSleepRecordingStateEnabledOne = """
        {
            "sleep_recording_state": {
                "enabled": 1
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledZero = """
        {
            "sleep_recording_state": {
                "enabled": 0
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledTrue = """
        {
            "sleep_recording_state": {
                "enabled": true
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledFalse = """
        {
            "sleep_recording_state": {
                "enabled": false
            }
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateEnabledMissing = """
        {
            "sleep_recording_state": {}
        }
        """.data(using: .utf8)!
        
        let jsonDataSleepRecordingStateMissing = """
        {}
        """.data(using: .utf8)!
        
        let jsonDataEmpty = "".data(using: .utf8)!
        
        var notificationParameters = [
           jsonDataSleepRecordingStateEnabledOne,
           jsonDataSleepRecordingStateEnabledZero,
           jsonDataSleepRecordingStateEnabledTrue,
           jsonDataSleepRecordingStateEnabledFalse,
           jsonDataSleepRecordingStateEnabledMissing,
           jsonDataSleepRecordingStateMissing,
           jsonDataEmpty
        ]
        
        if compressed {
            notificationParameters = notificationParameters.map {
                $0.deflated(512) ?? $0
            }
        }
        
        return notificationParameters
    }
    
    
    func testReceivesRestApiEventWhenUncompressed() async throws {
        
        // Arrange
        let notificationParameters = self.testNotificationParameters(compressed: false).map { $0.isEmpty ? [] : [$0] }
        let notifications = self.testNotificationParameters(compressed: false).map {
            (self.restApiEventNotifiationId, [$0], false)
        }
        
        mockClient.receiveNotificationCalls.append(contentsOf: notifications)

        // Act
        var result: [[Data]] = []
        for try await batch in mockClient.receiveRestApiEventData(identifier: UUID().uuidString) {
            result.append(batch)
        }
        
        // Assert
        XCTAssertFalse(result.isEmpty)
        XCTAssertEqual(result, notificationParameters)
    }
    
    func testReceivesRestApiEventWhenCompressed() async throws {
        
        // Arrange
        let notificationParameters = self.testNotificationParameters(compressed: false).map { [$0] }
        let notificationsWithCompressedData = self.testNotificationParameters(compressed: true).map {
            (self.restApiEventNotifiationId, [$0], true)
        }
        let notificationParametersCompressed = notificationsWithCompressedData.map { $0.1 }
        
        mockClient.receiveNotificationCalls.append(contentsOf: notificationsWithCompressedData)

        // Act
        var result: [[Data]] = []
        for try await batch in mockClient.receiveRestApiEventData(identifier: UUID().uuidString) {
            result.append(batch)
        }
        
        // Assert
        
        // Make sure mock data was compressed:
        XCTAssertEqual(notificationParameters.count, notificationParametersCompressed.count)
        XCTAssertNotEqual(notificationParameters, notificationParametersCompressed)
        
        // Check that result received expected uncompressed values
        XCTAssertFalse(result.isEmpty)
        XCTAssertEqual(result, notificationParameters)
    }

    func testReceivesRestApiEventUsesSharedD2hPlannerToSelectRestEvents() async throws {
        let payload = #"{"path":"/v1/users","operation":"created"}"#.data(using: .utf8)!
        XCTAssertEqual("REST_API_EVENT", PolarRuntimePlanner.d2hNotificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue))
        XCTAssertEqual("FILESYSTEM_MODIFIED", PolarRuntimePlanner.d2hNotificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.filesystemModified.rawValue))
        mockClient.receiveNotificationCalls.append((Protocol_PbPFtpDevToHostNotification.filesystemModified.rawValue, [Data([0x0a, 0x02, 0x08, 0x02])], false))
        mockClient.receiveNotificationCalls.append((restApiEventNotifiationId, [payload], false))

        var result: [[Data]] = []
        for try await batch in mockClient.receiveRestApiEventData(identifier: UUID().uuidString) {
            result.append(batch)
        }

        XCTAssertEqual([[payload]], result)
    }

    func testRestServiceGoldenVectorsMapJsonToPublicModels() throws {
        let vectors = try loadRestServiceGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected REST service golden vectors")

        for vector in vectors {
            let caseId = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], caseId)
            let kind = try XCTUnwrap(input["kind"] as? String, caseId)

            switch kind {
            case "serviceList":
                let jsonObject = try XCTUnwrap(input["json"] as? [String: Any], caseId)
                let data = try JSONSerialization.data(withJSONObject: jsonObject)
                let expected = try XCTUnwrap(vector["expected"] as? [String: Any], caseId)
                if let iosExpected = expected["ios"] as? [String: Any],
                   let decodeError = iosExpected["decodeError"] as? Bool,
                   decodeError {
                    XCTAssertThrowsError(try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: data), caseId)
                    continue
                }
                let model = try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: data)
                try assertServiceList(model, expected: iosExpected(expected), id: caseId)
            case "serviceDescription":
                let jsonObject = try XCTUnwrap(input["json"] as? [String: Any], caseId)
                let data = try JSONSerialization.data(withJSONObject: jsonObject)
                let expected = try XCTUnwrap(vector["expected"] as? [String: Any], caseId)
                let model = try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: data)
                try assertServiceDescription(model, expected: expected, id: caseId)
            case "restEventCompression":
                continue
            case "restRequestTransportPolicy":
                continue
            case "restRequestTransportReadiness":
                continue
            case "restFacadeRuntimePolicy":
                continue
            case "restFacadeRuntimeReadiness":
                continue
            case "restEventCompressionReadiness":
                continue
            case "restServiceMappingReadiness":
                continue
            default:
                XCTFail("Unknown REST service vector kind for \(caseId)")
            }
        }
    }

    func testRestEventCompressionGoldenVectorsPreserveIOSPolicy() async throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { (($0["input"] as? [String: Any])?["kind"] as? String) == "restEventCompression" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        for testCase in try XCTUnwrap(input["cases"] as? [[String: Any]]) {
            let id = try XCTUnwrap(testCase["id"] as? String)
            if testCase["expected"] != nil {
                continue
            }
            let payloads = try XCTUnwrap(testCase["payloads"] as? [String]).map { Data($0.utf8) }
            let uncompressed = try XCTUnwrap(testCase["uncompressed"] as? Bool)
            let notificationPayloads = uncompressed ? payloads : payloads.map { payload in
                payload.deflated(512) ?? payload
            }
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.receiveNotificationCalls.append((restApiEventNotifiationId, notificationPayloads, !uncompressed))

            var result: [[Data]] = []
            for try await batch in mockClient.receiveRestApiEventData(identifier: UUID().uuidString) {
                result.append(batch)
            }

            XCTAssertEqual(result, [payloads], id)
        }
    }

    func testRestEventMalformedCompressionGoldenVectorsPreserveIOSPolicy() async throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { (($0["input"] as? [String: Any])?["kind"] as? String) == "restEventCompression" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let testCase = try XCTUnwrap((try XCTUnwrap(input["cases"] as? [[String: Any]])).first { ($0["id"] as? String) == "malformed-compressed-payload" })
        let payloads = try XCTUnwrap(testCase["payloads"] as? [String]).map { Data($0.utf8) }
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockClient.receiveNotificationCalls.append((restApiEventNotifiationId, payloads, true))

        var result: [[Data]] = []
        for try await batch in mockClient.receiveRestApiEventData(identifier: UUID().uuidString) {
            result.append(batch)
        }

        XCTAssertEqual(result, [payloads])
    }

    func testRestServiceGoldenVectorsFollowNeutralKmpShape() throws {
        let vectors = try loadRestServiceGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected REST service golden vectors")
        for vector in vectors {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testRestRequestTransportPolicyVectorIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { ($0["id"] as? String) == "rest-request-transport-policy" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requests = try XCTUnwrap(input["requests"] as? [[String: Any]], "rest-request-transport-policy")
        let requestIds = requests.compactMap { $0["id"] as? String }
        let expectedCases = try XCTUnwrap((expected["commonRuntimePrototype"] as? [String: Any])?["cases"] as? [[String: Any]], "rest-request-transport-policy")
        let expectedCaseIds = expectedCases.compactMap { $0["id"] as? String }

        XCTAssertNotNil(vector["execution"], "rest-request-transport-policy")
        XCTAssertEqual(requestIds, REST_REQUEST_TRANSPORT_POLICY_CASE_IDS, "rest-request-transport-policy")
        XCTAssertEqual(expectedCaseIds, REST_REQUEST_TRANSPORT_POLICY_CASE_IDS, "rest-request-transport-policy")
        XCTAssertEqual(expected["migrationRequirement"] as? String, REST_REQUEST_TRANSPORT_MIGRATION_REQUIREMENT, "rest-request-transport-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "rest-request-transport-policy"), ["com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "rest-request-transport-policy"), ["PolarDeviceRestApiTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "rest-request-transport-policy"), ["com.polar.sharedtest.RestRequestTransportPolicyCommonTest"])
    }

    func testRestRequestTransportReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { ($0["id"] as? String) == "rest-request-transport-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])

        XCTAssertEqual(vector["id"] as? String, "rest-request-transport-readiness")
        XCTAssertEqual(input["kind"] as? String, "restRequestTransportReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/rest-service/rest-request-transport-policy.json")
        XCTAssertEqual(REST_REQUEST_TRANSPORT_READINESS_FAMILIES, requiredFamilies, "rest-request-transport-readiness")
        XCTAssertEqual(REST_REQUEST_TRANSPORT_READINESS_FAMILIES, coveredFamilies, "rest-request-transport-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, REST_REQUEST_TRANSPORT_READINESS_COMMON_DECISION, "rest-request-transport-readiness")
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "rest-request-transport-readiness")
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest runtime planning guard", "rest-request-transport-readiness")
        XCTAssertEqual(commonRuntimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", "rest-request-transport-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "rest-request-transport-readiness"), ["com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "rest-request-transport-readiness"), ["PolarDeviceRestApiTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "rest-request-transport-readiness"), ["com.polar.sharedtest.RestRequestTransportPolicyCommonTest"])
    }

    func testRestEventCompressionReadinessManifestIsPinnedBeforeCodecMigration() throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { ($0["id"] as? String) == "rest-event-compression-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])

        XCTAssertEqual(vector["id"] as? String, "rest-event-compression-readiness")
        XCTAssertEqual(input["kind"] as? String, "restEventCompressionReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/rest-service/rest-event-compression-platform-policy.json")
        XCTAssertEqual(REST_EVENT_COMPRESSION_READINESS_FAMILIES, requiredFamilies, "rest-event-compression-readiness")
        XCTAssertEqual(REST_EVENT_COMPRESSION_READINESS_FAMILIES, coveredFamilies, "rest-event-compression-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, REST_EVENT_COMPRESSION_READINESS_COMMON_DECISION, "rest-event-compression-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "rest-event-compression-readiness"), ["com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "rest-event-compression-readiness"), ["PolarDeviceRestApiServiceTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "rest-event-compression-readiness"), ["com.polar.sharedtest.RestEventCompressionPolicyCommonTest"])
    }

    func testRestServiceMappingReadinessManifestIsPinnedBeforeModelMigration() throws {
        let vector = try XCTUnwrap(try loadRestServiceGoldenVectors().first { ($0["id"] as? String) == "rest-service-mapping-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "restServiceMappingReadiness")
        XCTAssertEqual(expected["migrationReadiness"] as? String, "compileVerifiedPreMigrationCharacterization")
        XCTAssertEqual(REST_SERVICE_MAPPING_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "rest-service-mapping-readiness")
        XCTAssertEqual(REST_SERVICE_MAPPING_READINESS_FAMILIES, requiredFamilies, "rest-service-mapping-readiness")
        XCTAssertEqual(REST_SERVICE_MAPPING_READINESS_FAMILIES, coveredFamilies, "rest-service-mapping-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "rest-service-mapping-readiness"), ["com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "rest-service-mapping-readiness"), ["PolarDeviceRestApiServiceTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "rest-service-mapping-readiness"), ["com.polar.sharedtest.RestServiceMappingCommonPolicyTest"])
    }

    private func assertServiceList(_ actual: PolarDeviceRestApiServices, expected: [String: Any], id: String) throws {
        if expected["pathsForServices"] is NSNull {
            XCTAssertNil(actual.pathsForServices, id)
        } else {
            let expectedPaths = try XCTUnwrap(expected["pathsForServices"] as? [String: String], id)
            XCTAssertEqual(actual.pathsForServices, expectedPaths, id)
        }
        XCTAssertEqual(Set(actual.serviceNames), Set(try XCTUnwrap(expected["serviceNames"] as? [String], id)), id)
        XCTAssertEqual(Set(actual.servicePaths), Set(try XCTUnwrap(expected["servicePaths"] as? [String], id)), id)
    }

    private func assertServiceDescription(_ actual: PolarDeviceRestApiServiceDescription, expected: [String: Any], id: String) throws {
        let expectedActions = try XCTUnwrap(expected["actions"] as? [String: String], id)
        XCTAssertEqual(actual.events, try XCTUnwrap(expected["events"] as? [String], id), id)
        XCTAssertEqual(actual.endpoints, try XCTUnwrap(expected["endpoints"] as? [String], id), id)
        XCTAssertEqual(actual.actions, expectedActions, id)
        XCTAssertEqual(Set(actual.actionNames), Set(expectedActions.keys), id)
        XCTAssertEqual(Set(actual.actionPaths), Set(expectedActions.values), id)
        let details = try XCTUnwrap(expected["eventDetails"] as? [String: [String]], id)
        for (event, expectedDetails) in details {
            XCTAssertEqual(actual.eventDetails(for: event), expectedDetails, "\(id) details \(event)")
        }
        let triggers = try XCTUnwrap(expected["eventTriggers"] as? [String: [String]], id)
        for (event, expectedTriggers) in triggers {
            XCTAssertEqual(actual.eventTriggers(for: event), expectedTriggers, "\(id) triggers \(event)")
        }
    }

    private func iosExpected(_ expected: [String: Any]) -> [String: Any] {
        return expected["ios"] as? [String: Any] ?? expected
    }

    private func loadRestServiceGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/rest-service")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

}

private let REST_SERVICE_MAPPING_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/rest-service/service-list-basic.json",
    "sdk/rest-service/service-list-empty.json",
    "sdk/rest-service/service-description-training.json",
    "sdk/rest-service/service-description-empty.json",
    "sdk/rest-service/service-list-wrong-type-platform-policy.json"
]

private let REST_REQUEST_TRANSPORT_READINESS_FAMILIES = [
    "service-list-get-path",
    "service-description-get-path",
    "response-error-payload-status",
    "response-error-payload-message",
    "empty-successful-response-policy-gate",
    "fake-pftp-request-harness-gate",
    "facade-error-mapping-pinned",
    "platform-transport-vector-reference-gate",
    "compile-verification-gate"
]

private let REST_REQUEST_TRANSPORT_POLICY_CASE_IDS = [
    "service-list-request-error-payload",
    "service-description-request-error-payload",
    "service-list-empty-transport-response",
    "service-description-empty-transport-response"
]

private let REST_REQUEST_TRANSPORT_MIGRATION_REQUIREMENT = "Before moving REST request orchestration into common KMP code, implement a fake PFTP request harness that can inject response-error payloads and byte-for-byte empty successful responses for service discovery and service-description reads."

private let REST_REQUEST_TRANSPORT_READINESS_COMMON_DECISION = "REST request transport migration may proceed only after rest-request-transport-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS REST tests continue to reference the same vectors, service-list and service-description GET paths remain pinned, response-error status and message mapping stay covered, empty successful responses are deliberately normalized or deliberately preserved as platform facade behavior, public facade error mapping stays pinned through rest-facade-runtime-policy.json, and the shared tests are compile-verified."

private let REST_EVENT_COMPRESSION_READINESS_FAMILIES = [
    "uncompressed-batch-payload-preservation",
    "empty-uncompressed-batch-emission",
    "compressed-batch-platform-codec-split",
    "android-gzip-codec-reference-gate",
    "ios-deflate-codec-reference-gate",
    "malformed-compressed-payload-platform-split",
    "notification-payload-order-gate",
    "shared-platform-actual-codec-gate",
    "platform-event-vector-reference-gate",
    "compile-verification-gate"
]

private let REST_EVENT_COMPRESSION_READINESS_COMMON_DECISION = "REST event compression migration may proceed only after rest-event-compression-platform-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS event tests continue to reference the same vectors, uncompressed and empty batches preserve current payload semantics, Android gzip and iOS deflate behavior are preserved through shared KMP platform actual codecs, malformed compressed payload handling remains explicit for both platforms, notification payload order is pinned, and the shared tests are compile-verified."

private let REST_SERVICE_MAPPING_READINESS_FAMILIES = [
    "service-list-name-path-mapping",
    "service-list-empty-defaults",
    "service-description-action-event-mapping",
    "service-description-empty-defaults",
    "wrong-type-services-platform-split",
    "unknown-field-ignore-policy",
    "platform-rest-service-vector-references",
    "compile-verification-gate"
]
