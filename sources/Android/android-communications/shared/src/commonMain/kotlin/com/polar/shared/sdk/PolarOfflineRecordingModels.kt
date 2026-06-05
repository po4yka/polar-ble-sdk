package com.polar.shared.sdk

object PolarOfflineRecordingModels {
    fun measurementTypeFromFileName(fileName: String): PolarOfflineRecordingMeasurementType {
        val baseName = fileName.substringBeforeLast(".").dropLastWhile { char -> char in '0'..'9' }
        return when (baseName) {
            "ACC" -> PolarOfflineRecordingMeasurementType.ACC
            "GYRO" -> PolarOfflineRecordingMeasurementType.GYRO
            "MAG" -> PolarOfflineRecordingMeasurementType.MAGNETOMETER
            "PPG" -> PolarOfflineRecordingMeasurementType.PPG
            "PPI" -> PolarOfflineRecordingMeasurementType.PPI
            "HR" -> PolarOfflineRecordingMeasurementType.OFFLINE_HR
            "TEMP" -> PolarOfflineRecordingMeasurementType.TEMPERATURE
            "SKINTEMP" -> PolarOfflineRecordingMeasurementType.SKIN_TEMP
            else -> throw IllegalArgumentException("Unknown offline file $fileName")
        }
    }

    fun groupedRecordingEntries(entries: List<PolarOfflineRecordingFileEntry>): List<PolarOfflineRecordingEntry> {
        val groups = linkedMapOf<String, MutableRecordingGroup>()
        entries.forEach { entry ->
            if (entry.size <= 0L) return@forEach
            val segments = entry.path.split('/')
            if (segments.size < 7) return@forEach
            val date = segments[3]
            val time = segments[5]
            if (!isValidDateTime(date, time)) return@forEach
            val fileName = segments.last()
            val type = runCatching { measurementTypeFromFileName(fileName).toPublicType() }.getOrNull() ?: return@forEach
            val baseFileName = fileName.substringBeforeLast(".").dropLastWhile { char -> char in '0'..'9' } + ".REC"
            val key = "$date/$time/$type"
            val group = groups.getOrPut(key) {
                MutableRecordingGroup(
                    type = type,
                    firstPath = entry.path,
                    normalizedPath = "/U/0/$date/R/$time/$baseFileName",
                    dateTime = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}T${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
                )
            }
            group.size += entry.size
        }
        return groups.values.map { group ->
            PolarOfflineRecordingEntry(
                type = group.type,
                androidPath = group.normalizedPath,
                iosPath = group.firstPath,
                size = group.size,
                dateTime = group.dateTime
            )
        }
    }

    fun parsePmdFilesV2(text: String): List<PolarOfflineRecordingEntry> {
        return groupedRecordingEntries(
            text.lines()
                .filter { line -> line.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(' ').filter { part -> part.isNotBlank() }
                    if (parts.size != 2) return@mapNotNull null
                    val size = parts[0].toLongOrNull() ?: return@mapNotNull null
                    PolarOfflineRecordingFileEntry(path = parts[1], size = size)
                }
        )
    }

    private fun PolarOfflineRecordingMeasurementType.toPublicType(): String {
        return when (this) {
            PolarOfflineRecordingMeasurementType.OFFLINE_HR -> "HR"
            PolarOfflineRecordingMeasurementType.SKIN_TEMP -> "SKIN_TEMPERATURE"
            else -> name
        }
    }

    private fun isValidDateTime(date: String, time: String): Boolean {
        if (!DATE.matches(date) || !TIME.matches(time)) return false
        val month = date.substring(4, 6).toInt()
        val day = date.substring(6, 8).toInt()
        val hour = time.substring(0, 2).toInt()
        val minute = time.substring(2, 4).toInt()
        val second = time.substring(4, 6).toInt()
        return month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..59
    }

    private data class MutableRecordingGroup(
        val type: String,
        val firstPath: String,
        val normalizedPath: String,
        val dateTime: String,
        var size: Long = 0L
    )

    private val DATE = Regex("\\d{8}")
    private val TIME = Regex("\\d{6}")
}

data class PolarOfflineRecordingFileEntry(
    val path: String,
    val size: Long
)

data class PolarOfflineRecordingEntry(
    val type: String,
    val androidPath: String,
    val iosPath: String,
    val size: Long,
    val dateTime: String
)

enum class PolarOfflineRecordingMeasurementType {
    ACC,
    GYRO,
    MAGNETOMETER,
    PPG,
    PPI,
    OFFLINE_HR,
    TEMPERATURE,
    SKIN_TEMP
}
