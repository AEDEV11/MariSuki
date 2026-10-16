package com.aesprt.marisuki.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.aesprt.marisuki.data.local.PosStoragePaths
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-private photo storage shared by product forms and encrypted backups. */
class ProductImageStore(context: Context) {
    private val appContext = context.applicationContext
    val imageDirectory: File
        get() = File(PosStoragePaths.storeDirectory(appContext), "item_images")

    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val mimeType = appContext.contentResolver.getType(uri)?.lowercase()
            ?: throw IOException("The selected file is not a supported image.")
        val extension = SUPPORTED_MIME_TYPES[mimeType]
            ?: throw IOException("Choose a JPEG, PNG, or WebP image.")
        val directory = imageDirectory
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("MariSuki could not create the item photo folder.")
        }
        val filename = newFilename(extension)
        val target = File(directory, filename)
        val temporary = File(directory, ".$filename.part")
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("MariSuki could not read the selected image.")
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) throw IOException("Choose an image smaller than 10 MB.")
                        output.write(buffer, 0, count)
                    }
                    if (total == 0L) throw IOException("The selected image is empty.")
                    output.fd.sync()
                }
            }
            validateImage(temporary)
            if (!temporary.renameTo(target)) throw IOException("MariSuki could not save the item photo.")
            filename
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    fun imageFile(filename: String?): File? {
        if (filename == null || !SAFE_FILENAME.matches(filename)) return null
        return File(imageDirectory, filename)
    }

    fun decodePreview(filename: String?, maxDimension: Int = 360): Bitmap? {
        val file = imageFile(filename)?.takeIf(File::isFile) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun readBackupImage(filename: String): ByteArray {
        val file = imageFile(filename)?.takeIf(File::isFile)
            ?: throw IOException("A product photo referenced by this store could not be found.")
        if (file.length() !in 1..MAX_IMAGE_BYTES) throw IOException("A product photo is too large for a backup.")
        return FileInputStream(file).use { input ->
            val bytes = input.readBytes()
            if (bytes.size.toLong() > MAX_IMAGE_BYTES) throw IOException("A product photo is too large for a backup.")
            validateImage(file)
            bytes
        }
    }

    fun backupExtension(filename: String): String =
        filename.takeIf(SAFE_FILENAME::matches)?.substringAfterLast('.')
            ?: throw IOException("A product photo filename in this backup is invalid.")

    fun saveRestoredImage(extension: String, bytes: ByteArray): String {
        require(extension in SUPPORTED_EXTENSIONS)
        require(bytes.size in 1..MAX_IMAGE_BYTES.toInt())
        val directory = imageDirectory
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("MariSuki could not create the item photo folder.")
        }
        val filename = newFilename(extension)
        val target = File(directory, filename)
        val temporary = File(directory, ".$filename.part")
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            validateImage(temporary)
            if (!temporary.renameTo(target)) throw IOException("MariSuki could not restore a product photo.")
            filename
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    fun delete(filename: String?) {
        imageFile(filename)?.delete()
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val pixelCount = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixelCount > MAX_IMAGE_PIXELS) {
            throw IOException("The selected file is not a supported product image.")
        }
    }

    private fun newFilename(extension: String) = "photo-${UUID.randomUUID()}.$extension"

    companion object {
        const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L
        private const val MAX_IMAGE_PIXELS = 100_000_000L
        private val SUPPORTED_MIME_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "png", "webp")
        private val SAFE_FILENAME = Regex("^photo-[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
    }
}
