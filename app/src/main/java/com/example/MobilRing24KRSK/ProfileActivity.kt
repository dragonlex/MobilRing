package com.example.MobilRing24KRSK

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.MobilRing24KRSK.data.Profile
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.ui.components.AvatarImage
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.BrandBlue
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted
import com.example.MobilRing24KRSK.ui.theme.Paper

/**
 * «Профиль»: ФИО, адрес, телефон и аватар.
 *
 * Данные лежат локально (ProfileStore → SharedPreferences), поэтому экран работает
 * без сети. При публикации объявления профиль автоматически попадает в базу
 * (users/{uid}) — по нему другие люди видят контакты автора.
 */
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val store = ProfileStore(this)
        setContent {
            LostAndFindTheme {
                ProfileScreen(
                    store = store,
                    onSaved = {
                        Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    store: ProfileStore,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var avatar by remember { mutableStateOf(store.profile.avatar) }
    var fullName by remember { mutableStateOf(store.profile.fullName) }
    var address by remember { mutableStateOf(store.profile.address) }
    var phone by remember { mutableStateOf(store.profile.phone) }
    var about by remember { mutableStateOf(store.profile.about) }
    var showErrors by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            avatar = uri.toString()
        }
    }

    SimpleScreen(title = "Профиль") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
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
                label = "Адрес *",
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

            Spacer(Modifier.height(10.dp))
            Text(
                text = "* — обязательные поля. Без них нельзя сообщить о пропаже или находке.",
                fontSize = 12.sp,
                color = Muted
            )

            Spacer(Modifier.height(22.dp))

            DoodleButton(
                text = "СОХРАНИТЬ",
                onClick = {
                    val profile = Profile(
                        avatar = avatar,
                        fullName = fullName.trim(),
                        address = address.trim(),
                        phone = phone.trim(),
                        about = about.trim()
                    )
                    if (profile.isComplete) {
                        store.save(profile)
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

            Spacer(Modifier.height(24.dp))
        }
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
