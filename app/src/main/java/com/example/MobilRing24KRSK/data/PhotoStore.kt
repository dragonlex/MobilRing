package com.example.MobilRing24KRSK.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * Снимки вещей: сжатие при отправке и расшифровка при показе.
 *
 * Фотографии хранятся прямо в объявлении строкой base64 (Firebase Storage в проекте
 * не включён). Чтобы запись не раздувалась, снимок уменьшается до [MAX_SIDE] пикселей
 * по длинной стороне и сжимается в JPEG — обычно это 60–120 КБ, то есть 80–160 КБ текста.
 *
 * Расшифровка кешируется в [LruCache]: лента перерисовывается часто, и декодировать
 * один и тот же снимок заново на каждый кадр нельзя.
 */
object PhotoStore {

    /** Длинная сторона снимка после сжатия, пикселей. */
    private const val MAX_SIDE = 720

    /** Качество JPEG при сжатии: ниже — мельче файл, но заметны артефакты. */
    private const val QUALITY = 70

    private const val CACHE_KB = 8 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Временный кеш base64-фото по ID объявления, чтобы не передавать большие строки через Intent. */
    private val adPhotos = object : LruCache<String, String>(64) {}

    fun putByAdId(adId: String, base64: String) {
        if (adId.isNotBlank() && base64.isNotBlank()) {
            adPhotos.put(adId, base64)
        }
    }

    fun getByAdId(adId: String): String {
        if (adId.isBlank()) return ""
        return adPhotos.get(adId).orEmpty()
    }

    /**
     * Читает картинку по [uri], уменьшает её и возвращает base64 (JPEG).
     * Пустая строка — прочитать не удалось.
     */
    fun encode(context: Context, uri: Uri): String {
        val original = runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return ""

        val scaled = scaleDown(original)
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes)
        if (scaled !== original) scaled.recycle()
        original.recycle()

        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    /** Расшифровывает base64 в картинку; повторные вызовы берут готовый результат из кеша. */
    fun decode(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        cache.get(base64)?.let { return it }

        val bitmap = runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null

        cache.put(base64, bitmap)
        return bitmap
    }

    /** Уменьшает картинку так, чтобы длинная сторона стала не больше [MAX_SIDE]. */
    private fun scaleDown(source: Bitmap): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= MAX_SIDE) return source

        val ratio = MAX_SIDE.toFloat() / longSide
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
