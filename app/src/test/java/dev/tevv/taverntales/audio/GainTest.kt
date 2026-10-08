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

    @Test
    fun fadeCurve_isEqualPowerSoACrossfadeKeepsLoudnessEven() {
        assertEquals(0f, AmbienceMixer.fadeCurve(0f), 0f)
        assertEquals(1f, AmbienceMixer.fadeCurve(1f), 1e-6f)
        assertEquals(1f, AmbienceMixer.fadeCurve(2f), 1e-6f)
        for (i in 0..20) {
            val t = i / 20f
            val incoming = AmbienceMixer.fadeCurve(t)
            val outgoing = AmbienceMixer.fadeCurve(1f - t)
            assertEquals("power at $t", 1f, incoming * incoming + outgoing * outgoing, 1e-5f)
        }
    }
}
