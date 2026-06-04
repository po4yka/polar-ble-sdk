package com.polar.sharedtest

const val PROTOCOL_ONLY_MIGRATION_OWNERSHIP = "Protocol-only characterization excluded from first common KMP migration because current Android/iOS behavior is platform-specific. Use this vector to make an explicit shared parser policy before promoting this behavior to common code."

fun String.objectValue(field: String): String {
    val objectStart = fieldValueStart(field) ?: error("Missing object field $field")
    require(this[objectStart] == '{') { "Missing object start for $field" }
    return balancedObjectAt(objectStart)
}

fun String.optionalObjectValue(field: String): String? {
    val objectStart = fieldValueStart(field) ?: return null
    if (this[objectStart] != '{') return null
    return balancedObjectAt(objectStart)
}

fun String.objectArray(field: String): List<String> {
    val arrayStart = fieldValueStart(field) ?: error("Missing array field $field")
    require(this[arrayStart] == '[') { "Missing array start for $field" }
    val arrayEnd = balancedEnd(arrayStart, '[', ']')
    val arrayContent = substring(arrayStart + 1, arrayEnd)
    val objects = mutableListOf<String>()
    var index = 0
    while (index < arrayContent.length) {
        val objectStart = arrayContent.indexOf('{', index)
        if (objectStart < 0) break
        val jsonObject = arrayContent.balancedObjectAt(objectStart)
        objects += jsonObject
        index = objectStart + jsonObject.length
    }
    return objects
}

fun String.stringValue(field: String): String {
    val valueStart = fieldValueStart(field) ?: error("Missing string field $field in $this")
    require(this[valueStart] == '"') { "Missing string value for $field in $this" }
    return readJsonString(valueStart).value
}

fun String.optionalStringValue(field: String): String? {
    val valueStart = fieldValueStart(field) ?: return null
    if (this[valueStart] != '"') return null
    return readJsonString(valueStart).value
}

fun String.nullableStringValue(field: String): String? {
    val valueStart = fieldValueStart(field) ?: error("Missing nullable string field $field")
    if (startsWith("null", valueStart)) return null
    return stringValue(field)
}

fun String.stringArrayValue(field: String): List<String> {
    return optionalStringArrayValue(field) ?: error("Missing string array field $field")
}

fun String.stringArrayContains(field: String, value: String): Boolean {
    return stringArrayValue(field).contains(value)
}

fun String.optionalStringArrayValue(field: String): List<String>? {
    val arrayStart = fieldValueStart(field) ?: return null
    if (this[arrayStart] != '[') return null
    val arrayEnd = balancedEnd(arrayStart, '[', ']')
    return Regex("\"([^\"]*)\"").findAll(substring(arrayStart + 1, arrayEnd)).map { it.groupValues[1] }.toList()
}

fun String.intValue(field: String): Int {
    val valueStart = fieldValueStart(field) ?: error("Missing int field $field in $this")
    val match = Regex("-?\\d+").find(this, valueStart)
    require(match != null) { "Missing int field $field in $this" }
    return match.value.toInt()
}

fun String.doubleValue(field: String): Double {
    val valueStart = fieldValueStart(field) ?: error("Missing double field $field in $this")
    val match = Regex("-?\\d+(?:\\.\\d+)?").find(this, valueStart)
    require(match != null) { "Missing double field $field in $this" }
    return match.value.toDouble()
}

fun String.signedIntArrayValue(field: String): List<Int> {
    val arrayStart = fieldValueStart(field) ?: error("Missing signed int array field $field")
    require(this[arrayStart] == '[') { "Missing signed int array start for $field" }
    val arrayEnd = balancedEnd(arrayStart, '[', ']')
    val content = substring(arrayStart + 1, arrayEnd)
    if (content.trim().isEmpty()) return emptyList()
    return Regex("-?\\d+").findAll(content).map { it.value.toInt() }.toList()
}

fun String.optionalSignedIntArrayValue(field: String): List<Int>? {
    val arrayStart = fieldValueStart(field) ?: return null
    if (this[arrayStart] != '[') return null
    val arrayEnd = balancedEnd(arrayStart, '[', ']')
    val content = substring(arrayStart + 1, arrayEnd)
    if (content.trim().isEmpty()) return emptyList()
    return Regex("-?\\d+").findAll(content).map { it.value.toInt() }.toList()
}

