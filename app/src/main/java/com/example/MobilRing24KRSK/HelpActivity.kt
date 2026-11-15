package com.example.MobilRing24KRSK

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted

/**
 * Экран «Справка» — статический текст: как работает приложение и правила безопасности.
 * Данные сюда не подгружаются, поэтому экран открывается даже без сети.
 */
class HelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        setContent {
            LostAndFindTheme {
                HelpScreen()
            }
        }
    }
}

@Composable
fun HelpScreen() {
    SimpleScreen(title = "Справка") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "MobilRing — помощь в поиске потерянных вещей в Красноярске",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Spacer(Modifier.height(12.dp))

            // Каждый блок — заголовок и абзац текста.
            HelpParagraph(
                "Как это работает",
                "Вы заполняете профиль, публикуете объявление о пропаже или находке. " +
                        "Каждому запросу присваивается ID, локальная копия которого хранится на устройстве."
            )
            HelpParagraph(
                "Уведомления",
                "Приложение сверяет локальные копии ID ваших запросов с общей базой находок. " +
                        "Если ID совпал — в разделе «Уведомления» появится запись с описанием находки."
            )
            HelpParagraph(
                "Профиль",
                "ФИО, адрес и телефон обязательны: по ним с вами свяжется человек, " +
                        "нашедший вещь. Поле «О себе» — до 100 символов, там можно указать ссылку на соц сети или мессенджеры."
            )
            HelpParagraph(
                "Все объявления",
                "Лента доступна без профиля. Фильтр «Моё» показывает только ваши записи, " +
                        "второй фильтр переключает между потерями и находками."
            )
            HelpParagraph(
                "Безопасность",
                "Не передавайте вещь без проверки примет. Встречайтесь в людных местах, " +
                        "не сообщайте коды из СМС."
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Версия 1.0 · данные хранятся локально",
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Заголовок блока и текст под ним. */
@Composable
private fun HelpParagraph(title: String, body: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
        modifier = Modifier.height(24.dp)
    )
    Text(text = body, fontSize = 14.sp, color = Ink.copy(alpha = 0.85f))
    Spacer(Modifier.height(14.dp))
}
