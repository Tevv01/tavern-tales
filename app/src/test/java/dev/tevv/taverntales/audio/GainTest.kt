package dev.tevv.taverntales.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class GainTest {
    @Test
    fun gain_isSquaredAndClamped() {
        assertEquals(0f, AmbienceMixer.gain(0f), 0f)
        assertEquals(0.25f, AmbienceMixer.gain(0.5f), 1e-6f)
        assertEquals(1f, AmbienceMixer.gain(1f), 0f)
        assertEquals(1f, AmbienceMixer.gain(1.5f), 0f)
        assertEquals(0f, AmbienceMixer.gain(-1f), 0f)
    }
}
