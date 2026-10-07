package dev.tevv.taverntales.data

import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCodecTest {
    @Test
    fun roundTrip_preservesLibrary() {
        val library = Library(
            collections = listOf(
                SceneCollection(
                    id = "c1",
                    name = "Mine",
                    scenes = listOf(
                        Scene(
                            id = "town",
                            name = "Town",
                            background = "file:///data/backgrounds/x.jpg",
                            layers = listOf(
                                SoundLayer(id = "a", name = "Crowd", uri = "content://a", volume = 0.4f, autoPlay = false),
                                SoundLayer(id = "b", name = "Bell", uri = "content://b", loop = false),
                            ),
                        ),
                    ),
                ),
                SceneCollection(id = "c2", name = "Empty"),
            ),
            events = listOf(SoundEvent("e", "Boom", "content://boom", volume = 0.5f, icon = "explosion", color = "crimson")),
        )
        assertEquals(library, LibraryCodec.decode(LibraryCodec.encode(library)))
    }

    @Test
    fun decode_fillsDefaultsAndIgnoresUnknownFields() {
        val json = """
            {
              "version": 2,
              "futureField": true,
              "collections": [
                { "id": "c", "name": "C", "scenes": [
                  { "id": "town", "name": "Town", "lighting": { "hue": 40 },
                    "layers": [ { "id": "a", "name": "Crowd", "uri": "content://a" } ] } ] }
              ],
              "events": [ { "id": "e", "name": "Boom", "uri": "content://boom" } ]
            }
        """.trimIndent()
        val library = LibraryCodec.decode(json)
        assertEquals(SoundLayer(id = "a", name = "Crowd", uri = "content://a"), library.collections.single().scenes.single().layers.single())
        assertEquals(SoundEvent(id = "e", name = "Boom", uri = "content://boom"), library.events.single())
    }

    @Test
    fun decode_migratesVersion1IntoOwnCollectionNextToDefaults() {
        val v1 = """
            { "version": 1, "scenes": [ { "id": "s", "name": "My town", "layers": [] } ] }
        """.trimIndent()
        val library = LibraryCodec.decode(v1)
        val defaults = DefaultLibrary.create()
        assertEquals(defaults.collections.first(), library.collections.first())
        assertEquals("My scenes", library.collections[1].name)
        assertEquals(listOf(Scene(id = "s", name = "My town")), library.collections[1].scenes)
        assertEquals(defaults.events, library.events)
    }

    @Test
    fun decode_emptyVersion1GivesJustTheDefaults() {
        assertEquals(DefaultLibrary.create(), LibraryCodec.decode("""{ "version": 1, "scenes": [] }"""))
    }
}
