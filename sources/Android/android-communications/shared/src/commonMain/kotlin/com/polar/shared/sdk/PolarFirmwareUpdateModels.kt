package com.polar.shared.sdk

object PolarFirmwareUpdateModels {
    const val SYSTEM_UPDATE_FILE: String = "SYSUPDAT.IMG"

    fun deviceVersionToString(major: Int, minor: Int, patch: Int): String {
        return "$major.$minor.$patch"
    }

    fun isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        val current = currentVersion.versionParts()
        val available = availableVersion.versionParts()
        val sharedSize = minOf(current.size, available.size)
        for (index in 0 until sharedSize) {
            if (available[index] > current[index]) return true
            if (available[index] < current[index]) return false
        }
        return available.size > current.size
    }

    fun orderFirmwareFiles(fileNames: List<String>): List<String> {
        return fileNames.filterNot { fileName -> fileName.contains(SYSTEM_UPDATE_FILE) } + fileNames.filter { fileName -> fileName.contains(SYSTEM_UPDATE_FILE) }
    }

    fun firmwareFilePriority(fileName: String): Int {
        return if (fileName.contains(SYSTEM_UPDATE_FILE)) 1 else 0
    }

    private fun String.versionParts(): List<Int> {
        return split(".").map { part ->
            part.toIntOrNull() ?: throw FirmwareVersionParseException(this)
        }
    }
}

class FirmwareVersionParseException(version: String) : NumberFormatException("Invalid firmware version $version")
