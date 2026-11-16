package com.kARThik.vote.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies picked images into app-managed storage so records never depend on the gallery. */
object ImageStore {
    private const val MAX_DIM = 768

    fun dir(ctx: Context): File = File(ctx.filesDir, "images").apply { mkdirs() }

    fun file(ctx: Context, name: String?): File? =
        if (name.isNullOrBlank()) null else File(dir(ctx), name).takeIf { it.exists() }

    suspend fun import(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_DIM * 2 || bounds.outHeight / sample > MAX_DIM * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            val rotation = try {
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) { 0f }
            val scale = MAX_DIM.toFloat() / maxOf(bmp.width, bmp.height)
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rotation != 0f) m.postRotate(rotation)
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val name = "img_${UUID.randomUUID()}.jpg"
            File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun toBase64(ctx: Context, name: String?): String? {
        val f = file(ctx, name) ?: return null
        return Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }

    /** Small JPEG (max 256 px) for Bluetooth sync so the transfer takes seconds, not minutes. */
    fun toCompactBase64(ctx: Context, name: String?): String? {
        val f = file(ctx, name) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return toBase64(ctx, name)
            val scale = 256f / maxOf(bmp.width, bmp.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            val bos = java.io.ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 78, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { toBase64(ctx, name) }
    }

    fun saveBase64(ctx: Context, name: String, b64: String) {
        val safe = File(name).name // prevent path traversal
        val f = File(dir(ctx), safe)
        if (!f.exists()) f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
    }

    fun clearAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
