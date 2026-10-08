package dev.tevv.taverntales.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.tevv.taverntales.model.layerNameFromFileName

/** An audio file picked by the user: a display name derived from its file name, and its URI. */
data class ImportedAudio(val name: String, val uri: String)

/**
 * Prepares an audio file picked with the system file picker (OpenDocument) for use as a layer or event.
 * The file stays where it is; the app keeps a persistable read permission so it survives restarts.
 */
fun importAudio(context: Context, uri: Uri): ImportedAudio {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val fileName = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty()
    return ImportedAudio(layerNameFromFileName(fileName), uri.toString())
}
