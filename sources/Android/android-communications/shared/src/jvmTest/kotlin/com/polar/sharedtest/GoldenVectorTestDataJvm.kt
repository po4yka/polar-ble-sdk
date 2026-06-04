package com.polar.sharedtest

import java.io.File

actual fun loadGoldenVectorText(relativePath: String): String {
    val root = findRepositoryRoot()
    return root.resolve("testdata/golden-vectors/$relativePath").readText()
}

private fun findRepositoryRoot(): File {
    val userDir = System.getProperty("user.dir") ?: "."
    var directory = File(userDir).absoluteFile
    while (true) {
        if (directory.resolve("testdata/golden-vectors").isDirectory) {
            return directory
        }
        directory = directory.parentFile ?: error("Could not find repository root from $userDir")
    }
}
