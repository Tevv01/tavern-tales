package dev.tevv.taverntales.data.backup

import dev.tevv.taverntales.data.DefaultLibrary
import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    @get:Rule val temp = TemporaryFolder()

    private fun library(backgroundsDir: File): Library {
        val picture = File(backgroundsDir.apply { mkdirs() }, "pic-1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        return Library(
            collections = listOf(
                SceneCollection(
                    "c1", "Campaign",
                    listOf(
                        Scene(
                            id = "s1",
                            name = "Swamp",
                            background = BackupZip.fileUri(picture),
                            layers = listOf(
                                SoundLayer("l1", "Frogs", "content://media/frogs"),
                                SoundLayer("l2", "Rain", "asset:///sounds/rain.ogg"),
                                SoundLayer("l3", "Lost", "content://media/gone"),
                            ),
                        ),
                    ),
                ),
            ),
            events = listOf(SoundEvent("e1", "Croak", "content://media/frogs")),
        )
    }

    private val sounds = mapOf("content://media/frogs" to byteArrayOf(9, 8, 7))

    @Test
    fun roundTripRestoresEverythingWithFilesCopied() {
        val original = library(temp.newFolder("bg-src"))
        val out = ByteArrayOutputStream()
        val summary = BackupZip.write(out, original, BackupManifest(created = "now"), temp.root.resolve("bg-src")) {
            sounds[it]?.let(::ByteArrayInputStream)
        }
        assertEquals(BackupSummary(scenes = 1, pictures = 1, sounds = 1, missingSounds = 1), summary)

        val bgDir = temp.newFolder("bg")
        val audioDir = temp.newFolder("audio")
        val restored = BackupZip.read(ByteArrayInputStream(out.toByteArray()), bgDir, audioDir)

        val scene = restored.collections.single().scenes.single()
        assertEquals("Swamp", scene.name)
        // Picture and own sound now point at copies in app storage, with the same content.
        val picture = File(bgDir, scene.background!!.substringAfterLast('/'))
        assertTrue(scene.background.startsWith("file://"))
        assertEquals(listOf<Byte>(1, 2, 3), picture.readBytes().toList())
        val frogs = scene.layers[0].uri
        assertEquals(listOf<Byte>(9, 8, 7), File(audioDir, frogs.substringAfterLast('/')).readBytes().toList())
        // The same sound used twice is stored once and both references point at it.
        assertEquals(frogs, restored.events.single().uri)
        // Built-in sounds are left as they are; unreadable sounds keep their old reference.
        assertEquals("asset:///sounds/rain.ogg", scene.layers[1].uri)
        assertEquals("content://media/gone", scene.layers[2].uri)
        assertEquals(1, audioDir.listFiles()!!.size)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertThrows(BackupException::class.java) {
            BackupZip.read(ByteArrayInputStream("not a zip".toByteArray()), temp.newFolder(), temp.newFolder())
        }
        val zip = zipOf(mapOf("library.json" to "{}", "manifest.json" to """{"format":"something-else"}"""))
        val error = assertThrows(BackupException::class.java) { BackupZip.read(zip, temp.newFolder(), temp.newFolder()) }
        assertEquals("This isn't a Tavern Tales backup", error.message)
    }

    @Test
    fun rejectsBackupsFromNewerVersions() {
        val zip = zipOf(mapOf("manifest.json" to """{"format":"tavern-tales-backup","version":99}""", "library.json" to "{}"))
        val error = assertThrows(BackupException::class.java) { BackupZip.read(zip, temp.newFolder(), temp.newFolder()) }
        assertEquals("This backup was made by a newer version of the app", error.message)
    }

    @Test
    fun ignoresEntriesOutsideTheExpectedFolders() {
        assertFalse(BackupFormat.isAllowedEntry("../../evil.sh"))
        assertFalse(BackupFormat.isAllowedEntry("audio/../../evil"))
        assertFalse(BackupFormat.isAllowedEntry("audio/sub/dir"))
        assertFalse(BackupFormat.isAllowedEntry("/etc/passwd"))
        assertTrue(BackupFormat.isAllowedEntry("audio/s1"))
        assertTrue(BackupFormat.isAllowedEntry("backgrounds/abc-1.jpg"))

        val bgDir = temp.newFolder("bg2")
        val zip = zipOf(
            mapOf(
                "manifest.json" to """{"format":"tavern-tales-backup","version":1}""",
                "library.json" to """{"version":4,"collections":[],"events":[]}""",
                "../escaped.txt" to "x",
                "backgrounds/../../escaped2.txt" to "x",
            ),
        )
        BackupZip.read(zip, bgDir, temp.newFolder("audio2"))
        assertFalse(File(temp.root, "escaped.txt").exists())
        assertFalse(File(temp.root, "escaped2.txt").exists())
        assertEquals(0, bgDir.listFiles()!!.size)
    }

    @Test
    fun olderLibraryFormatsInBackupsAreMigrated() {
        val zip = zipOf(
            mapOf(
                "manifest.json" to """{"format":"tavern-tales-backup","version":1}""",
                "library.json" to """{"version":1,"scenes":[{"id":"s","name":"Old"}]}""",
            ),
        )
        val restored = BackupZip.read(zip, temp.newFolder(), temp.newFolder())
        assertEquals("Old", restored.collections.last().scenes.single().name)
    }

    @Test
    fun mergeAddsWithFreshIdsAndRenamesClashingCollections() {
        val current = DefaultLibrary.create()
        val incoming = DefaultLibrary.create().copy(
            events = DefaultLibrary.events() + SoundEvent("mine", "Door slam", "content://door"),
        )
        val merged = BackupFormat.merge(current, incoming)
        assertEquals(listOf("Essentials", "Essentials (imported)"), merged.collections.map { it.name })
        val ids = merged.collections.flatMap { c -> listOf(c.id) + c.scenes.map { it.id } + c.scenes.flatMap { s -> s.layers.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(merged.collections[0].scenes[0].id, merged.collections[1].scenes[0].id)
        // Built-in events already present aren't duplicated; the new one is added.
        assertEquals(current.events.size + 1, merged.events.size)
        assertEquals("Door slam", merged.events.last().name)
    }

    @Test
    fun deleteUnreferencedKeepsOnlyFilesInUse() {
        val bgDir = temp.newFolder("bg3")
        val keep = File(bgDir, "keep.jpg").apply { writeText("k") }
        val stale = File(bgDir, "stale.jpg").apply { writeText("s") }
        val library = Library(listOf(SceneCollection("c", "C", listOf(Scene("s", "S", background = BackupZip.fileUri(keep))))))
        BackupZip.deleteUnreferenced(library, bgDir)
        assertTrue(keep.exists())
        assertFalse(stale.exists())
    }

    private fun zipOf(entries: Map<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }
}
