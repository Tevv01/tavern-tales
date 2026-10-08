package dev.tevv.taverntales.audio

import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaylistTest {
    private val library = Library(
        collections = listOf(
            SceneCollection("a", "Essentials", listOf(Scene("town", "Town"), Scene("tavern", "Tavern"))),
            SceneCollection("b", "Nights", listOf(Scene("camp", "Camp"), Scene("storm", "Storm"), Scene("dawn", "Dawn"))),
        ),
        events = emptyList(),
    )

    @Test
    fun theControlsStepThroughThePlayingScenesCollection() {
        val (collection, index) = mediaPlaylist(library, "storm")
        assertEquals("Nights", collection?.name)
        assertEquals(1, index)
        assertEquals(listOf("camp", "storm", "dawn"), collection?.scenes?.map { it.id })
    }

    @Test
    fun noSceneOrADeletedSceneGivesNoPlaylist() {
        assertNull(mediaPlaylist(library, null).first)
        assertEquals(-1, mediaPlaylist(library, "gone").second)
    }
}
