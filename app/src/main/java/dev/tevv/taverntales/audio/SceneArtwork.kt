package dev.tevv.taverntales.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.net.toUri
import dev.tevv.taverntales.data.BuiltinBackgrounds
import dev.tevv.taverntales.model.Scene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Small copies of scene pictures for the media notification, the lock screen and the system media
 * player. Loaded in the background on first request; [updates] changes when one becomes available,
 * so callers ask again. Scenes without a picture have no artwork.
 */
class SceneArtwork(private val context: Context, private val scope: CoroutineScope) {

    class Art(val bitmap: Bitmap, val jpeg: ByteArray)

    private val cache = LruCache<String, Art>(CACHE_SIZE)
    private val loading = mutableSetOf<String>()

    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates.asStateFlow()

    /** The artwork for [scene] if it's loaded; otherwise starts loading it and returns null. Main thread only. */
    fun get(scene: Scene): Art? {
        val key = scene.background ?: return null
        cache.get(key)?.let { return it }
        if (loading.add(key)) {
            scope.launch {
                val art = withContext(Dispatchers.IO) { runCatching { load(key) }.getOrNull() }
                loading.remove(key)
                if (art != null) {
                    cache.put(key, art)
                    _updates.value++
                }
            }
        }
        return null
    }

    private fun load(background: String): Art? {
        val bitmap = decodeSampled(background) ?: return null
        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        return Art(bitmap, jpeg)
    }

    /** Decodes the picture at a fraction of its size, so the longest side is about [MAX_SIDE]. */
    private fun decodeSampled(background: String): Bitmap? {
        val drawable = BuiltinBackgrounds.drawableFor(background)
        val decode = { options: BitmapFactory.Options ->
            if (drawable != null) {
                BitmapFactory.decodeResource(context.resources, drawable, options)
            } else {
                context.contentResolver.openInputStream(background.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val MAX_SIDE = 512
        const val CACHE_SIZE = 8
        const val JPEG_QUALITY = 85
    }
}
