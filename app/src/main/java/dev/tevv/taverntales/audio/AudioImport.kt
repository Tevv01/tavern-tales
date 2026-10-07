package dev.tevv.taverntales.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.model.layerNameFromFileName
import dev.tevv.taverntales.model.newId

/**
 * Creates a [SoundLayer] for an audio file picked with the system file picker (OpenDocument).
 * The file stays where it is; the app keeps a persistable read permission so it survives restarts.
 */
fun importAudioLayer(context: Context, uri: Uri): SoundLayer {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val fileName = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty()
    return SoundLayer(id = newId(), name = layerNameFromFileName(fileName), uri = uri.toString())
}
