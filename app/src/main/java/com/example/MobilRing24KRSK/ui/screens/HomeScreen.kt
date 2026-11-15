package com.example.MobilRing24KRSK.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.example.MobilRing24KRSK.R
import com.example.MobilRing24KRSK.ui.components.AvatarImage
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper

private val Alert = Color(0xFFE2574C)

/**
 * Главный экран: фон-рисунок, кнопки действий, бейдж непрочитанных уведомлений
 * и точка-«восклицание» на аватаре, пока профиль не заполнен.
 *
 * Экран ничего не знает про Firebase и SharedPreferences: все данные приходят
 * параметрами, а нажатия уходят наружу колбэками — поэтому его можно собрать
 * в превью без базы (см. HomeScreenPreview в MainActivity).
 *
 * Если профиль не заполнен, кнопки публикации остаются, но «выключены»:
 * нажатие по ним открывает профиль ([onDisabledClick] в DoodleButton).
 */
@Composable
fun HomeScreen(
    notificationsCount: Int,
    avatarUri: Uri?,
    profileComplete: Boolean,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onReportLostClick: () -> Unit,
    onReportFoundClick: () -> Unit,
    onAllAdsClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper.copy(alpha = 0.55f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
        ) {

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        modifier = Modifier.clickable(onClick = onNotificationsClick),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(BrandBlue)
                                .border(2.dp, Ink, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BellIcon(Modifier.size(30.dp))
                        }
                        CountBadge(
                            count = notificationsCount,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "уведомления",
                        fontFamily = FontFamily.Cursive,
                        fontSize = 24.sp,
                        color = Ink,
                        modifier = Modifier.rotate(-5f)
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "профиль",
                        fontFamily = FontFamily.Cursive,
                        fontSize = 26.sp,
                        color = Ink,
                        modifier = Modifier
                            .padding(end = 8.dp, top = 8.dp)
                            .rotate(-7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clickable(onClick = onProfileClick)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Paper)
                                .border(2.dp, Ink, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUri != null) {
                                AvatarImage(
                                    uri = avatarUri,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                PersonIcon(Modifier.size(46.dp))
                            }
                        }
                        if (!profileComplete) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopStart)
                                    .clip(CircleShape)
                                    .background(Alert)
                                    .border(2.dp, Paper, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            DoodleButton(
                text = "СООБЩИТЬ О ПРОПАЖЕ",
                onClick = onReportLostClick,
                enabled = profileComplete,
                onDisabledClick = onProfileClick
            )
            Spacer(Modifier.height(18.dp))
            DoodleButton(
                text = "СООБЩИТЬ О НАХОДКЕ",
                onClick = onReportFoundClick,
                enabled = profileComplete,
                onDisabledClick = onProfileClick
            )
            Spacer(Modifier.height(18.dp))
            DoodleButton(
                text = "ВСЕ ОБЪЯВЛЕНИЯ",
                onClick = onAllAdsClick
            )

            if (!profileComplete) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Публикация недоступна: заполните ФИО, адрес и телефон в профиле.",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Справка",
                    fontFamily = FontFamily.Cursive,
                    fontSize = 28.sp,
                    color = Ink,
                    modifier = Modifier
                        .padding(end = 54.dp)
                        .rotate(-6f)
                )
                Spacer(Modifier.height(2.dp))
                SmallDoodleButton(
                    text = "СПРАВКА",
                    onClick = onHelpClick,
                    modifier = Modifier.fillMaxWidth(0.62f)
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Alert)
            .border(2.dp, Paper, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = if (count > 9) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BellIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = h * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val body = Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            cubicTo(w * 0.20f, h * 0.14f, w * 0.26f, h * 0.52f, w * 0.14f, h * 0.70f)
            lineTo(w * 0.86f, h * 0.70f)
            cubicTo(w * 0.74f, h * 0.52f, w * 0.80f, h * 0.14f, w * 0.5f, h * 0.10f)
        }
        drawPath(body, color = Ink, style = stroke)
        drawCircle(
            color = Ink,
            radius = w * 0.075f,
            center = Offset(w * 0.5f, h * 0.86f)
        )
    }
}

@Composable
private fun PersonIcon(modifier: Modifier = Modifier) {
    val ink = Ink.copy(alpha = 0.55f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.07f, cap = StrokeCap.Round)
        drawCircle(
            color = ink,
            radius = w * 0.17f,
            center = Offset(w * 0.5f, h * 0.33f),
            style = stroke
        )
        drawArc(
            color = ink,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.50f),
            size = Size(w * 0.64f, h * 0.62f),
            style = stroke
        )
    }
}
