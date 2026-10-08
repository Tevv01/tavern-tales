package dev.tevv.taverntales.data

import dev.tevv.taverntales.R

/** The pictures of the built-in scenes: `builtin:<key>` background values map to drawables. */
object BuiltinBackgrounds {
    private const val PREFIX = "builtin:"

    private val drawables = mapOf(
        "town" to R.drawable.bg_town,
        "tavern" to R.drawable.bg_tavern,
        "dungeon" to R.drawable.bg_dungeon,
        "market" to R.drawable.bg_market,
        "forest" to R.drawable.bg_forest,
        "cave" to R.drawable.bg_cave,
    )

    fun isBuiltin(background: String?): Boolean = background?.startsWith(PREFIX) == true

    /** The drawable for a `builtin:<key>` background, or null for anything else. */
    fun drawableFor(background: String?): Int? =
        background?.takeIf(::isBuiltin)?.let { drawables[it.removePrefix(PREFIX)] }
}
