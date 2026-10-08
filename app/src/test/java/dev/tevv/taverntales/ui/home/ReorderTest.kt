package dev.tevv.taverntales.ui.home

import androidx.compose.foundation.lazy.LazyListState
import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.withCollectionOrder
import dev.tevv.taverntales.model.withSceneOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    @Test
    fun movedTakesAnItemToItsNewPlace() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moved(order, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), moved(order, "d", 0))
        assertEquals(order, moved(order, "b", 1))
        assertEquals(order, moved(order, "missing", 0))
        assertEquals(listOf("b", "c", "d", "a"), moved(order, "a", 9)) // clamped to the end
    }

    private val library = Library(
        collections = listOf(
            SceneCollection("x", "X", listOf(Scene("1", "One"), Scene("2", "Two"), Scene("3", "Three"))),
            SceneCollection("y", "Y"),
            SceneCollection("z", "Z"),
        ),
    )

    @Test
    fun collectionOrderIsAppliedAndUnlistedOnesKeepTheirOrderAtTheEnd() {
        assertEquals(listOf("z", "x", "y"), library.withCollectionOrder(listOf("z", "x", "y")).collections.map { it.id })
        assertEquals(listOf("y", "x", "z"), library.withCollectionOrder(listOf("y")).collections.map { it.id })
    }

    @Test
    fun sceneOrderOnlyChangesThatCollection() {
        val result = library.withSceneOrder("x", listOf("3", "1", "2"))
        assertEquals(listOf("3", "1", "2"), result.collections[0].scenes.map { it.id })
        assertEquals(library.collections.drop(1), result.collections.drop(1))
    }

    @Test
    fun aCancelWithoutADragLeavesTheListAlone() {
        // Collection headers report a cancel when they scroll out of view; that once emptied the list.
        val state = CollectionReorderState(LazyListState())
        val ids = listOf("x", "y", "z")
        assertEquals(null, state.end(ids))
        assertEquals(false, state.reordering)
        assertEquals(ids, state.shown(ids))
    }
}
