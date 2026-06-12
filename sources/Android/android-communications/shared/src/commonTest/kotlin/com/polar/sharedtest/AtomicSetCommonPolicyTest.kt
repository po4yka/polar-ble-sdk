package com.polar.sharedtest

import com.polar.shared.ble.PolarAtomicSet
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomicSetCommonPolicyTest {
    @Test
    fun sharedAtomicSetExecutesAndroidCompatibilityVectors() {
        assertAddDuplicateNullAndSnapshotVector()
        assertClearRemoveMissingAndFetchNoneVector()
        assertReverseIterationAndFetchVector()
    }

    private fun assertAddDuplicateNullAndSnapshotVector() {
        val vector = loadGoldenVectorText("sdk/atomic-set/add-duplicate-null-and-snapshot.json")
        assertContains(vector, "\"id\": \"add-duplicate-null-and-snapshot\"")
        assertContains(vector, "Shared PolarAtomicSet owns collection-like set semantics")

        val set = PolarAtomicSet<String>()
        assertTrue(set.add("item1"))
        assertFalse(set.add("item1"))
        assertFalse(set.add(null))
        val snapshot = set.objects()
        set.remove("item1")

        assertEquals(0, set.size())
        assertFalse(set.contains("item1"))
        assertEquals(setOf("item1"), snapshot)
    }

    private fun assertClearRemoveMissingAndFetchNoneVector() {
        val vector = loadGoldenVectorText("sdk/atomic-set/clear-remove-missing-and-fetch-none.json")
        assertContains(vector, "\"id\": \"clear-remove-missing-and-fetch-none\"")
        assertContains(vector, "Shared PolarAtomicSet owns collection-like set semantics")

        val set = PolarAtomicSet<String>()
        assertTrue(set.add("item1"))
        assertTrue(set.add("item2"))
        set.remove(null)
        set.remove("missing")
        val snapshot = set.objects()
        set.clear()
        val visited = mutableListOf<String>()
        set.accessAll { visited.add(it) }

        assertEquals(0, set.size())
        assertEquals(emptyList(), visited)
        assertNull(set.fetch { it.startsWith("item") })
        assertFalse(set.contains("item1"))
        assertFalse(set.contains("item2"))
        assertFalse(set.contains("missing"))
        assertEquals(setOf("item1", "item2"), snapshot)
    }

    private fun assertReverseIterationAndFetchVector() {
        val vector = loadGoldenVectorText("sdk/atomic-set/reverse-iteration-and-fetch.json")
        assertContains(vector, "\"id\": \"reverse-iteration-and-fetch\"")
        assertContains(vector, "Shared PolarAtomicSet owns collection-like set semantics")

        val set = PolarAtomicSet<String>()
        set.add("item1")
        set.add("item2")
        set.add("item3")
        val visited = mutableListOf<String>()
        set.accessAll { visited.add(it) }

        assertEquals(3, set.size())
        assertEquals(listOf("item3", "item2", "item1"), visited)
        assertEquals("item3", set.fetch { it.startsWith("item") })
        assertTrue(set.contains("item1"))
        assertFalse(set.contains("missing"))
    }
}
