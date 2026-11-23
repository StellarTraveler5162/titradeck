package com.titradeck.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import java.io.File
import java.io.IOException

internal fun saveCompoundImageToGallery(context: Context, cid: Int, png: ByteArray) {
    val filename = "PubChem-CID-$cid.png"
    val album = "${Environment.DIRECTORY_PICTURES}/TitraDeck"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(Media.DISPLAY_NAME, filename)
            put(Media.MIME_TYPE, "image/png")
            put(Media.RELATIVE_PATH, album)
            put(Media.IS_PENDING, 1)
        }
        val collection = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create gallery image")
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open gallery image")
            output.use { it.write(png) }
            values.clear()
            values.put(Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) {
                throw IOException("Could not publish gallery image")
            }
        } catch (failure: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    } else {
        saveLegacyGalleryImage(context, filename, png)
    }
}

@Suppress("DEPRECATION")
private fun saveLegacyGalleryImage(context: Context, filename: String, png: ByteArray) {
    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TitraDeck")
    if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Could not create TitraDeck album")
    val destination = File(directory, filename)
    val image = if (destination.createNewFile()) destination
        else File.createTempFile(filename.removeSuffix(".png") + "-", ".png", directory)
    try {
        image.writeBytes(png)
        MediaScannerConnection.scanFile(context, arrayOf(image.absolutePath), arrayOf("image/png"), null)
    } catch (failure: Exception) {
        image.delete()
        throw failure
    }
}