fun String.booleanValue(field: String): Boolean {
    val valueStart = fieldValueStart(field) ?: error("Missing boolean field $field in $this")
    return when {
        startsWith("true", valueStart) -> true
        startsWith("false", valueStart) -> false
        else -> error("Missing boolean field $field in $this")
    }
}

fun String.optionalBooleanValue(field: String): Boolean? {
    val valueStart = fieldValueStart(field) ?: return null
    return when {
        startsWith("true", valueStart) -> true
        startsWith("false", valueStart) -> false
        else -> null
    }
}

fun String.booleanArrayValue(field: String): List<Boolean> {
    val arrayStart = fieldValueStart(field) ?: error("Missing boolean array field $field")
    require(this[arrayStart] == '[') { "Missing boolean array start for $field" }
    val arrayEnd = balancedEnd(arrayStart, '[', ']')
    val content = substring(arrayStart + 1, arrayEnd)
    if (content.trim().isEmpty()) return emptyList()
    return Regex("true|false").findAll(content).map { it.value == "true" }.toList()
}

fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.decodeAscii(): String {
    return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toChar().toString() }
}

fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toHexByte() }
}

fun Int.toHexByte(): String {
    val value = this and 0xFF
    return "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
}

fun String.withTrailingSlash(): String {
    return if (endsWith("/")) this else "$this/"
}

fun String.normalizedDirectoryPath(): String {
    val prefixed = if (startsWith("/")) this else "/$this"
    return if (prefixed.endsWith("/")) prefixed else "$prefixed/"
}

private fun String.balancedObjectAt(start: Int): String {
    val end = balancedEnd(start, '{', '}')
    return substring(start, end + 1)
}

private fun String.fieldValueStart(field: String): Int? {
    return directFieldValueStart(field) ?: nestedFieldValueStart(field)
}

private fun String.directFieldValueStart(field: String): Int? {
    var depth = 0
    var index = 0
    while (index < length) {
        val char = this[index]
        when (char) {
            '"' -> {
                val stringToken = readJsonString(index)
                index = stringToken.nextIndex
                if (depth == 1 && stringToken.value == field) {
                    var colonIndex = index
                    while (colonIndex < length && this[colonIndex].isWhitespace()) colonIndex += 1
                    if (colonIndex < length && this[colonIndex] == ':') {
                        var valueStart = colonIndex + 1
                        while (valueStart < length && this[valueStart].isWhitespace()) valueStart += 1
                        return valueStart
                    }
                }
            }
            '{', '[' -> {
                depth += 1
                index += 1
            }
            '}', ']' -> {
                depth -= 1
                index += 1
            }
            else -> index += 1
        }
    }
    return null
}

private fun String.nestedFieldValueStart(field: String): Int? {
    var searchIndex = 0
    while (searchIndex < length) {
        val keyStart = indexOf("\"$field\"", searchIndex)
        if (keyStart < 0) return null
        val token = readJsonString(keyStart)
        if (token.value == field) {
            var colonIndex = token.nextIndex
            while (colonIndex < length && this[colonIndex].isWhitespace()) colonIndex += 1
            if (colonIndex < length && this[colonIndex] == ':') {
                var valueStart = colonIndex + 1
                while (valueStart < length && this[valueStart].isWhitespace()) valueStart += 1
                return valueStart
            }
        }
        searchIndex = token.nextIndex
    }
    return null
}

private fun String.readJsonString(start: Int): JsonStringToken {
    require(this[start] == '"') { "Expected JSON string at $start" }
    val value = StringBuilder()
    var index = start + 1
    var escaped = false
    while (index < length) {
        val char = this[index]
        if (escaped) {
            value.append(
                when (char) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> char
                }
            )
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == '"') {
            return JsonStringToken(value.toString(), index + 1)
        } else {
            value.append(char)
        }
        index += 1
    }
    error("Unterminated JSON string at $start")
}

fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until length) {
        val char = this[index]
        if (escaped) {
            escaped = false
            continue
        }
        if (char == '\\') {
            escaped = inString
            continue
        }
        if (char == '"') {
            inString = !inString
            continue
        }
        if (!inString && char == open) depth += 1
        if (!inString && char == close) {
            depth -= 1
            if (depth == 0) return index
        }
    }
    error("Unbalanced $open$close block")
}

private fun Int.toHexDigit(): Char {
    return if (this < 10) '0' + this else 'a' + (this - 10)
}

private data class JsonStringToken(
    val value: String,
    val nextIndex: Int
)
