package com.example.MobilRing24KRSK

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.data.RecoveryKey
import com.example.MobilRing24KRSK.ui.components.DoodleButton
import com.example.MobilRing24KRSK.ui.components.SimpleScreen
import com.example.MobilRing24KRSK.ui.theme.Ink
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme
import com.example.MobilRing24KRSK.ui.theme.Muted

/**
 * «Войти по ключу»: перенос аккаунта на новый телефон.
 *
 * Человек вводит 16-значный ключ из профиля на старом телефоне, приложение
 * выполняет вход в Firebase под почтой и паролем, построенными из ключа,
 * и подтягивает профиль из users/{uid}. После этого объявления, переписка
 * и настройки приватности снова доступны — они привязаны к uid, а не к телефону.
 *
 * Ошибиться в ключе легко (это 16 знаков), поэтому дефисы и пробелы
 * при вводе можно ставить как удобно: [RecoveryKey.normalize] их убирает.
 */
class RestoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()
        val store = ProfileStore(this)
        setContent {
            LostAndFindTheme {
                RestoreScreen(
                    onRestored = { profile ->
                        if (profile != null) {
                            // Аватар лежит только на устройстве — из базы его не вернуть,
                            // поэтому сохраняем тот, что уже выбран на этом телефоне.
                            store.save(profile.copy(avatar = store.profile.avatar))
                        }
                        startActivity(
                            Intent(this, MainActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun RestoreScreen(onRestored: (com.example.MobilRing24KRSK.data.Profile?) -> Unit) {
    val context = LocalContext.current
    val repo = remember { FirebaseRepo(context) }
    var keyInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    SimpleScreen(title = "Восстановление") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Введите ключ восстановления из профиля на старом телефоне.",
                fontSize = 14.sp,
                color = Ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Это 16 знаков, обычно записаны группами по четыре: K4RT-9M2P-7XQV-3BND.",
                fontSize = 12.sp,
                color = Muted
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    error = ""
                },
                label = { Text("Ключ") },
                placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                singleLine = true,
                isError = error.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = error, fontSize = 13.sp, color = ErrorInk)
            }

            Spacer(Modifier.height(18.dp))
            DoodleButton(
                text = if (busy) "ВХОЖУ…" else "ВОЙТИ",
                enabled = !busy && keyInput.isNotBlank(),
                onClick = {
                    if (busy) return@DoodleButton
                    busy = true
                    error = ""
                    RecoveryKey.signIn(keyInput) { success, message ->
                        if (!success) {
                            busy = false
                            error = message
                            return@signIn
                        }
                        // Ключ принят: он же пригодится, если приложение переустановят.
                        RecoveryKey.save(context, message)
                        repo.loadProfile { profile, loadError ->
                            busy = false
                            if (profile == null && loadError.isNotBlank()) {
                                error = "Вход выполнен, но профиль не прочитан: $loadError"
                            }
                            onRestored(profile)
                        }
                    }
                }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "После входа профиль и объявления подтянутся из базы. " +
                        "Текущие объявления этого телефона останутся в аккаунте, " +
                        "к которому привязан ключ.",
                fontSize = 12.sp,
                color = Muted
            )
        }
    }
}

/** Цвет сообщения об ошибке — тёмно-красный, чтобы отличался от обычного текста. */
private val ErrorInk = Color(0xFF9B2C2C)
