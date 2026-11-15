package com.example.MobilRing24KRSK

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AdCategories
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.CampusZones
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.PhotoStore
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AllAdsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val repo = FirebaseRepo(this)
        val initialZoneId = intent.getStringExtra(EXTRA_ZONE_ID)
        setContent {
            LostAndFindTheme {
                AllAdsScreen(repo = repo, initialZoneId = initialZoneId)
            }
        }
    }

    companion object {
        const val EXTRA_ZONE_ID = "filter_zone_id"

        fun intentForZone(context: Context, zoneId: String): Intent =
            Intent(context, AllAdsActivity::class.java).putExtra(EXTRA_ZONE_ID, zoneId)
    }
}

private enum class TypeFilter(val label: String) {
    ALL("ВСЕ"),
    LOST("ПОТЕРИ"),
    FOUND("НАХОДКИ")
}

/**
 * Быстрые варианты периода. Возвращают границы диапазона в миллисекундах:
 * от начала первого дня до конца последнего, 0 — граница не ограничена.
 */
private enum class DatePreset(val label: String) {
    ALL("Все даты"),
    TODAY("Сегодня"),
    WEEK("7 дней"),
    MONTH("30 дней");

    fun range(): Pair<Long, Long> = when (this) {
        ALL -> 0L to 0L
        TODAY -> {
            val start = startOfToday()
            start to endOfDay(start)
        }
        WEEK -> {
            val start = startOfToday() - 6 * DAY_MS
            start to endOfDay(startOfToday())
        }
        MONTH -> {
            val start = startOfToday() - 29 * DAY_MS
            start to endOfDay(startOfToday())
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Полночь текущих суток по местному времени. */
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Последняя миллисекунда суток, начавшихся с [dayStart]. */
private fun endOfDay(dayStart: Long): Long = dayStart + DAY_MS - 1

/**
 * Календарь Material3 отдаёт полночь выбранного дня по UTC, а в Красноярске (UTC+7)
 * это уже другой день. Переносим выбранную дату в местную полночь.
 */
private fun pickerDayToLocal(utcDay: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
    }.timeInMillis
}

/** Подпись выбранного периода: «15.11.2026 — 20.11.2026» или одна дата. */
private fun rangeLabel(from: Long, to: Long): String {
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return when {
        from > 0L && to > 0L -> "${format.format(Date(from))} — ${format.format(Date(to))}"
        from > 0L -> "с ${format.format(Date(from))}"
        to > 0L -> "по ${format.format(Date(to))}"
        else -> ""
    }
}

/**
 * «Все объявления»: лента из узлов lost и found.
 *
 * Фильтры локальные — данные уже загружены в [ads], поэтому переключение
 * «Моё / ВСЕ / ПОТЕРИ / НАХОДКИ» не ходит в сеть повторно.
 * Нажатие по карточке открывает экран просмотра (AdDetailActivity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAdsScreen(repo: FirebaseRepo, initialZoneId: String? = null) {
    val context = LocalContext.current
    var onlyMine by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    // null — показываем все категории.
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    // null — любое место; иначе точное совпадение с текстом места.
    var placeFilter by remember { mutableStateOf<String?>(null) }
    var zoneFilterId by remember { mutableStateOf(initialZoneId?.takeIf { it.isNotBlank() }) }
    // Поиск по названию, описанию и месту — без учёта регистра.
    var query by remember { mutableStateOf("") }
    // Диапазон дат потери/находки в миллисекундах; 0 — граница не задана.
    var dateFrom by remember { mutableStateOf(0L) }
    var dateTo by remember { mutableStateOf(0L) }
    // Какую границу периода сейчас выбираем: 1 — начало, 2 — конец, 0 — диалог закрыт.
    var pickerFor by remember { mutableStateOf(0) }
    var ads by remember { mutableStateOf<List<AdItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(repo) {
        val feed = repo.feed()
        feed.start(
            onAds = {
                ads = it
                loading = false
            },
            onNotifications = {},
            onError = {
                errorMessage = it
                loading = false
            }
        )
        onDispose { feed.stop() }
    }

    // Места берём из самих объявлений: так список всегда актуален и не требует настроек.
    val places = remember(ads) {
        ads.map { it.place.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val trimmedQuery = query.trim()
    val visible = ads
        .filter { ad ->
            when (typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.LOST -> ad.type == AdType.LOST
                TypeFilter.FOUND -> ad.type == AdType.FOUND
            }
        }
        .filter { ad -> !onlyMine || ad.isMine }
        .filter { ad -> categoryFilter == null || ad.category == categoryFilter }
        .filter { ad -> placeFilter == null || ad.place.trim() == placeFilter }
        .filter { ad ->
            zoneFilterId == null || CampusZones.matchZone(ad.place)?.id == zoneFilterId
        }
        .filter { ad ->
            if (trimmedQuery.isBlank()) {
                true
            } else {
                val haystack = listOf(ad.title, ad.description, ad.place, ad.category)
                    .joinToString(" ")
                    .lowercase()
                haystack.contains(trimmedQuery.lowercase())
            }
        }
        .filter { ad ->
            val eventDate = if (ad.eventDate > 0L) ad.eventDate else ad.createdAt
            (dateFrom == 0L || eventDate >= dateFrom) && (dateTo == 0L || eventDate <= dateTo)
        }

    SimpleScreen(title = "Все объявления · ${visible.size}") {
        // Свой период: сначала выбираем начало, затем конец.
        if (pickerFor != 0) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { pickerFor = 0 },
                confirmButton = {
                    TextButton(onClick = {
                        val picked = pickerState.selectedDateMillis
                        if (picked != null) {
                            val localDay = pickerDayToLocal(picked)
                            if (pickerFor == 1) {
                                dateFrom = localDay
                                if (dateTo in 1 until localDay) dateTo = 0L
                                pickerFor = 2
                            } else {
                                dateTo = endOfDay(localDay)
                                pickerFor = 0
                            }
                        } else {
                            pickerFor = 0
                        }
                    }) {
                        Text(if (pickerFor == 1) "ДАЛЕЕ" else "ГОТОВО")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pickerFor = 0 }) { Text("ОТМЕНА") }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }

        // Поиск: одно поле на название, описание, место и категорию.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск по названию, описанию, месту") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = "Моё",
                selected = onlyMine,
                onClick = { onlyMine = !onlyMine }
            )
            val activeZone = CampusZones.ALL.firstOrNull { it.id == zoneFilterId }
            if (activeZone != null) {
                FilterPill(
                    label = "Зона: ${activeZone.shortTitle} ×",
                    selected = true,
                    onClick = { zoneFilterId = null }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypeFilter.entries.forEach { filter ->
                FilterPill(
                    label = filter.label,
                    selected = typeFilter == filter,
                    onClick = { typeFilter = filter }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Фильтр по категориям: «Все» плюс список категорий из AdCategories.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = "Все категории",
                selected = categoryFilter == null,
                onClick = { categoryFilter = null }
            )
            AdCategories.ALL.forEach { name ->
                FilterPill(
                    label = name,
                    selected = categoryFilter == name,
                    onClick = {
                        categoryFilter = if (categoryFilter == name) null else name
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Фильтр по месту: значения берутся из самих объявлений.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = "Все места",
                selected = placeFilter == null,
                onClick = { placeFilter = null }
            )
            places.forEach { place ->
                FilterPill(
                    label = place,
                    selected = placeFilter == place,
                    onClick = { placeFilter = if (placeFilter == place) null else place }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Фильтр по дате потери/находки: быстрые варианты и свой период.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DatePreset.entries.forEach { preset ->
                val (from, to) = preset.range()
                FilterPill(
                    label = preset.label,
                    selected = dateFrom == from && dateTo == to,
                    onClick = {
                        dateFrom = from
                        dateTo = to
                    }
                )
            }
            FilterPill(
                label = "СВОЙ ПЕРИОД",
                selected = dateFrom != 0L && dateTo != 0L &&
                    DatePreset.entries.none { it.range() == dateFrom to dateTo },
                onClick = { pickerFor = 1 }
            )
        }
        if (dateFrom != 0L || dateTo != 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = rangeLabel(dateFrom, dateTo),
                fontSize = 12.sp,
                color = Muted
            )
        }
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Text("Загружаем объявления из базы…", color = Muted, fontSize = 14.sp)
            return@SimpleScreen
        }

        if (visible.isEmpty()) {
            Text(
                text = errorMessage ?: "По этим фильтрам объявлений нет.",
                color = Muted,
                fontSize = 14.sp
            )
            return@SimpleScreen
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(visible, key = { it.id }) { ad ->
                AdCard(ad = ad, onClick = { openAdDetail(context, ad) })
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Paper else Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink else Paper)
            .border(2.dp, Ink, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun openAdDetail(context: Context, ad: AdItem) {
    context.startActivity(AdDetailActivity.intentFor(context, ad))
}

@Composable
private fun AdCard(ad: AdItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ad.type.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (ad.type == AdType.FOUND) BrandBlue else Paper)
                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.padding(start = 6.dp))
                Text(
                    text = ad.statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (ad.closed) Paper else BrandBlue.copy(alpha = 0.45f))
                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(text = ad.date, fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))

            // Снимок вещи: если он есть, показываем его до названия — по фото узнают быстрее.
            val photo = remember(ad.photo) { PhotoStore.decode(ad.photo) }
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Фото вещи",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Ink, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.height(8.dp))
            }

                Text(text = ad.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(text = ad.place, fontSize = 13.sp, color = Muted)
            if (ad.category.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ad.category,
                    fontSize = 12.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandBlue.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (ad.eventDateText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (ad.type == AdType.LOST) {
                        "потеряно: ${ad.eventDateText}"
                    } else {
                        "найдено: ${ad.eventDateText}"
                    },
                    fontSize = 13.sp,
                    color = Ink.copy(alpha = 0.75f)
                )
            }
            if (ad.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = ad.description, fontSize = 13.sp, color = Ink.copy(alpha = 0.85f))
            }
            if (ad.ownerName.isNotBlank() || ad.ownerPhone.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOf(ad.ownerName, ad.ownerPhone)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            if (ad.isMine) {
                Spacer(Modifier.height(6.dp))
                Text(text = "моё объявление", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
            }
        }
    }
}
