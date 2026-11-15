package com.example.MobilRing24KRSK

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AdCategories
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper

class AllAdsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val repo = FirebaseRepo(this)
        setContent {
            LostAndFindTheme {
                AllAdsScreen(repo)
            }
        }
    }
}

private enum class TypeFilter(val label: String) {
    ALL("ВСЕ"),
    LOST("ПОТЕРИ"),
    FOUND("НАХОДКИ")
}

/**
 * «Все объявления»: лента из узлов lost и found.
 *
 * Фильтры локальные — данные уже загружены в [ads], поэтому переключение
 * «Моё / ВСЕ / ПОТЕРИ / НАХОДКИ» не ходит в сеть повторно.
 * Нажатие по карточке открывает экран просмотра (AdDetailActivity).
 */
@Composable
fun AllAdsScreen(repo: FirebaseRepo) {
    val context = LocalContext.current
    var onlyMine by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    // null — показываем все категории.
    var categoryFilter by remember { mutableStateOf<String?>(null) }
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

    SimpleScreen(title = "Все объявления · ${visible.size}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = "Моё",
                selected = onlyMine,
                onClick = { onlyMine = !onlyMine }
            )
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
                Spacer(Modifier.weight(1f))
                Text(text = ad.date, fontSize = 12.sp, color = Muted)
            }
            if (ad.closed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (ad.type == AdType.LOST) "ВЕЩЬ НАЙДЕНА" else "ВЕЩЬ ВОЗВРАЩЕНА",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Paper)
                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
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
