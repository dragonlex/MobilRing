package com.example.MobilRing24KRSK.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.BrandBluePressed
import com.example.MobilRing24KRSK.ui.theme.DisabledFill
import com.example.MobilRing24KRSK.ui.theme.DisabledInk
import com.example.MobilRing24KRSK.ui.theme.Ink

/**
 * Главная кнопка приложения — «рисованная» плашка с толстой обводкой.
 *
 * Особенность: у выключенной кнопки можно задать [onDisabledClick].
 * Так «Сообщить о пропаже» при незаполненном профиле не молчит, а открывает профиль.
 */
@Composable
fun DoodleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    height: Int = 64
) {
    val fill = if (enabled) BrandBlue else DisabledFill
    val ink = if (enabled) Ink else DisabledInk

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clickable {
                if (enabled) onClick() else onDisabledClick?.invoke()
            },
        shape = RoundedCornerShape(6.dp),
        color = fill,
        contentColor = ink,
        border = BorderStroke(2.dp, ink),
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Маленькая кнопка (например, «СПРАВКА» на главном экране).
 * Отличается от [DoodleButton] только высотой и цветом заливки.
 */
@Composable
fun SmallDoodleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BrandBluePressed
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = color,
        contentColor = Ink,
        border = BorderStroke(2.dp, Ink),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
        }
    }
}
