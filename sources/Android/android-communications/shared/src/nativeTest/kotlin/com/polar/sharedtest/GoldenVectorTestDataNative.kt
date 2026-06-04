package com.polar.sharedtest

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.rewind

@OptIn(ExperimentalForeignApi::class)
actual fun loadGoldenVectorText(relativePath: String): String {
    val repositoryRoot = findRepositoryRoot()
    return readTextFile("$repositoryRoot/testdata/golden-vectors/$relativePath")
}

@OptIn(ExperimentalForeignApi::class)
private fun findRepositoryRoot(): String {
    getenv("POLAR_BLE_SDK_REPOSITORY_ROOT")?.toKString()?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.let { return it }
    return memScoped {
        val buffer = allocArray<ByteVar>(4096)
        val cwd = getcwd(buffer, 4096.convert())?.toKString() ?: error("Could not resolve current working directory")
        var directory = cwd.trimEnd('/')
        var repositoryRoot: String? = null
        while (repositoryRoot == null) {
            if (directoryExists("$directory/testdata/golden-vectors")) {
                repositoryRoot = directory
            } else {
                val parent = directory.substringBeforeLast('/', missingDelimiterValue = "")
                if (parent.isEmpty() || parent == directory) {
                    error("Could not find repository root from $cwd")
                }
                directory = parent
            }
        }
        repositoryRoot
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun directoryExists(path: String): Boolean {
    val directory = opendir(path) ?: return false
    closedir(directory)
    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun readTextFile(path: String): String {
    val file = fopen(path, "rb") ?: error("Could not read golden vector $path")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        if (size < 0) {
            error("Could not determine golden vector size for $path")
        }
        rewind(file)
        val bytes = ByteArray(size.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinnedBytes ->
                val read = fread(pinnedBytes.addressOf(0), 1.convert(), bytes.size.convert(), file)
                if (read.toLong() != bytes.size.toLong()) {
                    error("Could not read complete golden vector $path")
                }
            }
        }
        return bytes.decodeToString()
    } finally {
        fclose(file)
    }
}
