package dev.tevv.taverntales.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun Library.findScene(sceneId: String): Scene? =
    collections.firstNotNullOfOrNull { collection -> collection.scenes.find { it.id == sceneId } }

fun Library.collectionOf(sceneId: String): SceneCollection? =
    collections.find { collection -> collection.scenes.any { it.id == sceneId } }

fun Library.updateCollection(collectionId: String, transform: (SceneCollection) -> SceneCollection): Library =
    copy(collections = collections.map { if (it.id == collectionId) transform(it) else it })

fun Library.updateScene(sceneId: String, transform: (Scene) -> Scene): Library =
    copy(collections = collections.map { collection ->
        collection.copy(scenes = collection.scenes.map { if (it.id == sceneId) transform(it) else it })
    })

fun Library.addScene(collectionId: String, scene: Scene): Library =
    updateCollection(collectionId) { it.copy(scenes = it.scenes + scene) }

fun Library.removeScene(sceneId: String): Library =
    copy(collections = collections.map { collection -> collection.copy(scenes = collection.scenes.filterNot { it.id == sceneId }) })

/** Moves a scene to the end of another collection. No-op if either doesn't exist. */
fun Library.moveScene(sceneId: String, toCollectionId: String): Library {
    val scene = findScene(sceneId) ?: return this
    if (collections.none { it.id == toCollectionId } || collectionOf(sceneId)?.id == toCollectionId) return this
    return removeScene(sceneId).addScene(toCollectionId, scene)
}

/** Puts the collections in the order of [ids]; any not listed keep their order after them. */
fun Library.withCollectionOrder(ids: List<String>): Library =
    copy(collections = collections.sortedBy { c -> ids.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } })

/** Puts a collection's scenes in the order of [ids]; any not listed keep their order after them. */
fun Library.withSceneOrder(collectionId: String, ids: List<String>): Library = updateCollection(collectionId) { c ->
    c.copy(scenes = c.scenes.sortedBy { s -> ids.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it } })
}

fun Library.updateEvent(eventId: String, transform: (SoundEvent) -> SoundEvent): Library =
    copy(events = events.map { if (it.id == eventId) transform(it) else it })

fun Scene.updateLayer(layerId: String, transform: (SoundLayer) -> SoundLayer): Scene =
    copy(layers = layers.map { if (it.id == layerId) transform(it) else it })

fun Scene.removeLayer(layerId: String): Scene =
    copy(layers = layers.filterNot { it.id == layerId })

/** Turns an imported file name like `tavern_music-loop.ogg` into a display name like `Tavern music loop`. */
fun layerNameFromFileName(fileName: String): String {
    val base = fileName.substringBeforeLast('.').ifEmpty { fileName }
    val words = base.replace(Regex("[_\\-]+"), " ").replace(Regex("\\s+"), " ").trim()
    return words.replaceFirstChar { it.uppercase() }.ifEmpty { "Sound" }
}
