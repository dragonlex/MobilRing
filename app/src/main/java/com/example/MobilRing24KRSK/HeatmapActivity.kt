package com.example.MobilRing24KRSK

import android.content.Intent
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.CampusZone
import com.example.MobilRing24KRSK.data.CampusZones
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.ReturnAndTrustRepo
import com.example.MobilRing24KRSK.data.ThanksRecord
import com.example.MobilRing24KRSK.data.UserTrustSummary
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.BrandBluePressed
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HeatZero = Color(0xFFF3F6F8)
private val HeatLow = Color(0xFFDCF2D8)
private val HeatMedium = Color(0xFFFFF0B8)
private val HeatHigh = Color(0xFFFFCA99)
private val HeatCritical = Color(0xFFFF9B87)
private val VerifiedGreen = Color(0xFFC9F2C7)
private val GoldBadge = Color(0xFFFFE599)
private val SilverBadge = Color(0xFFE6ECF0)
private val BronzeBadge = Color(0xFFF4D1B4)

private const val UNMAPPED_ZONE_ID = "other_places"

/**
 * Экран аналитики и доверия школьного бюро находок:
 *  1) «ТЕПЛОВАЯ КАРТА» — интерактивная схема этажей и зон, где чаще забывают и находят вещи;
 *  2) «ДОСКА ПОЧЁТА» — живой рейтинг надёжности помощников и лента благодарностей.
 */
class HeatmapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val repo = FirebaseRepo(this)
        val trustRepo = ReturnAndTrustRepo()
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_HEATMAP)

        setContent {
            LostAndFindTheme {
                HeatmapAndHonorScreen(
                    repo = repo,
                    trustRepo = trustRepo,
                    initialTab = initialTab
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val TAB_HEATMAP = 0
        const val TAB_HONOR = 1
    }
}

private enum class HeatTypeFilter(val label: String) {
    ALL("ВСЕ ЗАЯВКИ"),
    LOST("ПОТЕРИ"),
    FOUND("НАХОДКИ")
}

private data class ZoneHeatStat(
    val zone: CampusZone,
    val totalCount: Int,
    val lostCount: Int,
    val foundCount: Int,
    val returnedCount: Int,
    val topCategories: List<Pair<String, Int>>,
    val recentAds: List<AdItem>
)

@Composable
private fun HeatmapAndHonorScreen(
    repo: FirebaseRepo,
    trustRepo: ReturnAndTrustRepo,
    initialTab: Int
) {
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, 1)) }
    var allAds by remember { mutableStateOf<List<AdItem>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<UserTrustSummary>>(emptyList()) }

    DisposableEffect(repo) {
        val feed = repo.feed()
        feed.start(
            onAds = { allAds = it },
            onNotifications = {}
        )
        onDispose { feed.stop() }
    }

    DisposableEffect(trustRepo) {
        val unsubscribe = trustRepo.observeLeaderboard { leaderboard = it }
        onDispose { unsubscribe() }
    }

    val screenTitle = if (selectedTab == HeatmapActivity.TAB_HEATMAP) {
        "Карта зон и рейтинг"
    } else {
        "Доска почёта"
    }

    SimpleScreen(title = screenTitle) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopSectionTab(
                    text = "ТЕПЛОВАЯ КАРТА",
                    selected = selectedTab == HeatmapActivity.TAB_HEATMAP,
                    onClick = { selectedTab = HeatmapActivity.TAB_HEATMAP },
                    modifier = Modifier.weight(1f)
                )
                TopSectionTab(
                    text = "ДОСКА ПОЧЁТА",
                    selected = selectedTab == HeatmapActivity.TAB_HONOR,
                    onClick = { selectedTab = HeatmapActivity.TAB_HONOR },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            if (selectedTab == HeatmapActivity.TAB_HEATMAP) {
                HeatmapSection(allAds = allAds)
            } else {
                HonorBoardSection(leaderboard = leaderboard)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeatmapSection(allAds: List<AdItem>) {
    val context = LocalContext.current
    var typeFilter by remember { mutableStateOf(HeatTypeFilter.ALL) }
    var onlyActive by remember { mutableStateOf(false) }
    var selectedZoneId by remember { mutableStateOf(CampusZones.ALL.first().id) }
    var autoPickedHottest by remember { mutableStateOf(false) }

    val filteredAds = remember(allAds, typeFilter, onlyActive) {
        allAds.filter { ad ->
            val typeOk = when (typeFilter) {
                HeatTypeFilter.ALL -> true
                HeatTypeFilter.LOST -> ad.type == AdType.LOST
                HeatTypeFilter.FOUND -> ad.type == AdType.FOUND
            }
            val activeOk = !onlyActive || !ad.closed
            typeOk && activeOk
        }
    }

    val unmappedZone = remember {
        CampusZone(
            id = UNMAPPED_ZONE_ID,
            title = "Другие места",
            shortTitle = "Другое место",
            floorLabel = "Вне схемы",
            keywords = emptyList()
        )
    }

    val statsByZoneId: Map<String, ZoneHeatStat> = remember(filteredAds) {
        val grouped = mutableMapOf<String, MutableList<AdItem>>()
        CampusZones.ALL.forEach { grouped[it.id] = mutableListOf() }
        grouped[UNMAPPED_ZONE_ID] = mutableListOf()

        for (ad in filteredAds) {
            val matched = CampusZones.matchZone(ad.place)
            val key = matched?.id ?: UNMAPPED_ZONE_ID
            grouped.getOrPut(key) { mutableListOf() }.add(ad)
        }

        val allZones = CampusZones.ALL + unmappedZone
        allZones.associate { zone ->
            val items = grouped[zone.id].orEmpty().sortedByDescending { it.createdAt }
            val topCats = items
                .map { it.category.trim().ifBlank { "другое" } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key to it.value }

            zone.id to ZoneHeatStat(
                zone = zone,
                totalCount = items.size,
                lostCount = items.count { it.type == AdType.LOST },
                foundCount = items.count { it.type == AdType.FOUND },
                returnedCount = items.count { it.closed },
                topCategories = topCats,
                recentAds = items.take(3)
            )
        }
    }

    val maxCount = remember(statsByZoneId) {
        CampusZones.ALL.maxOfOrNull { statsByZoneId[it.id]?.totalCount ?: 0 } ?: 0
    }

    LaunchedEffect(statsByZoneId) {
        if (!autoPickedHottest && allAds.isNotEmpty()) {
            val hottest = CampusZones.ALL
                .maxByOrNull { statsByZoneId[it.id]?.totalCount ?: 0 }
            if (hottest != null && (statsByZoneId[hottest.id]?.totalCount ?: 0) > 0) {
                selectedZoneId = hottest.id
            }
            autoPickedHottest = true
        }
    }

    val totalCount = allAds.size
    val activeCount = allAds.count { !it.closed }
    val returnedCount = allAds.count { it.closed }

    // Общая сводка по школе
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryMetricCard(
            value = totalCount.toString(),
            label = "Всего заявок",
            fill = Paper,
            modifier = Modifier.weight(1f)
        )
        SummaryMetricCard(
            value = activeCount.toString(),
            label = "В поиске",
            fill = BrandBlue.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f)
        )
        SummaryMetricCard(
            value = returnedCount.toString(),
            label = "Возвращено",
            fill = VerifiedGreen.copy(alpha = 0.65f),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(12.dp))

    // Фильтры тепловой карты
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HeatTypeFilter.entries.forEach { item ->
            FilterPill(
                text = item.label,
                selected = typeFilter == item,
                onClick = { typeFilter = item }
            )
        }
        FilterPill(
            text = if (onlyActive) "ТОЛЬКО АКТИВНЫЕ ✓" else "ТОЛЬКО АКТИВНЫЕ",
            selected = onlyActive,
            onClick = { onlyActive = !onlyActive }
        )
    }

    Spacer(Modifier.height(12.dp))

    // Интерактивная схема помещений школы по этажам
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Зоны, где чаще забывают вещи",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = "Нажмите на помещение на плане, чтобы посмотреть статистику и объявления зоны.",
                fontSize = 12.sp,
                color = Muted
            )

            Spacer(Modifier.height(10.dp))
            HeatLegendRow()
            Spacer(Modifier.height(12.dp))

            FloorHeader("2–3 ЭТАЖ · УЧЕБНЫЙ КОРПУС")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("classrooms"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "classrooms",
                    onClick = { selectedZoneId = "classrooms" },
                    modifier = Modifier.weight(1.25f)
                )
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("library"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "library",
                    onClick = { selectedZoneId = "library" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            ZoneRoomTile(
                stat = statsByZoneId.getValue("assembly"),
                maxCount = maxCount,
                selected = selectedZoneId == "assembly",
                onClick = { selectedZoneId = "assembly" },
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            FloorHeader("ПЕРЕХОДЫ · ЛЕСТНИЦЫ И РЕКРЕАЦИИ (1–3 ЭТАЖ)")
            Spacer(Modifier.height(6.dp))
            ZoneRoomTile(
                stat = statsByZoneId.getValue("corridor"),
                maxCount = maxCount,
                selected = selectedZoneId == "corridor",
                onClick = { selectedZoneId = "corridor" },
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            FloorHeader("1 ЭТАЖ · ВХОДНАЯ, СПОРТИВНАЯ И СТОЛОВАЯ ЗОНЫ")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("wardrobe"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "wardrobe",
                    onClick = { selectedZoneId = "wardrobe" },
                    modifier = Modifier.weight(1f)
                )
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("gym"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "gym",
                    onClick = { selectedZoneId = "gym" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("canteen"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "canteen",
                    onClick = { selectedZoneId = "canteen" },
                    modifier = Modifier.weight(1f)
                )
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("lobby"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "lobby",
                    onClick = { selectedZoneId = "lobby" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            FloorHeader("ТЕРРИТОРИЯ ВОКРУГ ШКОЛЫ")
            Spacer(Modifier.height(6.dp))
            val otherStat = statsByZoneId.getValue(UNMAPPED_ZONE_ID)
            if (otherStat.totalCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoneRoomTile(
                        stat = statsByZoneId.getValue("yard"),
                        maxCount = maxCount,
                        selected = selectedZoneId == "yard",
                        onClick = { selectedZoneId = "yard" },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    ZoneRoomTile(
                        stat = otherStat,
                        maxCount = maxOf(maxCount, otherStat.totalCount),
                        selected = selectedZoneId == UNMAPPED_ZONE_ID,
                        onClick = { selectedZoneId = UNMAPPED_ZONE_ID },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                ZoneRoomTile(
                    stat = statsByZoneId.getValue("yard"),
                    maxCount = maxCount,
                    selected = selectedZoneId == "yard",
                    onClick = { selectedZoneId = "yard" },
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Детальная карточка выбранной зоны
    val selectedStat = statsByZoneId[selectedZoneId] ?: statsByZoneId.getValue(CampusZones.ALL.first().id)
    SelectedZoneDetailCard(
        stat = selectedStat,
        maxCount = maxOf(maxCount, selectedStat.totalCount),
        onOpenZoneFeed = {
            if (selectedStat.zone.id == UNMAPPED_ZONE_ID) {
                context.startActivity(Intent(context, AllAdsActivity::class.java))
            } else {
                context.startActivity(AllAdsActivity.intentForZone(context, selectedStat.zone.id))
            }
        },
        onOpenAd = { ad ->
            context.startActivity(AdDetailActivity.intentFor(context, ad))
        }
    )

    Spacer(Modifier.height(14.dp))

    // Рейтинг зон по числу забытых и найденных вещей
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Топ зон по числу забытых вещей",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(8.dp))

            val rankedZones = CampusZones.ALL
                .map { statsByZoneId.getValue(it.id) }
                .sortedWith(
                    compareByDescending<ZoneHeatStat> { it.totalCount }
                        .thenByDescending { it.lostCount }
                )

            rankedZones.forEachIndexed { idx, stat ->
                val ratio = if (maxCount > 0) {
                    (stat.totalCount.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selectedZoneId = stat.zone.id }
                        .padding(vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${stat.zone.title}",
                            fontSize = 13.sp,
                            fontWeight = if (stat.zone.id == selectedZoneId) FontWeight.Bold else FontWeight.Medium,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${stat.totalCount} шт. (П:${stat.lostCount} / Н:${stat.foundCount})",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(HeatZero)
                            .border(1.dp, Ink.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ratio.coerceAtLeast(0.08f))
                                    .background(heatColor(stat.totalCount, maxCount))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedZoneDetailCard(
    stat: ZoneHeatStat,
    maxCount: Int,
    onOpenZoneFeed: () -> Unit,
    onOpenAd: (AdItem) -> Unit
) {
    val badgeColor = heatColor(stat.totalCount, maxCount)
    val heatText = heatLevelTitle(stat.totalCount, maxCount)

    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stat.zone.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        text = stat.zone.floorLabel,
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
                Text(
                    text = heatText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .border(1.5.dp, Ink, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniCounter("Потеряно", stat.lostCount, Modifier.weight(1f))
                MiniCounter("Найдено", stat.foundCount, Modifier.weight(1f))
                MiniCounter("Возвращено", stat.returnedCount, Modifier.weight(1f))
            }

            if (stat.topCategories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Чаще всего здесь забывают:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stat.topCategories.forEach { (cat, cnt) ->
                        Text(
                            text = "$cat · $cnt",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandBlue.copy(alpha = 0.55f))
                                .border(1.dp, Ink, RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (stat.recentAds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Последние заявки в этой зоне:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                stat.recentAds.forEach { ad ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(HeatZero)
                            .border(1.dp, Ink.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable { onOpenAd(ad) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ad.type == AdType.FOUND) "НАХОДКА" else "ПОТЕРЯ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (ad.type == AdType.FOUND) BrandBlue else HeatMedium)
                                .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = ad.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${ad.place.ifBlank { stat.zone.shortTitle }} · ${ad.statusLabel}",
                                fontSize = 11.sp,
                                color = Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "В этой зоне пока нет объявлений по выбранному фильтру.",
                    fontSize = 13.sp,
                    color = Muted
                )
            }

            Spacer(Modifier.height(12.dp))
            SmallDoodleButton(
                text = if (stat.zone.id == UNMAPPED_ZONE_ID) {
                    "ОТКРЫТЬ ВСЕ ОБЪЯВЛЕНИЯ"
                } else {
                    "ОБЪЯВЛЕНИЯ ЗОНЫ: ${stat.zone.shortTitle.uppercase()}"
                },
                onClick = onOpenZoneFeed,
                color = BrandBlue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HonorBoardSection(leaderboard: List<UserTrustSummary>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Рейтинг надёжности и благодарности",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Баллы начисляются за подтверждённый возврат вещи по 6-значному коду (+25), " +
                    "возвращённые находки (+10) и благодарности от владельцев (+15 и звёзды 1–5 ★).",
                fontSize = 12.sp,
                color = Muted
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        text = "Самые надёжные помощники школы",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Ink
    )
    Spacer(Modifier.height(8.dp))

    if (leaderboard.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Paper),
            border = BorderStroke(2.dp, Ink),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Пока нет завершённых возвратов с благодарностями",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Как только участники начнут подтверждать возврат вещей по 6-значному коду " +
                        "или отправлять благодарности в карточке объявления, они появятся на Доске почёта.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
    } else {
        leaderboard.take(15).forEachIndexed { index, user ->
            LeaderRowCard(rank = index + 1, summary = user)
            Spacer(Modifier.height(8.dp))
        }
    }

    val recentThanks: List<ThanksRecord> = remember(leaderboard) {
        leaderboard
            .flatMap { summary ->
                summary.recentThanks.map { rec ->
                    if (rec.toName.isBlank() && summary.userName.isNotBlank()) {
                        rec.copy(toName = summary.userName)
                    } else {
                        rec
                    }
                }
            }
            .distinctBy { "${it.toUserId}_${it.id}" }
            .sortedByDescending { it.createdAt }
            .take(10)
    }

    if (recentThanks.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Последние благодарности владельцев",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(Modifier.height(8.dp))

        recentThanks.forEach { item ->
            ThanksFeedCard(item)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LeaderRowCard(rank: Int, summary: UserTrustSummary) {
    val placeBg = when (rank) {
        1 -> GoldBadge
        2 -> SilverBadge
        3 -> BronzeBadge
        else -> BrandBlue.copy(alpha = 0.45f)
    }
    val displayName = summary.userName.ifBlank { "Участник #${summary.userId.takeLast(4)}" }

    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(placeBg)
                    .border(2.dp, Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary.rankTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted
                )
                Spacer(Modifier.height(4.dp))
                val starsPart = if (summary.thanksCount > 0) {
                    String.format(Locale.US, " · ★ %.1f", summary.averageStars)
                } else {
                    ""
                }
                Text(
                    text = "По коду: ${summary.verifiedReturns} · Возвратов: ${summary.closedFinds} · Спасибо: ${summary.thanksCount}$starsPart",
                    fontSize = 11.sp,
                    color = Ink.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${summary.reliabilityPercent}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VerifiedGreen)
                        .border(1.5.dp, Ink, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${summary.trustPoints} б.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun ThanksFeedCard(item: ThanksRecord) {
    val starsText = "★".repeat(item.stars.coerceIn(1, 5)) + "☆".repeat((5 - item.stars).coerceIn(0, 4))
    val dateStr = remember(item.createdAt) {
        if (item.createdAt > 0L) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.createdAt))
        } else {
            ""
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.5.dp, Ink),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.fromName.ifBlank { "Владелец" }} → ${item.toName.ifBlank { "Нашедший" }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = starsText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            if (item.adTitle.isNotBlank() || item.verifiedByCode) {
                Spacer(Modifier.height(3.dp))
                val codeMark = if (item.verifiedByCode) " · возврат по коду" else ""
                Text(
                    text = "Вещь: ${item.adTitle.ifBlank { "объявление" }}$codeMark",
                    fontSize = 11.sp,
                    color = Muted
                )
            }

            if (item.badge.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandBlue.copy(alpha = 0.6f))
                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            if (item.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "«${item.comment}»",
                    fontSize = 13.sp,
                    color = Ink
                )
            }

            if (dateStr.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun ZoneRoomTile(
    stat: ZoneHeatStat,
    maxCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val bg = heatColor(stat.totalCount, maxCount)
    val borderWidth = if (selected) 3.dp else 1.5.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(borderWidth, Ink, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.zone.shortTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Paper.copy(alpha = 0.9f))
                    .border(1.dp, Ink, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stat.totalCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (stat.totalCount == 0) {
                "Спокойно · 0 заявок"
            } else {
                "Потерь: ${stat.lostCount} · Находок: ${stat.foundCount}"
            },
            fontSize = 11.sp,
            color = Ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FloorHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = Muted
    )
}

@Composable
private fun HeatLegendRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(HeatZero, "0 · Спокойно")
        LegendSwatch(HeatLow, "Редко")
        LegendSwatch(HeatMedium, "Умеренно")
        LegendSwatch(HeatHigh, "Часто")
        LegendSwatch(HeatCritical, "Горячая зона")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .border(1.dp, Ink, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = Ink)
    }
}

@Composable
private fun SummaryMetricCard(
    value: String,
    label: String,
    fill: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .border(2.dp, Ink, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniCounter(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HeatZero)
            .border(1.dp, Ink.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Muted
        )
    }
}

@Composable
private fun TopSectionTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrandBluePressed else Paper)
            .border(2.dp, Ink, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Ink
        )
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrandBlue else Paper)
            .border(1.5.dp, Ink, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

private fun heatColor(count: Int, maxCount: Int): Color {
    if (count <= 0 || maxCount <= 0) return HeatZero
    val ratio = count.toFloat() / maxCount.toFloat()
    return when {
        ratio >= 0.80f && count >= 2 -> HeatCritical
        ratio >= 0.55f -> HeatHigh
        ratio >= 0.30f -> HeatMedium
        else -> HeatLow
    }
}

private fun heatLevelTitle(count: Int, maxCount: Int): String {
    if (count <= 0 || maxCount <= 0) return "СПОКОЙНО"
    val ratio = count.toFloat() / maxCount.toFloat()
    return when {
        ratio >= 0.80f && count >= 2 -> "ГОРЯЧАЯ ЗОНА"
        ratio >= 0.55f -> "ЧАСТО ТЕРЯЮТ"
        ratio >= 0.30f -> "УМЕРЕННО"
        else -> "РЕДКО"
    }
}
