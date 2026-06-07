package com.polar.sdk.api.model

import protocol.PftpResponse

/**
 * Disk space data in bytes.
 */
data class PolarDiskSpaceData(
    val totalSpace: Long,
    val freeSpace: Long
) {
    companion object {
        fun fromProto(proto: PftpResponse.PbPFtpDiskSpaceResult): PolarDiskSpaceData {
            val planned = PolarSdkModelAdapter.diskSpace(
                fragmentSize = proto.fragmentSize.toLong(),
                totalFragments = proto.totalFragments,
                freeFragments = proto.freeFragments
            )
            return PolarDiskSpaceData(
                totalSpace = planned.totalSpace,
                freeSpace = planned.freeSpace
            )
        }
    }
}
