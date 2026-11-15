package com.example.MobilRing24KRSK

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import com.example.MobilRing24KRSK.data.DbFeed
import com.example.MobilRing24KRSK.data.FirebaseRepo
import com.example.MobilRing24KRSK.data.ProfileStore
import com.example.MobilRing24KRSK.ui.screens.HomeScreen
import com.example.MobilRing24KRSK.ui.theme.LostAndFindTheme

/**
 * Главный экран приложения — «рисованная» доска с кнопками.
 *
 * Activity ничего не рисует сама: она держит состояние (заполнен ли профиль,
 * сколько уведомлений) и отдаёт его в composable [HomeScreen], а нажатия
 * превращает в открытие нужного экрана.
 */
class MainActivity : ComponentActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var feed: DbFeed

    // Compose-состояния: меняем значение — интерфейс перерисовывается сам.
    private val profileComplete = mutableStateOf(false)
    private val avatarUri = mutableStateOf<android.net.Uri?>(null)
    private val notificationsCount = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLightSystemBars()

        profileStore = ProfileStore(this)
        feed = FirebaseRepo(this).feed()
        refreshProfile()

        setContent {
            LostAndFindTheme {
                HomeScreen(
                    notificationsCount = notificationsCount.value,
                    avatarUri = avatarUri.value,
                    profileComplete = profileComplete.value,
                    onNotificationsClick = { open(NotificationsActivity::class.java) },
                    onProfileClick = { open(ProfileActivity::class.java) },
                    onReportLostClick = { openReport(ReportActivity.TYPE_LOST) },
                    onReportFoundClick = { openReport(ReportActivity.TYPE_FOUND) },
                    onAllAdsClick = { open(AllAdsActivity::class.java) },
                    onChatsClick = { open(ChatsActivity::class.java) },
                    onHelpClick = { open(HelpActivity::class.java) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        feed.start(
            onAds = {},
            onNotifications = { notificationsCount.value = it.size }
        )
    }

    override fun onStop() {
        super.onStop()
        feed.stop()
    }

    override fun onResume() {
        super.onResume()
        if (::profileStore.isInitialized) refreshProfile()
    }

    private fun refreshProfile() {
        profileStore.reload()
        profileComplete.value = profileStore.profile.isComplete
        avatarUri.value = profileStore.avatarUri()
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    private fun openReport(type: String) {
        startActivity(
            Intent(this, ReportActivity::class.java)
                .putExtra(ReportActivity.EXTRA_TYPE, type)
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@androidx.compose.runtime.Composable
fun HomeScreenPreview() {
    LostAndFindTheme {
        HomeScreen(
            notificationsCount = 3,
            avatarUri = null,
            profileComplete = false,
            onNotificationsClick = {},
            onProfileClick = {},
            onReportLostClick = {},
            onReportFoundClick = {},
            onAllAdsClick = {},
            onChatsClick = {},
            onHelpClick = {}
        )
    }
}
