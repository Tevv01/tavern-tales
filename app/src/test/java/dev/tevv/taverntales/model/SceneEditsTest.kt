package dev.tevv.taverntales.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneEditsTest {
    private val rain = SoundLayer(id = "rain", name = "Rain", uri = "content://rain")
    private val wind = SoundLayer(id = "wind", name = "Wind", uri = "content://wind")
    private val town = Scene(id = "town", name = "Town", layers = listOf(rain, wind))
    private val forest = Scene(id = "forest", name = "Forest")

    @Test
    fun updateScene_changesOnlyMatchingScene() {
        val result = listOf(town, forest).updateScene("forest") { it.copy(name = "Dark forest") }
        assertEquals(listOf(town, forest.copy(name = "Dark forest")), result)
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
