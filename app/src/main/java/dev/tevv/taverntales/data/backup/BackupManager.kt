package dev.tevv.taverntales.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Backs the library up to a file the user picks, and restores it. The Hue pairing is not part of a
 * backup. Restored pictures and sounds are copied into app storage (`backgrounds/`, `audio/`).
 */
class BackupManager(
    private val context: Context,
    private val repository: LibraryRepository,
    private val mixer: AmbienceMixer,
) {
    private val backgroundsDir = File(context.filesDir, "backgrounds")
    private val audioDir = File(context.filesDir, "audio")

    suspend fun backUp(target: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(target, "wt") ?: throw BackupException("Couldn't write the backup file")
        output.use { out ->
            BackupZip.write(
                out = out,
                library = repository.library.value,
                manifest = BackupManifest(created = Instant.now().toString(), appVersion = appVersion()),
                backgroundsDir = backgroundsDir,
                openSound = ::openSound,
            )
        }
    }

    /**
     * Restores the backup at [source]. With [replace], it becomes the whole library (playback is
     * stopped first, since the scenes it belongs to are replaced); otherwise its collections and events
     * are added. Returns the number of scenes restored.
     */
    suspend fun restore(source: Uri, replace: Boolean): Int {
        val incoming = withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(source) ?: throw BackupException("Couldn't open the backup file")
            input.use { BackupZip.read(it, backgroundsDir, audioDir) }
        }
        if (replace) {
            mixer.stopAll()
            repository.replace(incoming)
        } else {
            repository.merge(incoming)
        }
        withContext(Dispatchers.IO) { BackupZip.deleteUnreferenced(repository.library.value, backgroundsDir, audioDir) }
        return incoming.collections.sumOf { it.scenes.size }
    }

    private fun openSound(uri: String) = runCatching {
        val parsed = uri.toUri()
        if (parsed.scheme == "file") File(parsed.path!!).inputStream() else context.contentResolver.openInputStream(parsed)
    }.getOrNull()

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
