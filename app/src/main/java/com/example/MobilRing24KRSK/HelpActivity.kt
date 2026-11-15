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
 * Экран «Справка» — краткое руководство по цифровому бюро находок для школы и колледжа.
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
                text = "MobilRing — цифровое бюро находок для школы и колледжа",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Spacer(Modifier.height(12.dp))

            HelpParagraph(
                "1. Публикация пропажи и находки",
                "Заполните анкету в разделе «Профиль», затем нажмите «Сообщить о пропаже» или " +
                    "«Сообщить о находке». Выберите категорию, школьную зону (гардероб, спортзал, " +
                    "столовая, кабинеты и др.), дату события и приложите фотографию вещи."
            )
            HelpParagraph(
                "2. ИИ-помощник описания",
                "В форме новой заявки кнопка «ПОМОЧЬ С ОПИСАНИЕМ (ИИ)» помогает составить подробное " +
                    "и точное описание по названию и категории вещи (через локальную языковую модель " +
                    "или встроенный шаблон). При необходимости адрес ИИ-сервера можно указать по кнопке «ИИ IP»."
            )
            HelpParagraph(
                "3. Умное сопоставление на базе ИИ",
                "Сервер автоматически сравнивает объявления о пропажах и находках с учётом синонимов, " +
                    "описания, места и дат. Когда находится подходящая пара, карточка сразу появляется " +
                    "в разделе «Уведомления» с подсветкой на колокольчике."
            )
            HelpParagraph(
                "4. Лента, поиск и фильтры",
                "Раздел «Все объявления» поддерживает текстовый поиск, фильтры по типу (потери / находки), " +
                    "категориям вещей, школьным зонам, месту и датам (сегодня, 7 дней, 30 дней или свой период)."
            )
            HelpParagraph(
                "5. Встроенный чат и приватность",
                "Связаться с автором объявления можно прямо во встроенном диалоге («Написать в чат») " +
                    "или по телефону. В «Профиле» вы сами решаете, показывать ли номер телефона и адрес другим участникам."
            )
            HelpParagraph(
                "6. Возврат по проверочному 6-значному коду",
                "Чтобы убедиться, что вещь передана настоящему владельцу: при личной встрече владелец " +
                    "открывает карточку объявления, формирует 6-значный код и называет его нашедшему. " +
                    "Нашедший вводит код у себя — заявка получает статус «ВОЗВРАЩЕНО · ПО КОДУ»."
            )
            HelpParagraph(
                "7. Тепловая карта зон и Доска почёта",
                "На экране «Карта зон и рейтинг» показана интерактивная схема помещений школы с подсветкой " +
                    "зон, где чаще всего забывают вещи, а во вкладке «Доска почёта» — рейтинг надёжности " +
                    "участников и благодарности от владельцев."
            )
            HelpParagraph(
                "8. Перенос аккаунта по ключу",
                "В разделе «Профиль → Анкета и ключ» можно создать 16-значный ключ восстановления, " +
                    "чтобы перенести свои заявки, переписку и рейтинг надёжности на другое устройство."
            )
            HelpParagraph(
                "Правила безопасности",
                "Передавайте найденные вещи в присутствии дежурного администратора, классного руководителя " +
                    "или на посту охраны учебного заведения. Перед передачей уточните индивидуальные приметы вещи."
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Версия 2.0 · MobilRing Кейс №4 «Бюро находок»",
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
        color = Ink
    )
    Spacer(Modifier.height(4.dp))
    Text(text = body, fontSize = 14.sp, color = Ink.copy(alpha = 0.85f))
    Spacer(Modifier.height(14.dp))
}
