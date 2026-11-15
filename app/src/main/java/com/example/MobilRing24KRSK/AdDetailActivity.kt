package com.example.MobilRing24KRSK

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.MobilRing24KRSK.data.AdItem
import com.example.MobilRing24KRSK.data.AdType
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper

/**
 * Просмотр объявления: всё, что есть в заявке, плюс контакты автора и кнопка звонка.
 *
 * Данные приходят из ленты готовыми строками через [intentFor] — экран не ходит
 * в сеть заново. Кнопка «ПОЗВОНИТЬ» ведёт себя так:
 *  - нет разрешения CALL_PHONE → спрашиваем разрешение;
 *  - SIM готова → ACTION_CALL, номер набирается сразу;
 *  - SIM нет или звонок запрещён → ACTION_DIAL, открывается клавиатура с номером;
 *  - набор вообще недоступен → показываем сообщение об ошибке.
 */
class AdDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val ad = readAd(intent)
        setContent {
            LostAndFindTheme {
                AdDetailScreen(ad)
            }
        }
    }

    private fun readAd(intent: Intent): AdItem {
        val type = if (intent.getStringExtra(EXTRA_TYPE) == AdType.FOUND.node) {
            AdType.FOUND
        } else {
            AdType.LOST
        }
        return AdItem(
            id = intent.getStringExtra(EXTRA_ID).orEmpty(),
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
            ownerAbout = intent.getStringExtra(EXTRA_OWNER_ABOUT).orEmpty()
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

        fun intentFor(context: Context, ad: AdItem): Intent =
            Intent(context, AdDetailActivity::class.java).apply {
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
fun AdDetailScreen(ad: AdItem) {
    val context = LocalContext.current
    val number = dialNumber(ad.ownerPhone)
    val canCall = number.isNotBlank()

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
                Spacer(Modifier.weight(1f))
                Text(text = ad.date, fontSize = 12.sp, color = Muted)
            }

            Spacer(Modifier.height(14.dp))
            Text(text = ad.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)

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
                    if (ad.ownerPhone.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = ad.ownerPhone, fontSize = 14.sp, color = Ink)
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

            Spacer(Modifier.height(18.dp))
            DoodleButton(
                text = if (canCall) "ПОЗВОНИТЬ" else "ТЕЛЕФОН НЕ УКАЗАН",
                onClick = onCallClick,
                enabled = canCall
            )
            if (canCall) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Номер наберётся автоматически. Если приложение не получило разрешение на звонки, откроется обычная клавиатура набора.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            if (ad.isMine) {
                Spacer(Modifier.height(12.dp))
                Text(text = "это ваше объявление", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
