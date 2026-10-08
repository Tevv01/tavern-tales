package dev.tevv.taverntales.ui.info

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowTest {
    @Test
    fun joinsHardWrappedLinesAndKeepsParagraphs() {
        val text = "   Apache License\n   Version 2.0\r\n\r\n 1. Definitions.\n      \"License\" shall mean\n   the terms.\n\n\n\nEnd"
        assertEquals("Apache License Version 2.0\n\n1. Definitions. \"License\" shall mean the terms.\n\nEnd", reflow(text))
    }

    @Test
    fun blankLinesWithSpacesStillSeparateParagraphs() {
        assertEquals("a b\n\nc", reflow("a\nb\n   \nc"))
    }
}
