package com.example.MobilRing24KRSK

import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AdCategories
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.PhotoStore
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Форма «Сообщить о пропаже / о находке».
 *
 * Одна Activity обслуживает оба случая: режим приходит во входном интенте
 * ([EXTRA_TYPE] = [TYPE_LOST] или [TYPE_FOUND]) и передаётся в [ReportScreen].
 * Сама форма ничего не сохраняет — она отдаёт данные в [FirebaseRepo.submit].
 */
class ReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val store = ProfileStore(this)
        val repo = FirebaseRepo(this)
        val isLost = intent.getStringExtra(EXTRA_TYPE) != TYPE_FOUND

        setContent {
            LostAndFindTheme {
                ReportScreen(
                    isLost = isLost,
                    profileComplete = store.profile.isComplete,
                    onSubmit = { title, place, description, category, eventDate, photo, onDone ->
                        repo.submit(
                            type = if (isLost) AdType.LOST else AdType.FOUND,
                            title = title,
                            place = place,
                            description = description,
                            category = category,
                            eventDate = eventDate,
                            profile = store.profile,
                            photo = photo,
                            onResult = { success, message ->
                                if (success) {
                                    Toast.makeText(this, "Объявление отправлено", Toast.LENGTH_SHORT).show()
                                    finish()
                                } else {
                                    Toast.makeText(this, "Ошибка отправки: $message", Toast.LENGTH_LONG).show()
                                }
                                onDone()
                            }
                        )
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "report_type"
        const val TYPE_LOST = "lost"
        const val TYPE_FOUND = "found"
    }
}

/**
 * Дата-пикер Material3 работает в UTC и возвращает полночь выбранного дня
 * по Гринвичу. Если не пересчитывать, в Красноярске (UTC+7) дата уезжает на день назад.
 * Три функции ниже переводят «местную полночь» в UTC для календаря и обратно.
 */

/** Полночь текущих суток в локальном времени — начальное значение поля даты. */
private fun startOfDay(millis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun localDayToPicker(localMillis: Long): Long {
    val local = Calendar.getInstance()
    local.timeInMillis = localMillis
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH)
    )
    return utc.timeInMillis
}

private fun pickerDayToLocal(pickerMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = pickerMillis
    val local = Calendar.getInstance()
    local.clear()
    local.set(
        utc.get(Calendar.YEAR),
        utc.get(Calendar.MONTH),
        utc.get(Calendar.DAY_OF_MONTH)
    )
    return local.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    isLost: Boolean,
    profileComplete: Boolean,
    onSubmit: (
        title: String,
        place: String,
        description: String,
        category: String,
        eventDate: Long,
        photo: String,
        onDone: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AdCategories.OTHER) }
    var eventDate by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var photo by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var helping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Выбор снимка из галереи. Сжатие в base64 идёт в фоне, чтобы не подвешивать форму.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) { PhotoStore.encode(context, uri) }
                if (encoded.isBlank()) {
                    Toast.makeText(context, "Не удалось прочитать фото", Toast.LENGTH_SHORT).show()
                } else {
                    photo = encoded
                }
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    SimpleScreen(title = if (isLost) "Сообщить о пропаже" else "Сообщить о находке") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isLost) {
                    "Опишите вещь и место, где вы её потеряли."
                } else {
                    "Опишите находку и место, где вы её обнаружили."
                },
                fontSize = 14.sp,
                color = Muted
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Что за вещь *") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Категория: горизонтальная лента плашек, выбор одной.
            Text("Категория *", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdCategories.ALL.forEach { name ->
                    CategoryChip(
                        label = name,
                        selected = category == name,
                        onClick = { category = name }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateFormat.format(Date(eventDate)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isLost) "Дата потери *" else "Дата находки *") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { pickerOpen = true }
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text("Место *") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание и приметы") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Фото вещи: необязательно, но с ним находку узнают быстрее.
            Text("Фото вещи (необязательно)", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            val preview = remember(photo) { PhotoStore.decode(photo) }
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Фото вещи",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                SmallDoodleButton(
                    text = "УБРАТЬ ФОТО",
                    color = Paper,
                    onClick = { photo = "" }
                )
            } else {
                SmallDoodleButton(
                    text = "ДОБАВИТЬ ФОТО",
                    color = Paper,
                    onClick = { photoPicker.launch("image/*") }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Помощь с описанием: сервис на компьютере пишет текст по вещи и месту.
            SmallDoodleButton(
                text = if (helping) "ПИШУ ОПИСАНИЕ…" else "ПОМОЧЬ С ОПИСАНИЕМ",
                color = Paper,
                onClick = {
                    when {
                        helping -> Unit
                        title.isBlank() ->
                            Toast.makeText(context, "Сначала укажите, что за вещь", Toast.LENGTH_SHORT).show()
                        else -> {
                            helping = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { requestDescription(isLost, title, place, category) }
                                }
                                helping = false
                                result
                                    .onSuccess { text ->
                                        if (text.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                "Сервис вернул пустой ответ",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            description = text
                                        }
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            "Помощь недоступна: ${error.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Контактные данные берутся из профиля: ФИО, адрес, телефон.",
                fontSize = 13.sp,
                color = Muted
            )
            Spacer(Modifier.height(16.dp))

            DoodleButton(
                text = if (sending) "ОТПРАВКА…" else "ОТПРАВИТЬ",
                enabled = profileComplete && !sending,
                onDisabledClick = {
                    Toast.makeText(context, "Сначала заполните профиль", Toast.LENGTH_SHORT).show()
                },
                onClick = {
                    if (title.isBlank() || place.isBlank()) {
                        Toast.makeText(context, "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                    } else {
                        sending = true
                        onSubmit(title, place, description, category, eventDate, photo) { sending = false }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayToPicker(eventDate)
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { eventDate = pickerDayToLocal(it) }
                        pickerOpen = false
                    }
                ) { Text("ГОТОВО") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("ОТМЕНА") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Адрес сервиса помощи с описанием — Server2/assist.py.
 *
 * Хост и порт должны совпадать с config.json на компьютере, где запущен сервис.
 * Телефон и компьютер должны быть в одной сети Wi-Fi.
 */
private const val ASSIST_URL = "http://192.168.0.2:5002/describe"

/**
 * Просит у сервиса готовое описание вещи и возвращает текст.
 *
 * Вызывать только из фонового потока: функция блокирующая.
 * При любой проблеме бросает исключение — вызывающий код показывает его текст.
 */
private fun requestDescription(
    isLost: Boolean,
    title: String,
    place: String,
    category: String
): String {
    val connection = (URL(ASSIST_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5000
        readTimeout = 90000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    try {
        val body = JSONObject()
            .put("type", if (isLost) "lost" else "found")
            .put("title", title)
            .put("place", place)
            .put("category", category)
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("сервис ответил кодом $code")
        return JSONObject(raw).optString("text").trim()
    } finally {
        connection.disconnect()
    }
}

/** Плашка выбора категории: выбранная — тёмная, остальные светлые. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Paper else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink else BrandBlue)
            .border(2.dp, Ink, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
