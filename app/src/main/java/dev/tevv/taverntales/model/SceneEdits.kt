package dev.tevv.taverntales.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun List<Scene>.updateScene(sceneId: String, transform: (Scene) -> Scene): List<Scene> =
    map { if (it.id == sceneId) transform(it) else it }

fun Scene.updateLayer(layerId: String, transform: (SoundLayer) -> SoundLayer): Scene =
    copy(layers = layers.map { if (it.id == layerId) transform(it) else it })

fun Scene.removeLayer(layerId: String): Scene =
    copy(layers = layers.filterNot { it.id == layerId })

/** Turns an imported file name like `tavern_music-loop.ogg` into a layer name like `Tavern music loop`. */
fun layerNameFromFileName(fileName: String): String {
    val base = fileName.substringBeforeLast('.').ifEmpty { fileName }
    val words = base.replace(Regex("[_\\-]+"), " ").replace(Regex("\\s+"), " ").trim()
    return words.replaceFirstChar { it.uppercase() }.ifEmpty { "Sound" }
}
