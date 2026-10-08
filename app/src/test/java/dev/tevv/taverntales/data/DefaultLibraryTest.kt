package dev.tevv.taverntales.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DefaultLibraryTest {
    private val library = DefaultLibrary.create()

    @Test
    fun idsAreUnique() {
        val scenes = library.collections.flatMap { it.scenes }
        val ids = library.collections.map { it.id } + scenes.map { it.id } + scenes.flatMap { s -> s.layers.map { it.id } } +
            library.events.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyReferencedSoundIsBundled() {
        // Unit tests run with the module directory as working directory.
        val uris = library.collections.flatMap { it.scenes }.flatMap { s -> s.layers.map { it.uri } } + library.events.map { it.uri }
        uris.forEach { uri ->
            val path = uri.removePrefix("asset:///")
            assertTrue("missing asset $path", File("src/main/assets/$path").isFile)
        }
    }

    @Test
    fun everyBuiltinSceneHasLighting() {
        library.collections.flatMap { it.scenes }.forEach { scene ->
            val lighting = scene.lighting
            assertTrue("${scene.name} has no lighting", lighting != null && lighting.slots.isNotEmpty())
            lighting!!.slots.forEach { assertTrue("${scene.name}: bad colour ${it.color}", Regex("#[0-9A-F]{6}").matches(it.color)) }
        }
    }

    @Test
    fun everyBuiltinBackgroundHasAnImage() {
        library.collections.flatMap { it.scenes }.forEach { scene ->
            val key = scene.background!!.removePrefix("builtin:")
            assertTrue("missing bg_$key.webp", File("src/main/res/drawable-nodpi/bg_$key.webp").isFile)
        }
    }
}
