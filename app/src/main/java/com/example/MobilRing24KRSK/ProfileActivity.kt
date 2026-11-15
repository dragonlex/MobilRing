package com.example.MobilRing24KRSK

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.AuthGate
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.Profile
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.data.RecoveryKey
import com.example.MobilRing24KRSK.data.ReturnAndTrustRepo
import com.example.MobilRing24KRSK.data.UserTrustSummary
import com.example.MobilRing24KRSK.ui.components.AvatarImage
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper
import java.util.Locale

/**
 * Личный кабинет участника:
 *  - вкладка «КАБИНЕТ И ЗАЯВКИ»: рейтинг надёжности, благодарности нашедшему и список своих
 *    заявок со статусами («Ищет владельца» / «В поиске» / «Возвращено»);
 *  - вкладка «АНКЕТА И КЛЮЧ»: ФИО, адрес, телефон, аватар, приватность и 16-значный ключ восстановления.
 */
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val store = ProfileStore(this)
        val repo = FirebaseRepo(this)
        val trustRepo = ReturnAndTrustRepo()
        setContent {
            LostAndFindTheme {
                ProfileScreen(
                    store = store,
                    repo = repo,
                    trustRepo = trustRepo,
                    onRestore = {
                        startActivity(Intent(this, RestoreActivity::class.java))
                    },
                    onSaved = {
                        Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

private enum class ProfileTab(val label: String) {
    CABINET("КАБИНЕТ И ЗАЯВКИ"),
    FORM("АНКЕТА И КЛЮЧ")
}

private enum class MyAdsStatusFilter(val label: String) {
    ALL("Все"),
    ACTIVE("Ищет владельца / В поиске"),
    RETURNED("Возвращено")
}

@Composable
fun ProfileScreen(
    store: ProfileStore,
    repo: FirebaseRepo,
    trustRepo: ReturnAndTrustRepo,
    onRestore: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember {
        mutableStateOf(if (store.profile.isComplete) ProfileTab.CABINET else ProfileTab.FORM)
    }

    var avatar by remember { mutableStateOf(store.profile.avatar) }
    var fullName by remember { mutableStateOf(store.profile.fullName) }
    var address by remember { mutableStateOf(store.profile.address) }
    var phone by remember { mutableStateOf(store.profile.phone) }
    var about by remember { mutableStateOf(store.profile.about) }
    var showPhone by remember { mutableStateOf(store.profile.showPhone) }
    var showAddress by remember { mutableStateOf(store.profile.showAddress) }
    var showErrors by remember { mutableStateOf(false) }

    var myAds by remember { mutableStateOf<List<AdItem>>(emptyList()) }
    var statusFilter by remember { mutableStateOf(MyAdsStatusFilter.ALL) }
    var myTrust by remember { mutableStateOf<UserTrustSummary?>(null) }

    DisposableEffect(repo) {
        val feed = repo.feed()
        feed.start(
            onAds = { all ->
                myAds = all.filter { it.isMine }
                val uid = AuthGate.userId()
                if (uid.isNotBlank()) {
                    trustRepo.loadUserTrust(uid) { summary -> myTrust = summary }
                }
            },
            onNotifications = {}
        )
        onDispose { feed.stop() }
    }

    LaunchedEffect(Unit) {
        AuthGate.ensure { ok ->
            if (ok) {
                val uid = AuthGate.userId()
                if (uid.isNotBlank()) {
                    trustRepo.loadUserTrust(uid) { summary -> myTrust = summary }
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            avatar = uri.toString()
        }
    }

    SimpleScreen(title = "Личный кабинет") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Text(
                        text = tab.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Paper else Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Ink else Paper)
                            .border(2.dp, Ink, RoundedCornerShape(8.dp))
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (activeTab == ProfileTab.CABINET) {
                CabinetSection(
                    fullName = fullName,
                    avatar = avatar,
                    trust = myTrust,
                    myAds = myAds,
                    statusFilter = statusFilter,
                    onStatusFilterChange = { statusFilter = it },
                    onEditProfileClick = { activeTab = ProfileTab.FORM },
                    onOpenAd = { ad ->
                        context.startActivity(AdDetailActivity.intentFor(context, ad))
                    },
                    onToggleAdClosed = { ad ->
                        repo.setClosed(ad.type, ad.id, !ad.closed) { ok, msg ->
                            if (!ok) {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Paper)
                            .border(2.dp, Ink, CircleShape)
                            .clickable { picker.launch(arrayOf("image/*")) }
                    ) {
                        if (avatar.isNotBlank()) {
                            AvatarImage(
                                uri = android.net.Uri.parse(avatar),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "фото",
                                color = Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text("Аватар", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "Нажмите на круг, чтобы выбрать фото",
                            fontSize = 13.sp,
                            color = Muted
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                ProfileField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "ФИО *",
                    isError = showErrors && fullName.isBlank()
                )
                Spacer(Modifier.height(12.dp))
                ProfileField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Класс / корпус / адрес *",
                    isError = showErrors && address.isBlank()
                )
                Spacer(Modifier.height(12.dp))
                ProfileField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Телефон *",
                    isError = showErrors && phone.filter { ch -> ch.isDigit() }.length < 10
                )
                Spacer(Modifier.height(12.dp))
                ProfileField(
                    value = about,
                    onValueChange = { if (it.length <= ProfileStore.ABOUT_MAX) about = it },
                    label = "О себе / соц сети или мессенджеры",
                    supporting = "${about.length}/${ProfileStore.ABOUT_MAX}"
                )

                Spacer(Modifier.height(20.dp))
                Text("Приватность", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    text = "Что видят другие люди в карточке вашего объявления.",
                    fontSize = 12.sp,
                    color = Muted
                )
                Spacer(Modifier.height(8.dp))
                PrivacySwitch(
                    checked = showPhone,
                    onCheckedChange = { showPhone = it },
                    title = "Показывать телефон",
                    subtitle = if (showPhone) {
                        "Номер виден, по нему можно позвонить"
                    } else {
                        "Вместо номера будет «телефон скрыт»"
                    }
                )
                PrivacySwitch(
                    checked = showAddress,
                    onCheckedChange = { showAddress = it },
                    title = "Показывать адрес",
                    subtitle = if (showAddress) {
                        "Адрес виден в карточке рядом с именем"
                    } else {
                        "Адрес видите только вы"
                    }
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "* — обязательные поля. Без них нельзя сообщить о пропаже или находке.",
                    fontSize = 12.sp,
                    color = Muted
                )

                Spacer(Modifier.height(22.dp))
                RecoveryKeySection(context = context, onRestore = onRestore)

                Spacer(Modifier.height(22.dp))

                DoodleButton(
                    text = "СОХРАНИТЬ",
                    onClick = {
                        val profile = Profile(
                            avatar = avatar,
                            fullName = fullName.trim(),
                            address = address.trim(),
                            phone = phone.trim(),
                            about = about.trim(),
                            showPhone = showPhone,
                            showAddress = showAddress
                        )
                        if (profile.isComplete) {
                            store.save(profile)
                            FirebaseRepo(context).saveProfile(profile)
                            onSaved()
                        } else {
                            showErrors = true
                            Toast.makeText(
                                context,
                                "Заполните ФИО, адрес и телефон",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CabinetSection(
    fullName: String,
    avatar: String,
    trust: UserTrustSummary?,
    myAds: List<AdItem>,
    statusFilter: MyAdsStatusFilter,
    onStatusFilterChange: (MyAdsStatusFilter) -> Unit,
    onEditProfileClick: () -> Unit,
    onOpenAd: (AdItem) -> Unit,
    onToggleAdClosed: (AdItem) -> Unit
) {
    val summary = trust ?: UserTrustSummary(userId = "")

    // Карточка репутации и рейтинга надёжности
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(2.dp, Ink),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(BrandBlue)
                        .border(2.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar.isNotBlank()) {
                        AvatarImage(
                            uri = android.net.Uri.parse(avatar),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = fullName.trim().take(1).uppercase().ifBlank { "Я" },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fullName.ifBlank { "Заполните имя в анкете" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = summary.rankTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandBlue)
                            .border(1.dp, Ink, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    value = "${summary.reliabilityPercent}%",
                    label = "Надёжность",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = summary.verifiedReturns.toString(),
                    label = "По коду",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = if (summary.thanksCount > 0) {
                        "★ ${String.format(Locale.US, "%.1f", summary.averageStars)}"
                    } else {
                        "0"
                    },
                    label = "Спасибо (${summary.thanksCount})",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            val context = LocalContext.current
            SmallDoodleButton(
                text = "ДОСКА ПОЧЁТА И КАРТА ЗОН",
                color = BrandBlue,
                onClick = {
                    context.startActivity(
                        Intent(context, HeatmapActivity::class.java)
                            .putExtra(HeatmapActivity.EXTRA_INITIAL_TAB, HeatmapActivity.TAB_HONOR)
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            SmallDoodleButton(
                text = "РЕДАКТИРОВАТЬ КОНТАКТЫ И КЛЮЧ",
                color = Paper,
                onClick = onEditProfileClick
            )
        }
    }

    // Полученные благодарности
    if (summary.recentThanks.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text("Благодарности от владельцев", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        summary.recentThanks.take(5).forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BrandBlue.copy(alpha = 0.35f)),
                border = BorderStroke(1.5.dp, Ink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "★".repeat(item.stars.coerceIn(1, 5)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        if (item.badge.isNotBlank()) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = item.badge,
                                fontSize = 11.sp,
                                color = Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Paper)
                                    .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.comment.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = item.comment, fontSize = 13.sp, color = Ink)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${item.fromName} · за вещь «${item.adTitle}»",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            }
        }
    }

    // Список своих объявлений со статусами по ТЗ
    Spacer(Modifier.height(18.dp))
    val activeCount = myAds.count { !it.closed }
    val returnedCount = myAds.count { it.closed }
    Text(
        text = "Мои объявления · всего ${myAds.size} (активных: $activeCount, возвращено: $returnedCount)",
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = Ink
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MyAdsStatusFilter.entries.forEach { filter ->
            val selected = statusFilter == filter
            Text(
                text = filter.label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Paper else Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Ink else Paper)
                    .border(1.5.dp, Ink, RoundedCornerShape(18.dp))
                    .clickable { onStatusFilterChange(filter) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    val filteredAds = myAds.filter { ad ->
        when (statusFilter) {
            MyAdsStatusFilter.ALL -> true
            MyAdsStatusFilter.ACTIVE -> !ad.closed
            MyAdsStatusFilter.RETURNED -> ad.closed
        }
    }

    if (filteredAds.isEmpty()) {
        Text(
            text = if (myAds.isEmpty()) {
                "У вас пока нет опубликованных заявок о пропажах или находках."
            } else {
                "Нет заявок с выбранным статусом."
            },
            fontSize = 13.sp,
            color = Muted
        )
    } else {
        filteredAds.forEach { ad ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(2.dp, Ink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable { onOpenAd(ad) }
            ) {
                Column(Modifier.padding(12.dp)) {
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
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = ad.statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (ad.closed) Paper else BrandBlue.copy(alpha = 0.5f))
                                .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(text = ad.date, fontSize = 11.sp, color = Muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = ad.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (ad.place.isNotBlank()) {
                        Text(text = ad.place, fontSize = 13.sp, color = Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (ad.closed) "Вернуть в поиск" else "Отметить: Возвращено",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (ad.closed) Paper else BrandBlue)
                                .border(1.dp, Ink, RoundedCornerShape(6.dp))
                                .clickable { onToggleAdClosed(ad) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Text(
                            text = "Код и детали →",
                            fontSize = 12.sp,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Paper)
                                .border(1.dp, Ink, RoundedCornerShape(6.dp))
                                .clickable { onOpenAd(ad) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BrandBlue.copy(alpha = 0.4f))
            .border(1.5.dp, Ink, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(text = label, fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

/**
 * Блок «Ключ восстановления».
 */
@Composable
private fun RecoveryKeySection(context: Context, onRestore: () -> Unit) {
    var key by remember { mutableStateOf(RecoveryKey.stored(context)) }
    var busy by remember { mutableStateOf(false) }

    Text("Ключ восстановления", fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(4.dp))

    if (key.isBlank()) {
        Text(
            text = "16-значный ключ привязывает аккаунт к устройству-независимому входу: " +
                "по нему на новом телефоне вернутся профиль, объявления и переписка.",
            fontSize = 12.sp,
            color = Muted
        )
        Spacer(Modifier.height(8.dp))
        SmallDoodleButton(
            text = if (busy) "СОЗДАЮ…" else "СОЗДАТЬ КЛЮЧ",
            color = Paper,
            onClick = {
                if (busy) return@SmallDoodleButton
                busy = true
                RecoveryKey.create(context) { success, message ->
                    busy = false
                    if (success) {
                        key = RecoveryKey.stored(context)
                        Toast.makeText(context, "Ключ создан", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    } else {
        Text(
            text = "Ваш ключ. Запишите его отдельно от телефона — по нему восстанавливается аккаунт.",
            fontSize = 12.sp,
            color = Muted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = RecoveryKey.format(key),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Paper)
                .border(2.dp, Ink, RoundedCornerShape(6.dp))
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        SmallDoodleButton(
            text = "СКОПИРОВАТЬ КЛЮЧ",
            color = Paper,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Ключ восстановления", RecoveryKey.format(key)))
                Toast.makeText(context, "Ключ скопирован", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Spacer(Modifier.height(10.dp))
    SmallDoodleButton(text = "ВОЙТИ ПО КЛЮЧУ", color = BrandBlue, onClick = onRestore)
}

@Composable
private fun PrivacySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = supporting == null,
        isError = isError,
        supportingText = supporting?.let { { Text(it, fontSize = 12.sp) } },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
