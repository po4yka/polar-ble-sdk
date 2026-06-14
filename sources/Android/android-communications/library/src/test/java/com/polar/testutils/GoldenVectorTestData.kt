package com.polar.testutils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileReader

object GoldenVectorTestData {
    fun repositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    fun root(): File {
        return repositoryRoot().resolve("testdata/golden-vectors")
    }

    fun directory(relativePath: String): File {
        return root().resolve(relativePath)
    }

    fun loadObject(relativePath: String): JsonObject {
        return FileReader(root().resolve(relativePath)).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    fun loadObjects(relativeDirectory: String): List<JsonObject> {
        return directory(relativeDirectory)
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { file -> file.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
    }
}
