package dev.tevv.taverntales.hue

import kotlin.math.pow

/** CIE xy chromaticity, the colour space Hue lights are addressed in. */
data class Xy(val x: Double, val y: Double)

/** Colour conversions for driving Hue lights from `#RRGGBB` colours. */
object LightMath {
    private val WHITE = Xy(0.3227, 0.3290)

    fun parseHex(hex: String): Triple<Double, Double, Double>? {
        val digits = hex.removePrefix("#")
        if (digits.length != 6) return null
        val value = digits.toIntOrNull(16) ?: return null
        return Triple((value shr 16 and 0xFF) / 255.0, (value shr 8 and 0xFF) / 255.0, (value and 0xFF) / 255.0)
    }

    /** sRGB to xy using the wide-gamut conversion Philips recommends; the bridge fits it to each bulb's gamut. */
    fun hexToXy(hex: String): Xy {
        val (r, g, b) = parseHex(hex)?.let { (r, g, b) -> Triple(linear(r), linear(g), linear(b)) } ?: return WHITE
        val x = r * 0.664511 + g * 0.154324 + b * 0.162028
        val y = r * 0.283881 + g * 0.668433 + b * 0.047685
        val z = r * 0.000088 + g * 0.072310 + b * 0.986039
        val sum = x + y + z
        return if (sum <= 0.0) WHITE else Xy(round4(x / sum), round4(y / sum))
    }

    /**
     * Colour temperature in mirek for white-only bulbs, from the colour's xy (McCamy's approximation),
     * clamped to what the bulb supports.
     */
    fun hexToMirek(hex: String, min: Int = 153, max: Int = 500): Int {
        val (x, y) = hexToXy(hex)
        val n = (x - 0.3320) / (0.1858 - y)
        val kelvin = 449 * n.pow(3) + 3525 * n.pow(2) + 6823.3 * n + 5520.33
        return (1_000_000 / kelvin.coerceIn(1000.0, 12000.0)).toInt().coerceIn(min, max)
    }

    private fun linear(c: Double) = if (c > 0.04045) ((c + 0.055) / 1.055).pow(2.4) else c / 12.92

    private fun round4(v: Double) = kotlin.math.round(v * 10_000) / 10_000
}
