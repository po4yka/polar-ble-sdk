//
//  PolarBleSdkTests.swift
//  PolarBleSdkTests
//
//  Created by Jukka Oikarinen on 9.11.2022.
//  Copyright © 2022 Polar. All rights reserved.
//

import XCTest
@testable import PolarBleSdk

final class PolarBleSdkTests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testExample() throws {
        // This is an example of a functional test case.
        // Use XCTAssert and related functions to verify your tests produce the correct results.
        // Any test you write for XCTest can be annotated as throws and async.
        // Mark your test throws to produce an unexpected failure when your test encounters an uncaught error.
        // Mark your test async to allow awaiting for asynchronous code to complete. Check the results with assertions afterwards.
    }

    func testStoredDataTypeStringHelpersPreservePublicMapping() {
        XCTAssertEqual("UNDEFINED", PolarStoredDataType.getStringValue(dataTypeLocationIndex: 0))
        XCTAssertEqual("ACTIVITY", PolarStoredDataType.getStringValue(dataTypeLocationIndex: 1))
        XCTAssertEqual("SKINTEMP", PolarStoredDataType.getStringValue(dataTypeLocationIndex: 9))
        XCTAssertEqual(.AUTO_SAMPLE, PolarStoredDataType.getValue(name: "AUTO_SAMPLE"))
        XCTAssertEqual(.UNDEFINED, PolarStoredDataType.getValue(name: "UNKNOWN_TYPE"))
        XCTAssertEqual(PolarStoredDataType.StoredDataType.allCases.map { String(describing: $0) }, PolarStoredDataType.getAllAsString())
    }

    func testPerformanceExample() throws {
        // This is an example of a performance test case.
        measure {
            // Put the code you want to measure the time of here.
        }
    }

}
