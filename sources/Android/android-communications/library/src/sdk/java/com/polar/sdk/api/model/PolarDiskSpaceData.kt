package com.polar.sdk.api.model

import com.polar.shared.sdk.PolarSdkModelMappers
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
            val shared = PolarSdkModelMappers.diskSpace(
                fragmentSize = proto.fragmentSize.toLong(),
                totalFragments = proto.totalFragments,
                freeFragments = proto.freeFragments
            )
            return PolarDiskSpaceData(
                totalSpace = shared.totalSpace,
                freeSpace = shared.freeSpace
            )
        }
    }
}
