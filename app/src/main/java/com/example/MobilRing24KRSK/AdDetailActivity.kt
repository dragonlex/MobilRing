package com.example.MobilRing24KRSK

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.data.AuthGate
import com.example.MobilRing24KRSK.data.ChatRepo
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.PhotoStore
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.data.ReturnAndTrustRepo
import com.example.MobilRing24KRSK.data.ThanksBadges
import com.example.MobilRing24KRSK.data.UserTrustSummary
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.components.SmallDoodleButton
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper

/**
 * Просмотр объявления: приметы и фото вещи, контакты и рейтинг надёжности автора,
 * встроенный чат, подтверждение возврата по 6-значному проверочному коду и отправка благодарности.
 */
class AdDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val ad = readAd(intent)
        val repo = FirebaseRepo(this)
        val trustRepo = ReturnAndTrustRepo()
        val profileStore = ProfileStore(this)

        val onDelete: (() -> Unit)? = if (ad.isMine && ad.id.isNotBlank()) {
            {
                repo.deleteAd(ad.type, ad.id) { success, message ->
                    if (success) {
                        Toast.makeText(this, "Объявление удалено", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Ошибка удаления: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            null
        }

        val onToggleClosed: ((Boolean, (Boolean) -> Unit) -> Unit)? =
            if (ad.isMine && ad.id.isNotBlank()) {
                { closed, done ->
                    repo.setClosed(ad.type, ad.id, closed) { success, message ->
                        done(success)
                        if (success) {
                            val text = if (closed) "Статус обновлён: Возвращено" else "Объявление снова активно"
                            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Ошибка: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                null
            }

        setContent {
            LostAndFindTheme {
                AdDetailScreen(
                    ad = ad,
                    repo = repo,
                    trustRepo = trustRepo,
                    currentUserName = profileStore.profile.fullName,
                    onDelete = onDelete,
                    onToggleClosed = onToggleClosed
                )
            }
        }
    }

    private fun readAd(intent: Intent): AdItem {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val type = if (intent.getStringExtra(EXTRA_TYPE) == AdType.FOUND.node) {
            AdType.FOUND
        } else {
            AdType.LOST
        }
        return AdItem(
            id = id,
            type = type,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            place = intent.getStringExtra(EXTRA_PLACE).orEmpty(),
            date = intent.getStringExtra(EXTRA_DATE).orEmpty(),
            isMine = intent.getBooleanExtra(EXTRA_MINE, false),
            description = intent.getStringExtra(EXTRA_INFO).orEmpty(),
            eventDateText = intent.getStringExtra(EXTRA_EVENT).orEmpty(),
            ownerName = intent.getStringExtra(EXTRA_OWNER_NAME).orEmpty(),
            ownerPhone = intent.getStringExtra(EXTRA_OWNER_PHONE).orEmpty(),
            ownerAddress = intent.getStringExtra(EXTRA_OWNER_ADDRESS).orEmpty(),
            ownerAbout = intent.getStringExtra(EXTRA_OWNER_ABOUT).orEmpty(),
            category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
            ownerId = intent.getStringExtra(EXTRA_OWNER_ID).orEmpty(),
            photo = PhotoStore.getByAdId(id),
            closed = intent.getBooleanExtra(EXTRA_CLOSED, false),
            ownerPhoneHidden = intent.getBooleanExtra(EXTRA_PHONE_HIDDEN, false),
            verifiedReturn = intent.getBooleanExtra(EXTRA_VERIFIED_RETURN, false),
            finderId = intent.getStringExtra(EXTRA_FINDER_ID).orEmpty(),
            finderName = intent.getStringExtra(EXTRA_FINDER_NAME).orEmpty(),
            receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID).orEmpty(),
            returnedAt = intent.getLongExtra(EXTRA_RETURNED_AT, 0L)
        )
    }

    companion object {
        private const val EXTRA_ID = "detail_id"
        private const val EXTRA_TYPE = "detail_type"
        private const val EXTRA_TITLE = "detail_title"
        private const val EXTRA_PLACE = "detail_place"
        private const val EXTRA_DATE = "detail_date"
        private const val EXTRA_EVENT = "detail_event"
        private const val EXTRA_INFO = "detail_info"
        private const val EXTRA_MINE = "detail_mine"
        private const val EXTRA_OWNER_NAME = "detail_owner_name"
        private const val EXTRA_OWNER_PHONE = "detail_owner_phone"
        private const val EXTRA_OWNER_ADDRESS = "detail_owner_address"
        private const val EXTRA_OWNER_ABOUT = "detail_owner_about"
        private const val EXTRA_CATEGORY = "detail_category"
        private const val EXTRA_OWNER_ID = "detail_owner_id"
        private const val EXTRA_CLOSED = "detail_closed"
        private const val EXTRA_PHONE_HIDDEN = "detail_phone_hidden"
        private const val EXTRA_VERIFIED_RETURN = "detail_verified_return"
        private const val EXTRA_FINDER_ID = "detail_finder_id"
        private const val EXTRA_FINDER_NAME = "detail_finder_name"
        private const val EXTRA_RECEIVER_ID = "detail_receiver_id"
        private const val EXTRA_RETURNED_AT = "detail_returned_at"

        fun intentFor(context: Context, ad: AdItem): Intent {
            if (ad.id.isNotBlank() && ad.photo.isNotBlank()) {
                PhotoStore.putByAdId(ad.id, ad.photo)
            }
            return Intent(context, AdDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, ad.id)
                putExtra(EXTRA_TYPE, ad.type.node)
                putExtra(EXTRA_TITLE, ad.title)
                putExtra(EXTRA_PLACE, ad.place)
                putExtra(EXTRA_DATE, ad.date)
                putExtra(EXTRA_EVENT, ad.eventDateText)
                putExtra(EXTRA_INFO, ad.description)
                putExtra(EXTRA_MINE, ad.isMine)
                putExtra(EXTRA_OWNER_NAME, ad.ownerName)
                putExtra(EXTRA_OWNER_PHONE, ad.ownerPhone)
                putExtra(EXTRA_OWNER_ADDRESS, ad.ownerAddress)
                putExtra(EXTRA_OWNER_ABOUT, ad.ownerAbout)
                putExtra(EXTRA_CATEGORY, ad.category)
                putExtra(EXTRA_OWNER_ID, ad.ownerId)
                putExtra(EXTRA_CLOSED, ad.closed)
                putExtra(EXTRA_PHONE_HIDDEN, ad.ownerPhoneHidden)
                putExtra(EXTRA_VERIFIED_RETURN, ad.verifiedReturn)
                putExtra(EXTRA_FINDER_ID, ad.finderId)
                putExtra(EXTRA_FINDER_NAME, ad.finderName)
                putExtra(EXTRA_RECEIVER_ID, ad.receiverId)
                putExtra(EXTRA_RETURNED_AT, ad.returnedAt)
            }
        }
    }
}

private fun dialNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() || it == '+' }
    return digits.ifBlank { trimmed }
}

private fun simReady(context: Context): Boolean {
    val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        ?: return false
    return manager.simState == TelephonyManager.SIM_STATE_READY
}

private fun startCall(context: Context, number: String, direct: Boolean) {
    val uri = Uri.fromParts("tel", number, null)
    val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
    try {
        context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: Exception) {
        if (direct) {
            startCall(context, number, direct = false)
        } else {
            Toast.makeText(context, "Не удалось набрать номер: ${error.message}", Toast.LENGTH_LONG)
                .show()
        }
    }
}

@Composable
fun AdDetailScreen(
    ad: AdItem,
    repo: FirebaseRepo? = null,
    trustRepo: ReturnAndTrustRepo? = null,
    currentUserName: String = "",
    onDelete: (() -> Unit)? = null,
    onToggleClosed: ((Boolean, (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val number = dialNumber(ad.ownerPhone)
    val phoneHidden = ad.ownerPhoneHidden
    val canCall = number.isNotBlank() && !phoneHidden

    var photoBase64 by remember(ad.id, ad.photo) { mutableStateOf(ad.photo) }
    var confirmDelete by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(ad.closed) }
    var verifiedReturn by remember { mutableStateOf(ad.verifiedReturn) }
    var finderId by remember { mutableStateOf(ad.finderId) }
    var finderName by remember { mutableStateOf(ad.finderName) }

    // Проверочный код возврата
    var generatedCode by remember { mutableStateOf("") }
    var codeLoading by remember { mutableStateOf(false) }
    var codeInputOpen by remember { mutableStateOf(false) }
    var codeDraft by remember { mutableStateOf("") }
    var confirmingCode by remember { mutableStateOf(false) }

    // Рейтинг надёжности автора и отправка благодарности
    var authorTrust by remember { mutableStateOf<UserTrustSummary?>(null) }
    var thanksDialogOpen by remember { mutableStateOf(false) }
    var thanksSent by remember { mutableStateOf(false) }
    var starsDraft by remember { mutableStateOf(5) }
    var badgeDraft by remember { mutableStateOf(ThanksBadges.ALL.first()) }
    var commentDraft by remember { mutableStateOf("") }
    var sendingThanks by remember { mutableStateOf(false) }

    // Подгружаем фото из базы, если переход пришёл без локального кеша (например, из уведомления).
    LaunchedEffect(ad.id) {
        if (photoBase64.isBlank() && ad.id.isNotBlank() && repo != null) {
            repo.loadAdPhoto(ad.type, ad.id) { loaded ->
                if (loaded.isNotBlank()) photoBase64 = loaded
            }
        }
    }

    LaunchedEffect(ad.ownerId, verifiedReturn, thanksSent) {
        if (ad.ownerId.isNotBlank() && trustRepo != null) {
            trustRepo.loadUserTrust(ad.ownerId) { summary ->
                authorTrust = summary
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCall(context, number, direct = simReady(context))
        } else {
            startCall(context, number, direct = false)
        }
    }

    val onCallClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        when {
            !granted -> permissionLauncher.launch(Manifest.permission.CALL_PHONE)
            else -> startCall(context, number, direct = simReady(context))
        }
    }

    // Роли при возврате вещи:
    // Владелец вещи (тот, кто получает вещь обратно и называет код):
    //   - для ПОТЕРИ это автор объявления (ad.isMine);
    //   - для НАХОДКИ это второй участник (!ad.isMine).
    // Нашедший вещь (тот, кто передаёт вещь и вводит код):
    //   - для НАХОДКИ это автор объявления (ad.isMine);
    //   - для ПОТЕРИ это второй участник (!ad.isMine).
    val isItemReceiver = (ad.type == AdType.LOST && ad.isMine) || (ad.type == AdType.FOUND && !ad.isMine)
    val isItemFinder = (ad.type == AdType.FOUND && ad.isMine) || (ad.type == AdType.LOST && !ad.isMine)

    // Кого можно поблагодарить:
    //   - автора чужой НАХОДКИ;
    //   - либо нашедшего по своей ПОТЕРЕ, если возврат подтверждён кодом и finderId известен.
    val thankTargetId = when {
        ad.type == AdType.FOUND && !ad.isMine && ad.ownerId.isNotBlank() -> ad.ownerId
        ad.type == AdType.LOST && ad.isMine && finderId.isNotBlank() -> finderId
        else -> ""
    }
    val thankTargetName = when {
        ad.type == AdType.FOUND && !ad.isMine -> ad.ownerName.ifBlank { "Нашедший" }
        ad.type == AdType.LOST && ad.isMine -> finderName.ifBlank { "Нашедший" }
        else -> ""
    }

    val currentStatusText = when {
        closed && verifiedReturn -> "ВОЗВРАЩЕНО · ПОДТВЕРЖДЕНО КОДОМ"
        closed -> "ВОЗВРАЩЕНО"
        ad.type == AdType.FOUND -> "ИЩЕТ ВЛАДЕЛЬЦА"
        else -> "В ПОИСКЕ"
    }

    SimpleScreen(title = "Объявление") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ad.type.label,
                    fontSize = 12.sp,
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
                    text = currentStatusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (closed) Paper else BrandBlue.copy(alpha = 0.45f))
                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(text = ad.date, fontSize = 12.sp, color = Muted)
            }

            Spacer(Modifier.height(14.dp))

            val photo = remember(photoBase64) { PhotoStore.decode(photoBase64) }
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Фото вещи",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(text = ad.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)

            if (ad.category.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ad.category,
                    fontSize = 12.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandBlue.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (ad.place.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Место: ${ad.place}", fontSize = 14.sp, color = Ink.copy(alpha = 0.85f))
            }

            if (ad.eventDateText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (ad.type == AdType.LOST) {
                        "Потеряно: ${ad.eventDateText}"
                    } else {
                        "Найдено: ${ad.eventDateText}"
                    },
                    fontSize = 14.sp,
                    color = Ink.copy(alpha = 0.85f)
                )
            }

            if (ad.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = ad.description,
                    fontSize = 15.sp,
                    color = Ink.copy(alpha = 0.9f)
                )
            }

            // Блок автора + его рейтинг надёжности и отзывы
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (ad.type == AdType.LOST) "Кто потерял" else "Кто нашёл",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(2.dp, Ink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = ad.ownerName.ifBlank { "Имя не указано" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    val trust = authorTrust
                    if (trust != null) {
                        Spacer(Modifier.height(6.dp))
                        val starPart = if (trust.thanksCount > 0) {
                            " · ★ ${String.format(java.util.Locale.US, "%.1f", trust.averageStars)} (${trust.thanksCount})"
                        } else {
                            ""
                        }
                        Text(
                            text = "${trust.rankTitle} · Надёжность ${trust.reliabilityPercent}%$starPart",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandBlue.copy(alpha = 0.65f))
                                .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        if (trust.verifiedReturns > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Подтверждённых возвратов по коду: ${trust.verifiedReturns}",
                                fontSize = 12.sp,
                                color = Muted
                            )
                        }
                        trust.recentThanks.take(2).forEach { item ->
                            Spacer(Modifier.height(6.dp))
                            val textLine = buildString {
                                append("«")
                                append(item.comment.ifBlank { item.badge.ifBlank { "Спасибо за возврат!" } })
                                append("» — ")
                                append(item.fromName)
                            }
                            Text(text = textLine, fontSize = 12.sp, color = Ink.copy(alpha = 0.8f))
                        }
                    }

                    if (ad.ownerPhone.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = ad.ownerPhone, fontSize = 14.sp, color = Ink)
                    } else if (phoneHidden) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = "Номер скрыт автором", fontSize = 14.sp, color = Muted)
                    }
                    if (ad.ownerAddress.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = ad.ownerAddress, fontSize = 13.sp, color = Muted)
                    }
                    if (ad.ownerAbout.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = ad.ownerAbout, fontSize = 13.sp, color = Muted)
                    }
                }
            }

            // Блок инновации ТЗ: безопасный возврат через проверочный 6-значный код
            if (ad.id.isNotBlank() && trustRepo != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Безопасный возврат по проверочному коду",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (verifiedReturn) BrandBlue.copy(alpha = 0.45f) else Paper
                    ),
                    border = BorderStroke(2.dp, Ink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (verifiedReturn) {
                            Text(
                                text = "Возврат вещи подтверждён проверочным кодом!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                            if (finderName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Вернул(а): $finderName (+25 баллов надёжности)",
                                    fontSize = 13.sp,
                                    color = Ink.copy(alpha = 0.85f)
                                )
                            }
                        } else if (isItemReceiver) {
                            Text(
                                text = "Вы забираете свою вещь? Получите 6-значный код и назовите его нашедшему " +
                                    "при личной встрече, когда убедитесь, что это ваша вещь.",
                                fontSize = 13.sp,
                                color = Ink.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(10.dp))
                            if (generatedCode.isNotBlank()) {
                                Text(
                                    text = ReturnAndTrustRepo.formatCode(generatedCode),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BrandBlue)
                                        .border(2.dp, Ink, RoundedCornerShape(6.dp))
                                        .padding(vertical = 10.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                SmallDoodleButton(
                                    text = "СКОПИРОВАТЬ КОД",
                                    color = Paper,
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Код возврата", generatedCode))
                                        Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                SmallDoodleButton(
                                    text = if (codeLoading) "СОЗДАЮ КОД…" else "ПОЛУЧИТЬ КОД ВОЗВРАТА",
                                    color = BrandBlue,
                                    onClick = {
                                        if (codeLoading) return@SmallDoodleButton
                                        codeLoading = true
                                        trustRepo.getOrCreateReturnCode(ad, currentUserName) { ok, code, msg ->
                                            codeLoading = false
                                            if (ok) {
                                                generatedCode = code
                                            } else {
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
                            }
                        } else if (isItemFinder) {
                            Text(
                                text = "Передаёте вещь владельцу? Попросите у него 6-значный код возврата " +
                                    "— заявка закроется официально, а ваш рейтинг надёжности вырастет.",
                                fontSize = 13.sp,
                                color = Ink.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(10.dp))
                            SmallDoodleButton(
                                text = "ВВЕСТИ КОД ВОЗВРАТА",
                                color = BrandBlue,
                                onClick = {
                                    codeDraft = ""
                                    codeInputOpen = true
                                }
                            )
                        }
                    }
                }
            }

            // Кнопка благодарности нашедшему
            if (thankTargetId.isNotBlank() && trustRepo != null) {
                Spacer(Modifier.height(12.dp))
                SmallDoodleButton(
                    text = if (thanksSent) "БЛАГОДАРНОСТЬ ОТПРАВЛЕНА ★" else "ПОБЛАГОДАРИТЬ НАШЕДШЕГО",
                    color = if (thanksSent) Paper else BrandBlue,
                    onClick = {
                        if (!thanksSent) thanksDialogOpen = true
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            DoodleButton(
                text = when {
                    phoneHidden -> "ТЕЛЕФОН СКРЫТ"
                    canCall -> "ПОЗВОНИТЬ"
                    else -> "ТЕЛЕФОН НЕ УКАЗАН"
                },
                onClick = onCallClick,
                enabled = canCall
            )
            if (phoneHidden) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Автор скрыл номер телефона в настройках приватности.",
                    fontSize = 12.sp,
                    color = Muted
                )
            } else if (canCall) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Номер наберётся автоматически. Если приложение не получило разрешение на звонки, откроется обычная клавиатура набора.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            if (!ad.isMine && ad.ownerId.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SmallDoodleButton(
                    text = "НАПИСАТЬ В ЧАТ",
                    color = Paper,
                    onClick = {
                        AuthGate.ensure { authorized ->
                            if (!authorized) {
                                Toast.makeText(
                                    context,
                                    "Не удалось войти в Firebase",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@ensure
                            }
                            val uid = AuthGate.userId()
                            val chatRepo = ChatRepo()
                            val chatId = chatRepo.chatId(uid, ad.ownerId, ad.id)
                            chatRepo.ensureChat(chatId, ad, uid)
                            context.startActivity(
                                ChatActivity.intentFor(context, chatId, ad.title)
                            )
                        }
                    }
                )
            }

            if (ad.isMine) {
                Spacer(Modifier.height(12.dp))
                Text(text = "это ваше объявление", fontSize = 12.sp, color = Muted)
                if (onToggleClosed != null) {
                    Spacer(Modifier.height(12.dp))
                    SmallDoodleButton(
                        text = if (closed) "ВЕРНУТЬ В ПОИСК" else "ОТМЕТИТЬ: ВОЗВРАЩЕНО",
                        color = if (closed) Paper else BrandBlue,
                        onClick = {
                            val next = !closed
                            onToggleClosed.invoke(next) { success ->
                                if (success) closed = next
                            }
                        }
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    DoodleButton(
                        text = "УДАЛИТЬ ОБЪЯВЛЕНИЕ",
                        onClick = { confirmDelete = true }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (codeInputOpen && trustRepo != null) {
        AlertDialog(
            onDismissRequest = { if (!confirmingCode) codeInputOpen = false },
            title = { Text("Подтверждение возврата") },
            text = {
                Column {
                    Text(
                        text = "Введите 6-значный проверочный код, который назвал владелец вещи при встрече:",
                        fontSize = 13.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = codeDraft,
                        onValueChange = { next ->
                            codeDraft = next.filter { it.isDigit() }.take(6)
                        },
                        label = { Text("6 цифр кода") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !confirmingCode,
                    onClick = {
                        confirmingCode = true
                        trustRepo.confirmReturnWithCode(ad, codeDraft, currentUserName) { ok, msg ->
                            confirmingCode = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (ok) {
                                closed = true
                                verifiedReturn = true
                                finderId = if (ad.type == AdType.FOUND) ad.ownerId else AuthGate.userId()
                                finderName = if (ad.type == AdType.FOUND) {
                                    ad.ownerName.ifBlank { currentUserName }
                                } else {
                                    currentUserName
                                }
                                codeInputOpen = false
                            }
                        }
                    }
                ) {
                    Text(if (confirmingCode) "ПРОВЕРКА…" else "ПОДТВЕРДИТЬ")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !confirmingCode,
                    onClick = { codeInputOpen = false }
                ) { Text("ОТМЕНА") }
            }
        )
    }

    if (thanksDialogOpen && trustRepo != null) {
        AlertDialog(
            onDismissRequest = { if (!sendingThanks) thanksDialogOpen = false },
            title = { Text("Поблагодарить: $thankTargetName") },
            text = {
                Column {
                    Text("Оценка надёжности:", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { star ->
                            Text(
                                text = if (star <= starsDraft) "★" else "☆",
                                fontSize = 28.sp,
                                color = Ink,
                                modifier = Modifier.clickable { starsDraft = star }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Быстрый тег:", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThanksBadges.ALL.forEach { badge ->
                            val selected = badgeDraft == badge
                            Text(
                                text = badge,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Paper else Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) Ink else BrandBlue)
                                    .border(1.dp, Ink, RoundedCornerShape(16.dp))
                                    .clickable { badgeDraft = badge }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { if (it.length <= 200) commentDraft = it },
                        label = { Text("Слова благодарности (необязательно)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sendingThanks,
                    onClick = {
                        sendingThanks = true
                        trustRepo.sendThanks(
                            ad = ad,
                            toUserId = thankTargetId,
                            toName = thankTargetName,
                            fromName = currentUserName,
                            stars = starsDraft,
                            badge = badgeDraft,
                            comment = commentDraft,
                            verifiedByCode = verifiedReturn
                        ) { ok, msg ->
                            sendingThanks = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (ok) {
                                thanksSent = true
                                thanksDialogOpen = false
                            }
                        }
                    }
                ) {
                    Text(if (sendingThanks) "ОТПРАВКА…" else "ОТПРАВИТЬ")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !sendingThanks,
                    onClick = { thanksDialogOpen = false }
                ) { Text("ОТМЕНА") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить объявление?") },
            text = {
                Text(
                    "Оно исчезнет из ленты вместе с найденными совпадениями. " +
                        "Вернуть объявление будет нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    }
                ) { Text("УДАЛИТЬ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("ОТМЕНА") }
            }
        )
    }
}
