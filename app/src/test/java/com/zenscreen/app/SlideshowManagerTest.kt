package com.zenscreen.app

import org.junit.Assert.*
import org.junit.Test

class SlideshowManagerTest {

    @Test
    fun `next cycles through files in order`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        assertEquals("a", mgr.current())
        mgr.next()
        assertEquals("b", mgr.current())
        mgr.next()
        assertEquals("c", mgr.current())
        mgr.next()
        assertEquals("a", mgr.current()) // loop
    }

    @Test
    fun `prev goes back`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        mgr.next() // b
        mgr.prev()
        assertEquals("a", mgr.current())
    }

    @Test
    fun `prev from first wraps to last`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        mgr.prev()
        assertEquals("c", mgr.current())
    }

    @Test
    fun `isEmpty returns true for empty list`() {
        assertTrue(SlideshowManager<String>(emptyList()).isEmpty())
    }

    @Test
    fun `reload updates list and resets index`() {
        val mgr = SlideshowManager(listOf("a", "b"))
        mgr.next()
        mgr.reload(listOf("x", "y", "z"))
        assertEquals("x", mgr.current())
        assertEquals(3, mgr.size())
    }
}
