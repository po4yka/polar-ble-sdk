//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth

private struct TimeoutError: Error {}

class BlePsFtpClientTest: XCTestCase {
    var blePsFtpClient: BlePsFtpClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        blePsFtpClient = BlePsFtpClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        blePsFtpClient.disconnected()
        mockGattServiceTransmitterImpl = nil
        blePsFtpClient = nil
    }

    // Helper – returns the first notification from the stream or throws TimeoutError if nothing arrives within `timeout` seconds.
    private func firstNotification(
        from stream: AsyncThrowingStream<BlePsFtpClient.PsFtpNotification, Error>,
        timeout: TimeInterval = 5.0
    ) async throws -> BlePsFtpClient.PsFtpNotification? {
        try await withThrowingTaskGroup(of: BlePsFtpClient.PsFtpNotification?.self) { group in
            group.addTask {
                var iter = stream.makeAsyncIterator()
                return try await iter.next()
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                throw TimeoutError()
            }
            do {
                let result = try await group.next()
                group.cancelAll()
                return result ?? nil
            } catch {
                group.cancelAll()
                throw error
            }
        }
    }

    private func notifications(
        from stream: AsyncThrowingStream<BlePsFtpClient.PsFtpNotification, Error>,
        count: Int,
        timeout: TimeInterval = 5.0
    ) async throws -> [BlePsFtpClient.PsFtpNotification] {
        try await withThrowingTaskGroup(of: [BlePsFtpClient.PsFtpNotification].self) { group in
            group.addTask {
                var iterator = stream.makeAsyncIterator()
                var result: [BlePsFtpClient.PsFtpNotification] = []
                while result.count < count {
                    guard let next = try await iterator.next() else {
                        break
                    }
                    result.append(next)
                }
                return result
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                throw TimeoutError()
            }
            do {
                let result = try await group.next()
                group.cancelAll()
                return result ?? []
            } catch {
                group.cancelAll()
                throw error
            }
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 single frame notification
    // THEN BLE PSFTP Service client emits the rfc76 payload to subscriber
    func testWaitNotificationSingleFrame() async throws {
        // Arrange
        let rfc76Header = Data([0x02])
        var rfc76Payload = Data()
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams = Data([0xFF, 0x00])
        rfc76Payload.append(psftpNotifcationId)
        rfc76Payload.append(psftpNotificationParams)
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        XCTAssertTrue(event!.parameters.isEqual(to: psftpNotificationParams))
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 multi frame notification
    // THEN BLE PSFTP Service client emits the combined payload from rfc76 notifications to subscriber
    func testWaitNotificationMultiFrame() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: 0)
        let event = try await firstNotification(from: blePsFtpClient.waitNotification())

        // Assert
        XCTAssertEqual(Int32(psftpNotifcationId[0]), event!.id)
        var expectedParameters = Data()
        expectedParameters.append(psftpNotificationParams1)
        expectedParameters.append(psftpNotificationParams2)
        XCTAssertTrue(event!.parameters.isEqual(to: expectedParameters))
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in first frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing and continue waiting
    func testWaitNotificationErrorInFirstFrame() async throws {
        // Arrange
        let rfc76Header = Data([0x00])
        let rfc76Payload = Data([0xFF, 0x00])
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(rfc76Payload)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act & Assert – stream should not emit within the timeout
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected: no notification emitted
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service sends rfc76 error notification in second frame
    // THEN BLE PSFTP Service client shall ignore the received rfc76 error frame, emit nothing and continue waiting
    func testWaitNotificationErrorInSecondFrame() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        // status = error (00b)
        let rfc76Header2 = Data([0x11])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        // Act & Assert – stream should not emit within the timeout
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: 0)
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification(), timeout: 1.0)
            XCTFail("Expected TimeoutError but got a notification")
        } catch is TimeoutError {
            // Expected: no notification emitted
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error packet (err != 0) in first packet
    // THEN BLE PSFTP Service client shall finish the stream with an error
    func testWaitNotificationErrorInFirstPackage() async throws {
        // Arrange
        let rfc76Header = Data([0x00])
        var notificationFromDevice = Data()
        notificationFromDevice.append(rfc76Header)
        notificationFromDevice.append(Data([0xFF, 0x00]))

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError = 1

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDevice, err: expectedError)

        // Assert
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification())
            XCTFail("Observable should fail instead of complete")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: expectedError) = error else {
                return XCTFail("Unexpected error code in \(error)")
            }
        }
    }

    // GIVEN that BLE PSFTP Service client is listening rfc76 notifications
    // WHEN BLE PSFTP service client receives error in second packet (err != 0)
    // THEN BLE PSFTP Service client shall finish the stream with an error
    func testWaitNotificationErrorInSecondPackage() async throws {
        // Arrange
        let psftpNotifcationId = Data([0x01])
        let psftpNotificationParams1 = Data([0xFF, 0x00])
        let psftpNotificationParams2 = Data([0xEF, 0xFE])

        let rfc76Header1 = Data([0x06])
        var rfc76Payload1 = Data()
        rfc76Payload1.append(psftpNotifcationId)
        rfc76Payload1.append(psftpNotificationParams1)

        let rfc76Header2 = Data([0x13])
        var rfc76Payload2 = Data()
        rfc76Payload2.append(psftpNotificationParams2)

        var notificationFromDeviceData1 = Data()
        notificationFromDeviceData1.append(rfc76Header1)
        notificationFromDeviceData1.append(rfc76Payload1)

        var notificationFromDeviceData2 = Data()
        notificationFromDeviceData2.append(rfc76Header2)
        notificationFromDeviceData2.append(rfc76Payload2)

        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        let expectedError = 255

        // Act
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData1, err: 0)
        blePsFtpClient.processServiceData(characteristic, data: notificationFromDeviceData2, err: expectedError)

        // Assert
        do {
            _ = try await firstNotification(from: blePsFtpClient.waitNotification())
            XCTFail("Observable should fail instead of complete")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: expectedError) = error else {
                return XCTFail("Unexpected error code in \(error)")
            }
        }
    }

    func testPsFtpNotificationGoldenVectorsReassembleCompleteNotifications() async throws {
        let vector = try loadPsFtpNotificationVector(id: "notification-reassembly")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        XCTAssertEqual(notificationReassemblyCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String) })

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let transmitter = MockGattServiceTransmitterImpl()
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            defer { client.disconnected() }

            for frameHex in try XCTUnwrap(testCase["framesHex"] as? [String], id) {
                client.processServiceData(characteristic, data: try dataFromHex(frameHex), err: 0)
            }
            let notification = try await firstNotification(from: client.waitNotification())
            let event = try XCTUnwrap(notification, id)
            let expected = try XCTUnwrap(testCase["expected"] as? [String: Any], id)
            XCTAssertEqual(event.id, Int32(try XCTUnwrap(expected["id"] as? Int, id)), id)
            XCTAssertEqual(event.parameters as Data, try dataFromHex(try XCTUnwrap(expected["parametersHex"] as? String, id)), id)
        }
    }

    func testPsFtpNotificationGoldenVectorsPreserveCompleteNotificationOrdering() async throws {
        let vector = try loadPsFtpNotificationVector(id: "notification-ordering")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        XCTAssertEqual(notificationOrderingCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String) })

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let transmitter = MockGattServiceTransmitterImpl()
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            defer { client.disconnected() }

            for packet in try XCTUnwrap(testCase["packets"] as? [[String: Any]], id) {
                client.processServiceData(
                    characteristic,
                    data: try dataFromHex(try XCTUnwrap(packet["frameHex"] as? String, id)),
                    err: try XCTUnwrap(packet["status"] as? Int, id)
                )
            }

            let expected = try XCTUnwrap(testCase["expected"] as? [String: Any], id)
            let expectedSequence = try XCTUnwrap(expected["sequence"] as? [[String: Any]], id)
            let events = try await notifications(from: client.waitNotification(), count: expectedSequence.count)
            XCTAssertEqual(events.count, expectedSequence.count, id)
            for (index, expectedNotification) in expectedSequence.enumerated() {
                XCTAssertEqual(events[index].id, Int32(try XCTUnwrap(expectedNotification["id"] as? Int, id)), id)
                XCTAssertEqual(events[index].parameters as Data, try dataFromHex(try XCTUnwrap(expectedNotification["parametersHex"] as? String, id)), id)
            }
        }
    }

    func testPsFtpResponseGoldenVectorsReassembleRequestResponses() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "request-response-reassembly")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let requestHeader = try dataFromHex(try XCTUnwrap(input["requestHeaderHex"] as? String))
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        XCTAssertEqual(requestResponseReassemblyCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String) })

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let responseFrames = try XCTUnwrap(testCase["responseFramesHex"] as? [String], id).map { try dataFromHex($0) }
            let transmitter = PsFtpResponseTransmitter(responseFrames: responseFrames)
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, enabled: true, err: 0)
            defer { client.disconnected() }

            let response = try await client.request(requestHeader)

            let expected = try XCTUnwrap(testCase["expected"] as? [String: Any], id)
            XCTAssertEqual(response as Data, try dataFromHex(try XCTUnwrap(expected["payloadHex"] as? String, id)), id)
        }
    }

    func testPsFtpResponseGoldenVectorsCharacterizeRequestErrorPolicy() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "request-response-error-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let requestHeader = try dataFromHex(try XCTUnwrap(input["requestHeaderHex"] as? String))
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        XCTAssertEqual(requestResponseErrorCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String) })

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let responseFrames = try XCTUnwrap(testCase["responseFramesHex"] as? [String], id).map { try dataFromHex($0) }
            let transmitter = PsFtpResponseTransmitter(responseFrames: responseFrames)
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, enabled: true, err: 0)
            defer { client.disconnected() }

            let expected = try XCTUnwrap(try XCTUnwrap(testCase["expected"] as? [String: Any], id)["ios"] as? [String: Any], id)
            switch try XCTUnwrap(expected["outcome"] as? String, id) {
            case "throwsResponseError":
                do {
                    _ = try await client.request(requestHeader)
                    XCTFail("\(id) should throw a response error")
                } catch let error as BlePsFtpException {
                    let expectedErrorCode = try XCTUnwrap(expected["errorCode"] as? Int, id)
                    guard case .responseError(errorCode: let actualErrorCode) = error, actualErrorCode == expectedErrorCode else {
                        return XCTFail("Unexpected error for \(id): \(error)")
                    }
                }
            default:
                XCTFail("Unsupported iOS PSFTP response expectation for \(id)")
            }
        }
    }

    func testPsFtpResponseGoldenVectorsCharacterizeWriteSuccessProgress() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "write-success-progress")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let header = try buildPftpOperationHeader(command: try XCTUnwrap(input["command"] as? String), path: try XCTUnwrap(input["path"] as? String))
        let payload = try dataFromHex(try XCTUnwrap(input["payloadHex"] as? String))
        let responseFrames = try XCTUnwrap(input["responseFramesHex"] as? [String]).map { try dataFromHex($0) }
        let transmitter = PsFtpResponseTransmitter(responseFrames: responseFrames)
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, enabled: true, err: 0)
        defer { client.disconnected() }

        var progress: [UInt] = []
        for try await emitted in client.write(header as NSData, data: InputStream(data: payload)) {
            progress.append(emitted)
        }

        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let expectedProgress = (try XCTUnwrap(expected["ios"] as? [String: Any]))["progress"] as? [Int]
        XCTAssertEqual(progress, try XCTUnwrap(expectedProgress).map { UInt($0) })
    }

    func testPsFtpResponseGoldenVectorsCharacterizeWriteInterruptionErrorPolicy() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "write-interruption-error-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let header = try buildPftpOperationHeader(command: try XCTUnwrap(input["command"] as? String), path: try XCTUnwrap(input["path"] as? String))
        let payload = try dataFromHex(try XCTUnwrap(input["payloadHex"] as? String))
        let interruptFrame = try dataFromHex(try XCTUnwrap(input["interruptFrameHex"] as? String))
        let transmitter = PsFtpResponseTransmitter(responseFrames: [], interruptFrames: [interruptFrame])
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, enabled: true, err: 0)
        client.setMtu(5)
        defer { client.disconnected() }

        let expected = try XCTUnwrap(try XCTUnwrap(vector["expected"] as? [String: Any])["ios"] as? [String: Any])
        switch try XCTUnwrap(expected["outcome"] as? String) {
        case "throwsResponseError":
            do {
                for try await _ in client.write(header as NSData, data: InputStream(data: payload)) {}
                XCTFail("write-interruption-error-policy should throw a response error")
            } catch let error as BlePsFtpException {
                let expectedErrorCode = try XCTUnwrap(expected["errorCode"] as? Int)
                guard case .responseError(errorCode: let actualErrorCode) = error, actualErrorCode == expectedErrorCode else {
                    return XCTFail("Unexpected error for write-interruption-error-policy: \(error)")
                }
            }
        default:
            XCTFail("Unsupported iOS PSFTP write interruption expectation")
        }
    }

    func testPsFtpResponseGoldenVectorsCharacterizeWriteTransportFailurePolicy() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "write-transport-failure-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let header = try buildPftpOperationHeader(command: try XCTUnwrap(input["command"] as? String), path: try XCTUnwrap(input["path"] as? String))
        let payload = try dataFromHex(try XCTUnwrap(input["payloadHex"] as? String))
        let failure = try XCTUnwrap(input["failure"] as? [String: Any])
        let transportError = NSError(
            domain: try XCTUnwrap(failure["domain"] as? String),
            code: try XCTUnwrap(failure["code"] as? Int),
            userInfo: [NSLocalizedDescriptionKey: try XCTUnwrap(failure["message"] as? String)]
        )
        let transmitter = PsFtpResponseTransmitter(responseFrames: [], transmitFailure: transportError)
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, enabled: true, err: 0)
        defer { client.disconnected() }

        let expected = try XCTUnwrap(try XCTUnwrap(vector["expected"] as? [String: Any])["ios"] as? [String: Any])
        switch try XCTUnwrap(expected["outcome"] as? String) {
        case "throwsNSError":
            do {
                for try await _ in client.write(header as NSData, data: InputStream(data: payload)) {}
                XCTFail("write-transport-failure-policy should throw the transport NSError")
            } catch {
                let nsError = error as NSError
                XCTAssertEqual(nsError.domain, try XCTUnwrap(expected["domain"] as? String))
                XCTAssertEqual(nsError.code, try XCTUnwrap(expected["code"] as? Int))
            }
        default:
            XCTFail("Unsupported iOS PSFTP write transport failure expectation")
        }
    }

    func testPsFtpNotificationGoldenVectorsCharacterizeErrorPolicy() async throws {
        let vector = try loadPsFtpNotificationVector(id: "notification-error-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC
        XCTAssertEqual(notificationErrorCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String) })

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let transmitter = MockGattServiceTransmitterImpl()
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            defer { client.disconnected() }

            for packet in try XCTUnwrap(testCase["packets"] as? [[String: Any]], id) {
                client.processServiceData(
                    characteristic,
                    data: try dataFromHex(try XCTUnwrap(packet["frameHex"] as? String, id)),
                    err: try XCTUnwrap(packet["status"] as? Int, id)
                )
            }

            let expected = try XCTUnwrap(try XCTUnwrap(testCase["expected"] as? [String: Any], id)["ios"] as? [String: Any], id)
            switch try XCTUnwrap(expected["outcome"] as? String, id) {
            case "noEmission":
                do {
                    _ = try await firstNotification(from: client.waitNotification(), timeout: 1.0)
                    XCTFail("\(id) should not emit a notification")
                } catch is TimeoutError {
                    break
                }
            case "throwsResponseError":
                do {
                    _ = try await firstNotification(from: client.waitNotification())
                    XCTFail("\(id) should throw a response error")
                } catch let error as BlePsFtpException {
                    let expectedErrorCode = try XCTUnwrap(expected["errorCode"] as? Int, id)
                    guard case .responseError(errorCode: let actualErrorCode) = error, actualErrorCode == expectedErrorCode else {
                        return XCTFail("Unexpected error for \(id): \(error)")
                    }
                }
            default:
                XCTFail("Unsupported iOS PSFTP notification expectation for \(id)")
            }
        }
    }

    func testPsFtpNotificationGoldenVectorsCharacterizeTimeoutPolicy() async throws {
        let vector = try loadPsFtpNotificationVector(id: "notification-timeout-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        let characteristic: CBUUID = BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC

        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let transmitter = MockGattServiceTransmitterImpl()
            let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
            defer { client.disconnected() }

            for packet in try XCTUnwrap(testCase["packets"] as? [[String: Any]], id) {
                client.processServiceData(
                    characteristic,
                    data: try dataFromHex(try XCTUnwrap(packet["frameHex"] as? String, id)),
                    err: try XCTUnwrap(packet["status"] as? Int, id)
                )
            }

            let expected = try XCTUnwrap(try XCTUnwrap(testCase["expected"] as? [String: Any], id)["ios"] as? [String: Any], id)
            switch try XCTUnwrap(expected["outcome"] as? String, id) {
            case "noEmission":
                do {
                    _ = try await firstNotification(from: client.waitNotification(), timeout: TimeInterval(try XCTUnwrap(testCase["observerTimeoutMs"] as? Int, id)) / 1000.0)
                    XCTFail("\(id) should not emit a notification")
                } catch is TimeoutError {
                    break
                }
            default:
                XCTFail("Unsupported iOS PSFTP notification timeout expectation for \(id)")
            }
        }
    }

    func testPsFtpNotificationGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadPsFtpNotificationVectors() {
            let id = try assertNeutralKmpVectorShape(vector)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            XCTAssertNotNil(input["cases"], id)
        }
    }

    func testPsFtpResponseGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadPsFtpVectors(directoryName: "psftp-response") {
            let inputKind = (vector["input"] as? [String: Any])?["kind"] as? String
            if inputKind == "psFtpRuntimeReadiness" { continue }
            let id = try assertNeutralKmpVectorShape(vector)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            XCTAssertTrue(input["cases"] != nil || input["payloadHex"] != nil, id)
            XCTAssertTrue(input["requestHeaderHex"] != nil || input["path"] != nil, id)
        }
    }

    func testPsFtpTimeoutPlanningVectorsRequireFakeClockForSharedRuntimeOwnership() throws {
        try assertFakeClockPlanningVector(
            try loadPsFtpNotificationVector(id: "notification-continuation-timeout-policy"),
            androidExecution: "injectable-timeout-unit-test",
            iosExecution: "injectable-timeout-xctest",
            commonExecution: "shared-common-test",
            expectedCaseIds: notificationContinuationTimeoutCaseIds
        )
        try assertFakeClockPlanningVector(
            try loadPsFtpVector(directoryName: "psftp-response", id: "write-ack-timeout-policy"),
            androidExecution: "injectable-timeout-unit-test",
            iosExecution: "injectable-timeout-xctest",
            commonExecution: "shared-common-test"
        )
    }

    func testPsFtpNotificationContinuationTimeoutExecutesWithInjectedProtocolTimeout() async throws {
        let vector = try loadPsFtpNotificationVector(id: "notification-continuation-timeout-policy")
        let testCase = try XCTUnwrap((try XCTUnwrap(vector["input"] as? [String: Any])["cases"] as? [[String: Any]])?.first)
        let packet = try XCTUnwrap((try XCTUnwrap(testCase["packets"] as? [[String: Any]])).first)
        let client = BlePsFtpClient(gattServiceTransmitter: MockGattServiceTransmitterImpl())
        client.PROTOCOL_TIMEOUT = 0.05
        defer { client.disconnected() }

        client.processServiceData(
            BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC,
            data: try dataFromHex(try XCTUnwrap(packet["frameHex"] as? String)),
            err: try XCTUnwrap(packet["status"] as? Int)
        )

        do {
            _ = try await firstNotification(from: client.waitNotification(), timeout: 1.0)
            XCTFail("Expected PSFTP continuation timeout")
        } catch let error as BlePsFtpException {
            guard case .responseError(errorCode: -1) = error else {
                return XCTFail("Expected responseError(-1), got \(error)")
            }
        }
    }

    func testPsFtpWriteAckTimeoutExecutesWithInjectedProtocolTimeout() async throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "write-ack-timeout-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let header = try buildPftpOperationHeader(command: try XCTUnwrap(input["command"] as? String), path: try XCTUnwrap(input["path"] as? String))
        let payload = try dataFromHex(try XCTUnwrap(input["payloadHex"] as? String))
        let transmitter = PsFtpNoAckTransmitter()
        let client = BlePsFtpClient(gattServiceTransmitter: transmitter)
        client.PROTOCOL_TIMEOUT = 0.05
        defer { client.disconnected() }

        do {
            for try await _ in client.write(header as NSData, data: InputStream(data: payload)) {}
            XCTFail("Expected PSFTP write ACK timeout")
        } catch AtomicIntegerException.waitTimeout {
            XCTAssertTrue(true)
        }
    }

    func testPsFtpRuntimeReadinessManifestPinsSharedRuntimeOwnership() throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-response", id: "psftp-runtime-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "psftp-runtime-readiness")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "psftp-runtime-readiness")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "psftp-runtime-readiness")
        let runtimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "psftp-runtime-readiness")
        XCTAssertEqual(vector["id"] as? String, "psftp-runtime-readiness")
        XCTAssertEqual(input["kind"] as? String, "psFtpRuntimeReadiness")
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "psftp-runtime-readiness")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "psftp-runtime-readiness")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "psftp-runtime-readiness")
        XCTAssertEqual(requiredPsFtpRuntimePolicyPaths, policyPaths)
        XCTAssertEqual(requiredPsFtpRuntimeFamilies, requiredFamilies)
        XCTAssertEqual(requiredPsFtpRuntimeFamilies, coveredFamilies)
        let commonDecision = try XCTUnwrap(expected["commonDecision"] as? String, "psftp-runtime-readiness")
        XCTAssertEqual(commonDecision, psFtpRuntimeReadinessCommonDecision)
        XCTAssertEqual(runtimePrototype["status"] as? String, "executable shared commonTest runtime planning guard")
        XCTAssertEqual(runtimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production shared ownership.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "psftp-runtime-readiness"), ["com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClientTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "psftp-runtime-readiness"), ["BlePsFtpClientTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "psftp-runtime-readiness"), ["com.polar.sharedtest.PsFtpRuntimePolicyCommonTest"])
    }

    private func loadPsFtpNotificationVector(id: String) throws -> [String: Any] {
        try loadPsFtpVector(directoryName: "psftp-notifications", id: id)
    }

    private func loadPsFtpNotificationVectors() throws -> [[String: Any]] {
        try loadPsFtpVectors(directoryName: "psftp-notifications")
    }

    private func loadPsFtpVector(directoryName: String, id: String) throws -> [String: Any] {
        for vector in try loadPsFtpVectors(directoryName: directoryName) {
            if vector["id"] as? String == id {
                return vector
            }
        }
        throw NSError(domain: "BlePsFtpClientTest", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not find PSFTP vector \(id) in \(directoryName)"])
    }

    private func loadPsFtpVectors(directoryName: String) throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/\(directoryName)")
        let files = try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        return try files.map { file in
            try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any], file.path)
        }
    }

    private let requiredPsFtpRuntimePolicyPaths = [
        "sdk/psftp-response/request-response-reassembly.json",
        "sdk/psftp-response/request-response-error-policy.json",
        "sdk/psftp-notifications/notification-reassembly.json",
        "sdk/psftp-notifications/notification-ordering.json",
        "sdk/psftp-notifications/notification-timeout-policy.json",
        "sdk/psftp-notifications/notification-error-policy.json",
        "sdk/psftp-notifications/notification-continuation-timeout-policy.json",
        "sdk/psftp-response/write-success-progress.json",
        "sdk/psftp-response/write-interruption-error-policy.json",
        "sdk/psftp-response/write-transport-failure-policy.json",
        "sdk/psftp-response/write-ack-timeout-policy.json"
    ]

    private let requiredPsFtpRuntimeFamilies = [
        "request-response-reassembly",
        "request-response-error-mapping",
        "notification-reassembly",
        "notification-ordering",
        "initial-silence-no-built-in-timeout",
        "consumer-timeout-observer-cleanup",
        "notification-rfc76-error-policy",
        "notification-transport-status-platform-split",
        "notification-continuation-timeout",
        "write-progress-platform-split",
        "write-interruption-response-error",
        "write-transport-failure",
        "write-ack-timeout",
        "fake-clock-timeout-gate",
        "platform-client-vector-reference-gate",
        "compile-verification-gate"
    ]

    private let requestResponseReassemblyCaseIds = ["single-frame", "multi-frame"]
    private let requestResponseErrorCaseIds = ["known-error-no-such-file", "unknown-error-code"]
    private let notificationReassemblyCaseIds = ["single-frame", "multi-frame"]
    private let notificationOrderingCaseIds = ["two-single-frame-notifications"]
    private let notificationErrorCaseIds = ["rfc76-error-first-frame", "transport-error-first-packet"]
    private let notificationContinuationTimeoutCaseIds = ["missing-last-frame-after-more"]

    private let psFtpRuntimeReadinessCommonDecision = "PSFTP runtime shared ownership remains valid while every policy vector listed in this readiness manifest is executable from shared commonTest, Android and iOS PSFTP client tests continue to reference the same vectors, request response reassembly, response-error mapping, notification reassembly and ordering, initial-silence policy, consumer timeout cleanup, notification error platform split, continuation timeout, write progress split, write interruption, transport failure, write acknowledgement timeout, fake-clock timeout gates, and the shared tests are compile-verified."

    private func assertNeutralKmpVectorShape(_ vector: [String: Any]) throws -> String {
        let id = try XCTUnwrap(vector["id"] as? String)
        XCTAssertNotNil(vector["area"], id)
        XCTAssertNotNil(vector["case"], id)
        XCTAssertNotNil(vector["source"], id)
        XCTAssertNotNil(vector["input"], id)
        XCTAssertNotNil(vector["expected"], id)
        let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
        XCTAssertEqual(platforms["android"] as? Bool, true, id)
        XCTAssertEqual(platforms["ios"] as? Bool, true, id)
        XCTAssertEqual(platforms["common"] as? Bool, true, id)
        return id
    }

    private func assertFakeClockPlanningVector(
        _ vector: [String: Any],
        androidExecution: String,
        iosExecution: String,
        commonExecution: String,
        expectedCaseIds: [String]? = nil
    ) throws {
        let id = try assertNeutralKmpVectorShape(vector)
        if let expectedCaseIds {
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let cases = try XCTUnwrap(input["cases"] as? [[String: Any]], id)
            XCTAssertEqual(expectedCaseIds, try cases.map { try XCTUnwrap($0["id"] as? String, id) }, id)
        }
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], id)
        XCTAssertEqual(execution["android"] as? String, androidExecution, id)
        XCTAssertEqual(execution["ios"] as? String, iosExecution, id)
        XCTAssertEqual(execution["common"] as? String, commonExecution, id)
        let platformExpectations = try XCTUnwrap(vector["platformExpectations"] as? [String: Any], id)
        let commonDecision = try XCTUnwrap(platformExpectations["commonDecision"] as? [String: Any], id)
        XCTAssertFalse(try XCTUnwrap(commonDecision["errorPolicy"] as? String, id).isEmpty, id)
    }

    private func buildPftpOperationHeader(command: String, path: String) throws -> Data {
        var operation = Communications_PbPFtpOperation()
        switch command {
        case "GET":
            operation.command = .get
        case "PUT":
            operation.command = .put
        case "MERGE":
            operation.command = .merge
        case "REMOVE":
            operation.command = .remove
        default:
            throw NSError(domain: "BlePsFtpClientTest", code: 5, userInfo: [NSLocalizedDescriptionKey: "Unsupported PFTP command \(command)"])
        }
        operation.path = path
        return try operation.serializedData()
    }


    private func dataFromHex(_ hex: String) throws -> Data {
        guard hex.count % 2 == 0 else {
            throw NSError(domain: "BlePsFtpClientTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Hex string must have even length"])
        }
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw NSError(domain: "BlePsFtpClientTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte"])
            }
            data.append(byte)
            index = next
        }
        return data
    }
}

