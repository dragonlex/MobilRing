package com.example.MobilRing24KRSK

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AuthGate
import com.example.MobilRing24KRSK.data.ChatInfo
import com.example.MobilRing24KRSK.data.ChatsFeed
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Список моих переписок.
 *
 * Чаты создаются из объявления кнопкой «НАПИСАТЬ В ЧАТ», поэтому здесь
 * только список того, что уже начато, — с последним сообщением и датой.
 */
class ChatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        setContent {
            LostAndFindTheme {
                ChatsScreen()
            }
        }
    }
}

@Composable
fun ChatsScreen() {
    val context = LocalContext.current
    val feed = remember { ChatsFeed() }
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    var chats by remember { mutableStateOf<List<ChatInfo>>(emptyList()) }
    var myUid by remember { mutableStateOf(AuthGate.userId()) }

    DisposableEffect(Unit) {
        AuthGate.ensure { authorized ->
            if (authorized) {
                myUid = AuthGate.userId()
                feed.start(myUid) { chats = it }
            }
        }
        onDispose { feed.stop() }
    }

    SimpleScreen(title = "Сообщения") {
        if (chats.isEmpty()) {
            Text(
                text = "Переписок пока нет. Откройте объявление из ленты и нажмите «НАПИСАТЬ В ЧАТ».",
                fontSize = 14.sp,
                color = Muted
            )
            return@SimpleScreen
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(chats, key = { it.id }) { chat ->
                ChatRow(
                    chat = chat,
                    date = if (chat.lastAt > 0L) dateFormat.format(Date(chat.lastAt)) else "",
                    onClick = {
                        context.startActivity(
                            ChatActivity.intentFor(context, chat.id, chat.adTitle)
                        )
                    }
                )
            }
        }
    }
}

/** Строка списка: кто, по какой вещи и последнее сообщение. */
@Composable
private fun ChatRow(chat: ChatInfo, date: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = chat.otherName.ifBlank { "Собеседник" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "по объявлению: ${chat.adTitle.ifBlank { "без названия" }}",
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = chat.lastText.ifBlank { "сообщений пока нет" },
                fontSize = 14.sp,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (date.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = date, fontSize = 11.sp, color = Muted)
            }
        }
    }
}
