package com.freekiosk.app

class SlideshowManager<T>(items: List<T>) {
    private var list: List<T> = items.toList()
    private var index: Int = 0

    fun current(): T = list[index]
    fun isEmpty(): Boolean = list.isEmpty()
    fun size(): Int = list.size

    fun next() {
        if (list.isEmpty()) return
        index = (index + 1) % list.size
    }

    fun prev() {
        if (list.isEmpty()) return
        index = (index - 1 + list.size) % list.size
    }

    fun reload(items: List<T>) {
        list = items.toList()
        index = 0
    }
}
