package com.polar.shared.ble

class PolarAtomicSet<T : Any> {
    private val items: MutableList<T> = mutableListOf()

    fun clear() {
        items.clear()
    }

    fun add(item: T?): Boolean {
        if (item != null && !items.contains(item)) {
            items.add(item)
            return true
        }
        return false
    }

    fun remove(item: T?) {
        if (item != null) {
            items.remove(item)
        }
    }

    fun accessAll(visitor: (T) -> Unit) {
        for (index in items.size - 1 downTo 0) {
            visitor(items[index])
        }
    }

    fun fetch(predicate: (T) -> Boolean): T? {
        for (index in items.size - 1 downTo 0) {
            if (predicate(items[index])) {
                return items[index]
            }
        }
        return null
    }

    fun objects(): Set<T> {
        return items.toSet()
    }

    fun size(): Int {
        return items.size
    }

    fun contains(item: T): Boolean {
        return items.contains(item)
    }
}
