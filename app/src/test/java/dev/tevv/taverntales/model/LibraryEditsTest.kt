package dev.tevv.taverntales.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryEditsTest {
    private val rain = SoundLayer(id = "rain", name = "Rain", uri = "content://rain")
    private val wind = SoundLayer(id = "wind", name = "Wind", uri = "content://wind")
    private val town = Scene(id = "town", name = "Town", layers = listOf(rain, wind))
    private val forest = Scene(id = "forest", name = "Forest")
    private val cave = Scene(id = "cave", name = "Cave")
    private val fire = SoundEvent(id = "fire", name = "Fire", uri = "asset:///fire.ogg")
    private val library = Library(
        collections = listOf(
            SceneCollection("a", "Adventure", listOf(town, forest)),
            SceneCollection("b", "Horror", listOf(cave)),
        ),
        events = listOf(fire),
    )

    @Test
    fun findScene_searchesAllCollections() {
        assertEquals(cave, library.findScene("cave"))
        assertEquals("b", library.collectionOf("cave")?.id)
        assertNull(library.findScene("missing"))
    }

    @Test
    fun updateScene_changesOnlyMatchingScene() {
        val result = library.updateScene("forest") { it.copy(name = "Dark forest") }
        assertEquals("Dark forest", result.findScene("forest")?.name)
        assertEquals(town, result.findScene("town"))
        assertEquals(cave, result.findScene("cave"))
    }

    @Test
    fun moveScene_appendsToTargetAndRemovesFromSource() {
        val result = library.moveScene("town", "b")
        assertEquals(listOf(forest), result.collections[0].scenes)
        assertEquals(listOf(cave, town), result.collections[1].scenes)
    }

    @Test
    fun moveScene_ignoresUnknownTargetOrSameCollection() {
        assertSame(library, library.moveScene("town", "nope"))
        assertSame(library, library.moveScene("town", "a"))
        assertSame(library, library.moveScene("nope", "b"))
    }

    @Test
    fun removeScene_keepsOtherScenes() {
        val result = library.removeScene("forest")
        assertEquals(listOf(town), result.collections[0].scenes)
        assertEquals(listOf(cave), result.collections[1].scenes)
    }

    @Test
    fun collectionsStartExpandedAndCollapseIndividually() {
        assertEquals(listOf(false, false), library.collections.map { it.collapsed })
        val result = library.updateCollection("b") { it.copy(collapsed = true) }
        assertEquals(listOf(false, true), result.collections.map { it.collapsed })
    }

    @Test
    fun updateEvent_changesOnlyMatchingEvent() {
        val result = library.updateEvent("fire") { it.copy(color = "crimson") }
        assertEquals("crimson", result.events.single().color)
    }

    @Test
    fun updateLayer_changesOnlyMatchingLayer() {
        val result = town.updateLayer("wind") { it.copy(volume = 0.2f) }
        assertEquals(listOf(rain, wind.copy(volume = 0.2f)), result.layers)
    }

    @Test
    fun removeLayer_keepsOrderOfOthers() {
        assertEquals(listOf(wind), town.removeLayer("rain").layers)
    }

    @Test
    fun layerNameFromFileName_cleansUpFileNames() {
        assertEquals("Tavern music loop", layerNameFromFileName("tavern_music-loop.ogg"))
        assertEquals("Rain heavy", layerNameFromFileName("rain  heavy.mp3"))
        assertEquals("Wind", layerNameFromFileName("wind"))
        assertEquals(".hidden", layerNameFromFileName(".hidden"))
        assertEquals("Sound", layerNameFromFileName("___.wav"))
        assertEquals("Sound", layerNameFromFileName(""))
    }
}
