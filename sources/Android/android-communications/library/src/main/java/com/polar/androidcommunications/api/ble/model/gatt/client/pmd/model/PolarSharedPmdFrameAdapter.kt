package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.shared.pmd.sensors.PolarPmdDataFrame

internal fun PmdDataFrame.toPolarSharedFrame(): PolarPmdDataFrame {
    return PolarPmdDataFrame(
        frameType = frameType.id.toInt(),
        compressed = isCompressedFrame,
        timeStamp = timeStamp,
        previousTimeStamp = previousTimeStamp,
        factor = factor,
        sampleRate = sampleRate,
        dataContent = dataContent
    )
}
