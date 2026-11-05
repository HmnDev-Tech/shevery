package moe.shizuku.manager.adb

import java.util.ArrayDeque

internal class AdbResolveQueue<T> {
    private val queue = ArrayDeque<T>()

    fun addLast(item: T) {
        queue.addLast(item)
    }

    fun retryFirst(item: T) {
        queue.remove(item)
        queue.addFirst(item)
    }

    fun removeAll(predicate: (T) -> Boolean) {
        queue.removeAll(predicate)
    }

    fun isEmpty(): Boolean = queue.isEmpty()

    fun isNotEmpty(): Boolean = queue.isNotEmpty()

    fun removeFirst(): T = queue.removeFirst()

    fun size(): Int = queue.size
}
