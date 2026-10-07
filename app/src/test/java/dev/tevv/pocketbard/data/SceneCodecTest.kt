package dev.tevv.pocketbard.data

import dev.tevv.pocketbard.model.Scene
import dev.tevv.pocketbard.model.SoundLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneCodecTest {
    @Test
    fun roundTrip_preservesScenes() {
        val scenes = listOf(
            Scene(
                id = "town",
                name = "Town",
                layers = listOf(
                    SoundLayer(id = "a", name = "Crowd", uri = "content://a", volume = 0.4f, autoPlay = false),
                    SoundLayer(id = "b", name = "Bell", uri = "content://b", loop = false),
                ),
            ),
            Scene(id = "forest", name = "Forest"),
        )
        assertEquals(scenes, SceneCodec.decode(SceneCodec.encode(scenes)))
    }

    @Test
    fun decode_fillsDefaultsAndIgnoresUnknownFields() {
        val json = """
            {
              "version": 1,
              "futureField": true,
              "scenes": [
                { "id": "town", "name": "Town", "lighting": { "hue": 40 },
                  "layers": [ { "id": "a", "name": "Crowd", "uri": "content://a" } ] }
              ]
            }
        """.trimIndent()
        val layer = SceneCodec.decode(json).single().layers.single()
        assertEquals(SoundLayer(id = "a", name = "Crowd", uri = "content://a"), layer)
    }
}
