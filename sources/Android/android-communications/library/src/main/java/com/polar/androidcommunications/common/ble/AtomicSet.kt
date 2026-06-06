package com.polar.androidcommunications.common.ble

import com.polar.shared.ble.PolarAtomicSet

class AtomicSet<Any : kotlin.Any> {

    private val items: PolarAtomicSet<Any> = PolarAtomicSet()

    fun interface CompareFunction<Any> {
        fun compare(`object`: Any): Boolean
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    fun add(`object`: Any?): Boolean {
        return items.add(`object`)
    }

    @Synchronized
    fun <T: Any> remove(`object`: T?) {
        items.remove(`object`)
    }

    @Synchronized
    fun <T: Any> accessAll(`object`: (kotlin.Any) -> Unit) {
        items.accessAll { item -> `object`(item) }
    }

    @Synchronized
    fun fetch(compareFunction: CompareFunction<Any?>): Any? {
        return items.fetch { item -> compareFunction.compare(item) }
    }

    @Synchronized
    fun objects(): Set<Any> {
        return HashSet(items.objects())
    }

    @Synchronized
    fun size(): Int {
        return items.size()
    }

    @Synchronized
    fun contains(`object`: Any): Boolean {
        return items.contains(`object`)
    }
}
