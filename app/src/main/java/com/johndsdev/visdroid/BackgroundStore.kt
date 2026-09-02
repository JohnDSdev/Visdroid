package com.johndsdev.visdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object BackgroundStore {
    private const val FILE_NAME = "wallpaper_background.jpg"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    fun clear(context: Context) {
        file(context).delete()
    }

    fun load(context: Context): Bitmap? = runCatching {
        if (!file(context).exists()) null else BitmapFactory.decodeFile(file(context).absolutePath)
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun saveFromUri(context: Context, uri: Uri) {
        val source = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val maxDimension = 3000
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height).toFloat())
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else source

        FileOutputStream(file(context)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }

        if (bitmap !== source) bitmap.recycle()
        source.recycle()
    }
}
