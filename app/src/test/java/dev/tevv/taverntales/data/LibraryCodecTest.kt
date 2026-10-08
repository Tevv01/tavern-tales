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
                SceneCollection(id = "c2", name = "Empty", collapsed = true),
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
        val mine = library.collections.single { it.id == "c" }
        assertEquals(SoundLayer(id = "a", name = "Crowd", uri = "content://a"), mine.scenes.single().layers.single())
        assertEquals(SoundEvent(id = "e", name = "Boom", uri = "content://boom"), library.events.single())
    }

    @Test
    fun decode_migratesVersion1IntoOwnCollectionNextToDefaults() {
        val v1 = """
            { "version": 1, "scenes": [ { "id": "s", "name": "My town", "layers": [] } ] }
        """.trimIndent()
        val library = LibraryCodec.decode(v1)
        val defaults = DefaultLibrary.create()
        assertEquals(defaults.collections, library.collections.dropLast(1))
        assertEquals("My scenes", library.collections.last().name)
        assertEquals(listOf(Scene(id = "s", name = "My town")), library.collections.last().scenes)
        assertEquals(defaults.events, library.events)
    }

    @Test
    fun decode_version2AddsDefaultLightingOnlyWhereLightsAreUnset() {
        val v2 = """
            { "version": 2, "events": [], "collections": [ { "id": "default", "name": "Essentials", "scenes": [
              { "id": "default-tavern", "name": "Tavern" },
              { "id": "default-town", "name": "Town", "lights": { "id": "hue-1", "name": "Relax" } },
              { "id": "mine", "name": "Mine" } ] } ] }
        """.trimIndent()
        val scenes = LibraryCodec.decode(v2).collections.flatMap { it.scenes }.associateBy { it.id }
        assertEquals(DefaultLibrary.lighting["tavern"], scenes.getValue("default-tavern").lighting)
        assertEquals(null, scenes.getValue("default-town").lighting) // user already linked a Hue scene
        assertEquals(null, scenes.getValue("mine").lighting) // not a built-in scene
    }

    @Test
    fun decode_version3AddsDefaultMotionKeepingUserColours() {
        val v3 = """
            { "version": 3, "events": [], "collections": [ { "id": "default", "name": "Essentials", "scenes": [
              { "id": "default-cave", "name": "Cave", "lighting": { "slots": [ { "color": "#112233" } ], "brightness": 0.9 } },
              { "id": "mine", "name": "Mine", "lighting": { "slots": [ { "color": "#112233" } ], "brightness": 0.9 } } ] } ] }
        """.trimIndent()
        val scenes = LibraryCodec.decode(v3).collections.flatMap { it.scenes }.associateBy { it.id }
        val cave = scenes.getValue("default-cave").lighting!!
        assertEquals(DefaultLibrary.lighting.getValue("cave").motion, cave.motion)
        assertEquals("#112233", cave.slots.single().color) // the user's edits survive
        assertEquals(0.9f, cave.brightness)
        assertEquals(0f, scenes.getValue("mine").lighting!!.motion) // the user's own scenes stay still
    }

    @Test
    fun decode_version4AddsDefaultFlashesToBuiltInEventsOnly() {
        val v4 = """
            { "version": 4, "collections": [], "events": [
              { "id": "event-thunder", "name": "Thunder", "uri": "asset:///sounds/event_thunder.ogg" },
              { "id": "event-fire", "name": "Fireball", "uri": "asset:///sounds/event_fire.ogg", "flash": { "style": "glow", "color": "#112233" } },
              { "id": "event-arrows", "name": "Arrow volley", "uri": "asset:///sounds/event_arrows.ogg" },
              { "id": "mine", "name": "Door", "uri": "content://door" } ] }
        """.trimIndent()
        val events = LibraryCodec.decode(v4).events.associateBy { it.id }
        assertEquals(DefaultLibrary.flashes["event-thunder"], events.getValue("event-thunder").flash)
        assertEquals("#112233", events.getValue("event-fire").flash!!.color) // the user's own choice survives
        assertEquals(null, events.getValue("event-arrows").flash) // built in, but without a flash
        assertEquals(null, events.getValue("mine").flash)
    }

    @Test
    fun decode_emptyVersion1GivesJustTheDefaults() {
        assertEquals(DefaultLibrary.create(), LibraryCodec.decode("""{ "version": 1, "scenes": [] }"""))
    }

    private fun builtIn(key: String) = DefaultLibrary.create().collections.flatMap { it.scenes }.first { it.id == "default-$key" }

    /** How a v5 library looked: the six original scenes in "Essentials" (plus whatever the user did). */
    private fun essentials(vararg scenes: Scene, collapsed: Boolean = false) =
        SceneCollection(DefaultLibrary.LEGACY_COLLECTION_ID, "Essentials", scenes.toList(), collapsed)

    private fun decodeAsVersion5(library: Library): Library {
        val json = LibraryCodec.encode(library)
        val v5 = json.replace("\"version\": ${LibraryCodec.CURRENT_VERSION}", "\"version\": 5")
        check(v5 != json)
        return LibraryCodec.decode(v5)
    }

    @Test
    fun decode_version5GroupsTheBuiltInScenesByThemeKeepingTheUsersChanges() {
        val quietTavern = builtIn("tavern").let { it.copy(layers = it.layers.map { l -> l.copy(volume = 0.1f) }) }
        val campaign = SceneCollection("mine", "Campaign", listOf(Scene("harbour", "Harbour")))
        val old = Library(
            collections = listOf(
                essentials(builtIn("town"), quietTavern, builtIn("dungeon"), builtIn("market"), builtIn("forest"), builtIn("cave"), collapsed = true),
                campaign,
            ),
        )
        val migrated = decodeAsVersion5(old)
        assertEquals(listOf("Settlements", "Wilderness", "Underground", "Campaign"), migrated.collections.map { it.name })
        assertEquals(DefaultLibrary.create().collections.map { it.scenes.map { s -> s.id } }, migrated.collections.take(3).map { it.scenes.map { s -> s.id } })
        assertEquals(quietTavern, migrated.collections[0].scenes[1]) // the user's mix survives the move
        assertEquals(listOf(true, true, true, false), migrated.collections.map { it.collapsed }) // folded like Essentials was
        assertEquals(migrated, LibraryCodec.decode(LibraryCodec.encode(migrated))) // and nothing changes on the next load
    }

    @Test
    fun decode_version5LeavesMovedDeletedAndOwnScenesAlone() {
        val old = Library(
            collections = listOf(
                // Market deleted, Cave moved to the campaign, the user's own scene added to Essentials.
                essentials(builtIn("town"), builtIn("tavern"), Scene("harbour", "Harbour"), builtIn("dungeon"), builtIn("forest")),
                SceneCollection("mine", "Campaign", listOf(builtIn("cave"))),
            ),
        )
        val migrated = decodeAsVersion5(old).collections.associate { it.name to it.scenes.map { s -> s.id.removePrefix("default-") } }
        assertEquals(listOf("Settlements", "Wilderness", "Underground", "Essentials", "Campaign"), migrated.keys.toList())
        assertEquals(listOf("town", "tavern", "castle", "temple"), migrated["Settlements"])
        assertEquals(listOf("forest", "swamp", "blizzard", "ship"), migrated["Wilderness"])
        assertEquals(listOf("dungeon"), migrated["Underground"])
        assertEquals(listOf("harbour"), migrated["Essentials"])
        assertEquals(listOf("cave"), migrated["Campaign"])
    }

    @Test
    fun decode_version5WithoutEssentialsGetsOnlyTheNewScenes() {
        val migrated = decodeAsVersion5(Library(collections = listOf(SceneCollection("mine", "Campaign"))))
        val byName = migrated.collections.associate { it.name to it.scenes.map { s -> s.id.removePrefix("default-") } }
        assertEquals(listOf("Campaign", "Settlements", "Wilderness"), byName.keys.toList()) // no new underground scenes
        assertEquals(listOf("castle", "temple"), byName["Settlements"])
        assertEquals(listOf("swamp", "blizzard", "ship"), byName["Wilderness"])
    }
}
