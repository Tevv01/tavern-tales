package dev.tevv.taverntales.data.backup

import dev.tevv.taverntales.data.LibraryCodec
import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.newId
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes and reads backup zips (see [BackupFormat]). Plain JVM code: Android specifics (document
 * pickers, content URIs) are passed in as streams and functions, so a full round trip is unit-tested.
 */
object BackupZip {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Writes [library] to [out]. Pictures are taken from [backgroundsDir] (only `file:` references into
     * it). Sounds that aren't built in are read with [openSound]; one that returns null (file gone or
     * no longer accessible) keeps its original reference and is counted as missing.
     */
    fun write(
        out: OutputStream,
        library: Library,
        manifest: BackupManifest,
        backgroundsDir: File,
        openSound: (uri: String) -> InputStream?,
    ): BackupSummary {
        val pictures = mutableMapOf<String, String>()
        val sounds = mutableMapOf<String, String>()
        var missing = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putEntry(BackupFormat.MANIFEST) { it.write(json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray()) }

            for (ref in BackupFormat.references(library)) {
                val file = localFile(ref, backgroundsDir)
                if (file != null && file.isFile) {
                    val entry = BackupFormat.BACKGROUNDS + file.name
                    zip.putEntry(entry) { file.inputStream().use { input -> input.copyTo(it) } }
                    pictures[ref] = BackupFormat.REF_PREFIX + entry
                }
            }

            val userSounds = library.collections.flatMap { it.scenes }.flatMap { s -> s.layers.map { it.uri } } +
                library.events.map { it.uri }
            for (uri in userSounds.distinct().filterNot { it.startsWith("asset:") }) {
                val input = runCatching { openSound(uri) }.getOrNull()
                if (input == null) {
                    missing++
                    continue
                }
                val entry = BackupFormat.AUDIO + "s${sounds.size + 1}"
                input.use { stream -> zip.putEntry(entry) { stream.copyTo(it) } }
                sounds[uri] = BackupFormat.REF_PREFIX + entry
            }

            val packed = BackupFormat.mapReferences(library, sound = { sounds[it] ?: it }, picture = { pictures[it] ?: it })
            zip.putEntry(BackupFormat.LIBRARY) { it.write(LibraryCodec.encode(packed).encodeToByteArray()) }
        }
        val scenes = library.collections.sumOf { it.scenes.size }
        return BackupSummary(scenes, pictures.size, sounds.size, missing)
    }

    /**
     * Reads a backup from [input]. Pictures go to [backgroundsDir] and sounds to [audioDir] under new
     * names, and the returned library refers to them with `file://` URIs. Throws [BackupException] if
     * this isn't a Tavern Tales backup or it comes from a newer app version.
     */
    fun read(input: InputStream, backgroundsDir: File, audioDir: File): Library {
        var manifest: BackupManifest? = null
        var libraryText: String? = null
        val extracted = mutableMapOf<String, File>() // backup entry -> file written
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !BackupFormat.isAllowedEntry(entry.name)) continue
                    when {
                        entry.name == BackupFormat.MANIFEST ->
                            manifest = runCatching { json.decodeFromString<BackupManifest>(zip.readBytes().decodeToString()) }.getOrNull()
                        entry.name == BackupFormat.LIBRARY -> libraryText = zip.readBytes().decodeToString()
                        else -> {
                            val dir = if (entry.name.startsWith(BackupFormat.BACKGROUNDS)) backgroundsDir else audioDir
                            dir.mkdirs()
                            val target = File(dir, "${newId()}${extension(entry.name)}")
                            target.outputStream().use { zip.copyTo(it) }
                            extracted[BackupFormat.REF_PREFIX + entry.name] = target
                        }
                    }
                }
            }
            val found = manifest
            if (found == null || found.format != BackupFormat.FORMAT) throw BackupException("This isn't a Tavern Tales backup")
            if (found.version > BackupFormat.VERSION) throw BackupException("This backup was made by a newer version of the app")
            val text = libraryText ?: throw BackupException("The backup has no library in it")
            val library = runCatching { LibraryCodec.decode(text) }.getOrElse { throw BackupException("The backup's library can't be read") }
            val resolve: (String) -> String = { ref -> extracted[ref]?.let(::fileUri) ?: ref }
            return BackupFormat.mapReferences(library, sound = resolve, picture = resolve)
        } catch (e: Exception) {
            extracted.values.forEach { it.delete() } // don't leave half a restore behind
            if (e is BackupException) throw e
            throw BackupException("The backup file is damaged or incomplete")
        }
    }

    /** Deletes files in [dirs] that [library] no longer refers to (left over after replacing or deleting). */
    fun deleteUnreferenced(library: Library, vararg dirs: File) {
        val referenced = BackupFormat.references(library).mapNotNull { ref -> dirs.firstNotNullOfOrNull { localFile(ref, it) } }.toSet()
        dirs.flatMap { it.listFiles()?.toList().orEmpty() }.filter { it.isFile && it !in referenced }.forEach { it.delete() }
    }

    fun fileUri(file: File): String = "file://" + file.absolutePath.replace('\\', '/').let { if (it.startsWith("/")) it else "/$it" }

    /** The file in [dir] that a `file://` reference points to, or null. */
    private fun localFile(ref: String, dir: File): File? {
        if (!ref.startsWith("file://")) return null
        val name = ref.substringAfterLast('/')
        val file = File(dir, name)
        return file.takeIf { fileUri(it) == ref || ref.endsWith("/" + dir.name + "/" + name) }
    }

    private fun extension(entryName: String) = entryName.substringAfterLast('/').let { name ->
        if ('.' in name) "." + name.substringAfterLast('.') else ""
    }

    private inline fun ZipOutputStream.putEntry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }
}
