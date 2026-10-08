package dev.tevv.taverntales.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dev.tevv.taverntales.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scene background images picked by the user. Images are downscaled and copied into app storage,
 * so they survive the original being moved or deleted and need no long-lived URI permission.
 */
class BackgroundStore(private val context: Context) {
    private val dir = File(context.filesDir, "backgrounds")

    /** Copies the picked image and returns the value to store in [dev.tevv.taverntales.model.Scene.background]. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri)
        dir.mkdirs()
        val file = File(dir, "${newId()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        file.toUri().toString()
    }

    /** Deletes the copied image behind [background], if it is one of ours. Built-in images are left alone. */
    fun delete(background: String?) {
        val path = background?.toUri()?.takeIf { it.scheme == "file" }?.path ?: return
        val file = File(path)
        if (file.parentFile == dir) file.delete()
    }

    private fun decodeScaled(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies EXIF rotation, which matters for camera photos.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not decode image")
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
