package com.example.MobilRing24KRSK

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.NotificationItem
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme

class NotificationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val repo = FirebaseRepo(this)
        setContent {
            LostAndFindTheme {
                NotificationsScreen(repo)
            }
        }
    }
}

/**
 * «Уведомления»: карточки совпадений, которые нашёл серверный матчер.
 *
 * Приложение само ничего не сравнивает — оно читает узел matches и показывает
 * только те пары, где участвует заявка текущего пользователя.
 * Нажатие по карточке открывает объявление второй стороны (AdDetailActivity),
 * откуда можно сразу позвонить.
 */
@Composable
fun NotificationsScreen(repo: FirebaseRepo) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(repo) {
        val feed = repo.feed()
        feed.start(
            onAds = {},
            onNotifications = {
                items = it
                loading = false
            },
            onError = {
                errorMessage = it
                loading = false
            }
        )
        onDispose { feed.stop() }
    }

    SimpleScreen(title = "Уведомления · ${items.size}") {
        if (loading) {
            Text("Сверяем ваши заявки с базой совпадений…", color = Muted)
            return@SimpleScreen
        }

        if (items.isEmpty()) {
            Text(
                text = errorMessage ?: "Пока нет уведомлений о совпадениях.",
                color = Muted
            )
            return@SimpleScreen
        }

        Text(
            text = "Мы сверяем локальные копии ID ваших запросов с общей базой и показываем совпадения.",
            fontSize = 13.sp,
            color = Muted
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(items, key = { it.id }) { item ->
                NotificationCard(
                    item = item,
                    onClick = {
                        item.otherAd?.let { ad ->
                            context.startActivity(AdDetailActivity.intentFor(context, ad))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandBlue),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(text = item.date, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            Text(text = item.message, fontSize = 14.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ID заявки: ${item.adId}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.copy(alpha = 0.8f),
                modifier = Modifier
                    .then(Modifier)
            )
        }
    }
}
