package com.polar.androidcommunications.common.ble

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileReader

class AtomicSetTest {

    private lateinit var sut: AtomicSet<String>

    @Before
    fun setUp() {
        sut = AtomicSet()
    }

    @Test
    fun add_whenItemIsNew_returnsTrueAndIncreasesSize() {
        // Act
        val result = sut.add("item1")

        // Assert
        assertTrue(result)
        assertEquals(1, sut.size())
    }

    @Test
    fun add_whenItemAlreadyExists_returnsFalseAndSizeUnchanged() {
        // Arrange
        sut.add("item1")

        // Act
        val result = sut.add("item1")

        // Assert
        assertFalse(result)
        assertEquals(1, sut.size())
    }

    @Test
    fun add_whenItemIsNull_returnsFalseAndSizeUnchanged() {
        // Act
        val result = sut.add(null)

        // Assert
        assertFalse(result)
        assertEquals(0, sut.size())
    }

    @Test
    fun add_whenMultipleDistinctItems_addsAll() {
        // Act
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Assert
        assertEquals(3, sut.size())
    }

    @Test
    fun remove_whenItemExists_removesItemAndDecreasesSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove("item1")

        // Assert
        assertEquals(0, sut.size())
        assertFalse(sut.contains("item1"))
    }

    @Test
    fun remove_whenItemDoesNotExist_doesNotChangeSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove("nonExistent")

        // Assert
        assertEquals(1, sut.size())
    }

    @Test
    fun remove_whenItemIsNull_doesNotChangeSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove(null)

        // Assert
        assertEquals(1, sut.size())
    }

    // endregion

    // region clear

    @Test
    fun clear_whenItemsExist_removesAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        sut.clear()

        // Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun clear_whenEmpty_remainsEmpty() {
        // Act
        sut.clear()

        // Assert
        assertEquals(0, sut.size())
    }

    // endregion

    // region contains

    @Test
    fun contains_whenItemExists_returnsTrue() {
        // Arrange
        sut.add("item1")

        // Act
        val result = sut.contains("item1")

        // Assert
        assertTrue(result)
    }

    @Test
    fun contains_whenItemDoesNotExist_returnsFalse() {
        // Act
        val result = sut.contains("nonExistent")

        // Assert
        assertFalse(result)
    }

    @Test
    fun contains_whenSetIsEmpty_returnsFalse() {
        // Act
        val result = sut.contains("item1")

        // Assert
        assertFalse(result)
    }

    @Test
    fun contains_whenItemRemovedAfterAdding_returnsFalse() {
        // Arrange
        sut.add("item1")
        sut.remove("item1")

        // Act
        val result = sut.contains("item1")

        // Assert
        assertFalse(result)
    }

    @Test
    fun size_whenEmpty_returnsZero() {
        // Act & Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun size_afterAddingItems_returnsCorrectCount() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act & Assert
        assertEquals(2, sut.size())
    }

    @Test
    fun size_afterClear_returnsZero() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.clear()

        // Act & Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun objects_whenItemsExist_returnsSetOfAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        val result = sut.objects()

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.contains("item1"))
        assertTrue(result.contains("item2"))
        assertTrue(result.contains("item3"))
    }

    @Test
    fun objects_whenEmpty_returnsEmptySet() {
        // Act
        val result = sut.objects()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun objects_returnsSnapshot_notAffectedBySubsequentRemove() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        val snapshot = sut.objects()

        // Act
        sut.remove("item1")

        // Assert
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.contains("item1"))
    }

    @Test
    fun accessAll_whenItemsExist_visitsAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")
        val visited = mutableListOf<Any>()

        // Act
        sut.accessAll<String> { visited.add(it) }

        // Assert
        assertEquals(3, visited.size)
        assertTrue(visited.contains("item1"))
        assertTrue(visited.contains("item2"))
        assertTrue(visited.contains("item3"))
    }

    @Test
    fun accessAll_whenEmpty_doesNotInvokeCallback() {
        // Arrange
        var callCount = 0

        // Act
        sut.accessAll<String> { callCount++ }

        // Assert
        assertEquals(0, callCount)
    }

    @Test
    fun fetch_whenMatchingItemExists_returnsItem() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act
        val result = sut.fetch { it == "item1" }

        // Assert
        assertEquals("item1", result)
    }

    @Test
    fun fetch_whenNoMatchingItem_returnsNull() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act
        val result = sut.fetch { it == "nonExistent" }

        // Assert
        assertNull(result)
    }

    @Test
    fun fetch_whenEmpty_returnsNull() {
        // Act
        val result = sut.fetch { it == "item1" }

        // Assert
        assertNull(result)
    }

    @Test
    fun fetch_whenMultipleMatchingItems_returnsOneOfThem() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        val result = sut.fetch { (it as String).startsWith("item") }

        // Assert
        assertTrue((result as String).startsWith("item"))
    }

    @Test
    fun atomicSetGoldenVectors_matchAndroidBehavior() {
        val vectors = loadAtomicSetVectors()
        assertTrue("Expected AtomicSet golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val atomicSet = AtomicSet<String>()
            val snapshots = mutableMapOf<String, Set<String>>()

            input.getAsJsonArray("operations").forEach { operationElement ->
                val operation = operationElement.asJsonObject
                when (operation.get("op").asString) {
                    "add" -> {
                        val value = operation.nullableString("value")
                        assertEquals(caseId, operation.get("expectedReturn").asBoolean, atomicSet.add(value))
                    }
                    "remove" -> atomicSet.remove(operation.nullableString("value"))
                    "snapshot" -> snapshots[operation.get("name").asString] = atomicSet.objects()
                    "clear" -> atomicSet.clear()
                    else -> throw AssertionError("$caseId has unsupported AtomicSet operation ${operation.get("op").asString}")
                }
            }

            assertEquals(caseId, expected.get("size").asInt, atomicSet.size())
            if (expected.has("contains")) {
                expected.getAsJsonObject("contains").entrySet().forEach { (value, contains) ->
                    assertEquals(caseId, contains.asBoolean, atomicSet.contains(value))
                }
            }
            if (expected.has("snapshots")) {
                expected.getAsJsonObject("snapshots").entrySet().forEach { (name, values) ->
                    assertEquals(caseId, values.asJsonArray.map { it.asString }.toSet(), snapshots[name])
                }
            }
            if (expected.has("accessAllOrder")) {
                val visited = mutableListOf<String>()
                atomicSet.accessAll<String> { visited.add(it as String) }
                assertEquals(caseId, expected.getAsJsonArray("accessAllOrder").map { it.asString }, visited)
            }
            if (expected.has("fetchPrefix")) {
                val fetched = atomicSet.fetch { it?.startsWith(expected.get("fetchPrefix").asString) == true }
                if (expected.get("fetched").isJsonNull) {
                    assertNull(caseId, fetched)
                } else {
                    assertEquals(caseId, expected.get("fetched").asString, fetched)
                }
            }
        }
    }

    @Test
    fun `atomic set golden vectors follow neutral KMP vector shape`() {
        loadAtomicSetVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.has("android"))
            assertTrue(id, platforms.has("ios"))
            assertTrue(id, platforms.has("common"))
        }
    }

    private fun JsonObject.nullableString(name: String): String? {
        return if (get(name).isJsonNull) null else get(name).asString
    }

    private fun loadAtomicSetVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/atomic-set")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }
}
