/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth

/// Implementation of PolarTemperatureApi
extension PolarBleApiImpl: PolarTemperatureApi {

    func getSkinTemperature(identifier: String, fromDate: Date, toDate: Date) async throws -> [PolarSkinTemperatureData.PolarSkinTemperatureResult] {
        guard toDate >= fromDate else {
                 BleLogger.error("getSkinTemperature: Invalid date range: toDate \(toDate) is before fromDate \(fromDate)")
                 throw PolarErrors.invalidArgument(description: "toDate must be greater than or equal to fromDate")
             }

        let session = try await serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        var results: [PolarSkinTemperatureData.PolarSkinTemperatureResult] = []
        for date in PolarTimeUtils.basicDateRange(fromDate: fromDate, toDate: toDate) {
            if let skinTemp = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: client, date: date) {
                results.append(skinTemp)
            }
        }
        return results
    }
}
