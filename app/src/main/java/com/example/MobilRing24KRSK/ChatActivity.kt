package com.example.MobilRing24KRSK

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AuthGate
import com.example.MobilRing24KRSK.data.ChatListener
import com.example.MobilRing24KRSK.data.ChatMessage
import com.example.MobilRing24KRSK.data.ChatRepo
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран переписки по объявлению.
 *
 * Ключ чата и заголовок передаются из экрана объявления: там уже известно,
 * чьё это объявление и о какой вещи речь.
 */
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val adTitle = intent.getStringExtra(EXTRA_AD_TITLE).orEmpty()

        setContent {
            LostAndFindTheme {
                ChatScreen(chatId = chatId, adTitle = adTitle)
            }
        }
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_AD_TITLE = "chat_ad_title"

        /** Переход в переписку: [chatId] уже посчитан по двум uid и объявлению. */
        fun intentFor(context: Context, chatId: String, adTitle: String): Intent =
            Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_AD_TITLE, adTitle)
            }
    }
}

@Composable
fun ChatScreen(chatId: String, adTitle: String) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val repo = remember { ChatRepo() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var myUid by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()

    DisposableEffect(chatId) {
        var listener: ChatListener? = null
        AuthGate.ensure { authorized ->
            if (authorized) {
                myUid = AuthGate.userId()
                listener = repo.listenMessages(chatId) { messages = it }
            }
        }
        onDispose { listener?.cancel() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(60)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    SimpleScreen(title = if (adTitle.isBlank()) "Чат" else "Чат · $adTitle") {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = "Переписки пока нет. Напишите первым: расскажите, " +
                                "где видели вещь и как договориться о встрече.",
                        fontSize = 14.sp,
                        color = Muted
                    )
                }
                messages.forEach { message ->
                    MessageBubble(
                        message = message,
                        mine = message.senderId == myUid,
                        time = if (message.at > 0L) timeFormat.format(Date(message.at)) else ""
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Сообщение") },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            SmallDoodleButton(
                text = if (sending) "ОТПРАВЛЯЮ…" else "ОТПРАВИТЬ",
                color = Paper,
                onClick = {
                    if (sending || draft.isBlank()) return@SmallDoodleButton
                    sending = true
                    val text = draft
                    repo.send(chatId, myUid, store.profile.fullName, text) { success, error ->
                        sending = false
                        if (success) {
                            draft = ""
                        } else {
                            android.widget.Toast
                                .makeText(context, error, android.widget.Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            )
        }
    }
}

/** Одно сообщение: своё — справа и голубое, чужое — слева и на «бумаге». */
@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, time: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(if (mine) BrandBlue else Paper, RoundedCornerShape(8.dp))
                .border(2.dp, Ink, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!mine && message.senderName.isNotBlank()) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(text = message.text, fontSize = 15.sp, color = Ink)
            if (time.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = time, fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
            }
        }
    }
}