private final class PsFtpResponseTransmitter: MockGattServiceTransmitterImpl {
    private let responseFrames: [Data]
    private let interruptFrames: [Data]
    private let transmitFailure: NSError?
    private var didInjectResponses = false
    private var didInjectInterrupt = false
    private var didThrowTransmitFailure = false

    init(responseFrames: [Data], interruptFrames: [Data] = [], transmitFailure: NSError? = nil) {
        self.responseFrames = responseFrames
        self.interruptFrames = interruptFrames
        self.transmitFailure = transmitFailure
    }

    override func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {
        if characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, let transmitFailure, !didThrowTransmitFailure {
            didThrowTransmitFailure = true
            throw transmitFailure
        }
        try super.transmitMessage(parent, serviceUuid: serviceUuid, characteristicUuid: characteristicUuid, packet: packet, withResponse: withResponse)
        parent.serviceDataWritten(characteristicUuid, err: 0)
        if characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, !didInjectInterrupt, let firstByte = packet.first, (firstByte & 0x06) == 0x06 {
            didInjectInterrupt = true
            interruptFrames.forEach {
                parent.processServiceData(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, data: $0, err: 0)
            }
            return
        }
        guard characteristicUuid == BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, !didInjectResponses, let firstByte = packet.first, (firstByte & 0x06) == 0x02 else {
            return
        }
        didInjectResponses = true
        responseFrames.forEach {
            parent.processServiceData(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, data: $0, err: 0)
        }
    }
}

private final class PsFtpNoAckTransmitter: MockGattServiceTransmitterImpl {
    override func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {
        try super.transmitMessage(parent, serviceUuid: serviceUuid, characteristicUuid: characteristicUuid, packet: packet, withResponse: withResponse)
    }
}
