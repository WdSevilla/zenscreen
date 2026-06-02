package com.freekiosk.app

import org.junit.Assert.*
import org.junit.Test

class PrefsTest {
    @Test
    fun `default PIN is 1234`() {
        assertEquals("1234", Prefs.DEFAULT_PIN)
    }

    @Test
    fun `default slideshow interval is 10 seconds`() {
        assertEquals(10L, Prefs.DEFAULT_INTERVAL_SECONDS)
    }
}
