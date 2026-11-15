package com.example.MobilRing24KRSK.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * Показывает картинку по ссылке (content:// от выбора фото).
 *
 * Декодирование — операция небыстрая, поэтому она идёт в фоне через [produceState]:
 * пока картинка грузится, на экране просто пустой круг, интерфейс не подвисает.
 */
@Composable
fun AvatarImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Аватар"
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = loadBitmap(context, uri)
    }

    Box(modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Читает файл по ссылке и превращает его в картинку Compose.
 * На Android 9+ используется ImageDecoder, на более старых — BitmapFactory.
 * Если файл не открылся (удалён, нет доступа), возвращается null — экран покажет заглушку.
 */
fun loadBitmap(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = false
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
    bitmap?.asImageBitmap()
}.getOrNull()
