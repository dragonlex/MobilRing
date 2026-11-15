package com.example.MobilRing24KRSK.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Профиль пользователя: то, что человек вводит на экране «Профиль».
 *
 * Контакты автора объявления хранятся не в самом объявлении, а отдельно —
 * в узле users/{uid}, чтобы их можно было править в одном месте.
 * При публикации объявления профиль заодно перезаписывается в базу.
 */
data class Profile(
    val avatar: String = "",
    val fullName: String = "",
    val address: String = "",
    val phone: String = "",
    val about: String = "",
    /** Показывать телефон в объявлениях: false — другие увидят «номер скрыт». */
    val showPhone: Boolean = true,
    /** Показывать адрес в объявлениях: false — адрес видит только владелец. */
    val showAddress: Boolean = true
) {
    /**
     * Можно ли публиковать объявления: нужны ФИО, адрес
     * и телефон, в котором не меньше 10 цифр.
     */
    val isComplete: Boolean
        get() = fullName.isNotBlank() &&
                address.isNotBlank() &&
                phone.filter { it.isDigit() }.length >= 10
}

/**
 * Локальное хранилище профиля (SharedPreferences, файл `profile.xml`).
 *
 * Профиль нужен приложению до обращения к Firebase, поэтому он лежит на устройстве:
 * так экран «Профиль» открывается без сети и не мигает пустыми полями.
 * [profile] — Compose-состояние: интерфейс сам перерисуется после [save].
 */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var profile by mutableStateOf(load())
        private set

    /** Читает профиль из SharedPreferences. */
    private fun load(): Profile = Profile(
        avatar = prefs.getString(KEY_AVATAR, "").orEmpty(),
        fullName = prefs.getString(KEY_NAME, "").orEmpty(),
        address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
        phone = prefs.getString(KEY_PHONE, "").orEmpty(),
        about = prefs.getString(KEY_ABOUT, "").orEmpty(),
        showPhone = prefs.getBoolean(KEY_SHOW_PHONE, true),
        showAddress = prefs.getBoolean(KEY_SHOW_ADDRESS, true)
    )

    /**
     * Перечитывает профиль с диска. Нужен после возврата на главный экран:
     * профиль могли изменить в другом экране, а состояние в памяти осталось старым.
     */
    fun reload() {
        profile = load()
    }

    /** Сохраняет профиль на устройство и обновляет состояние в памяти. */
    fun save(newProfile: Profile) {
        prefs.edit()
            .putString(KEY_AVATAR, newProfile.avatar)
            .putString(KEY_NAME, newProfile.fullName)
            .putString(KEY_ADDRESS, newProfile.address)
            .putString(KEY_PHONE, newProfile.phone)
            .putString(KEY_ABOUT, newProfile.about)
            .putBoolean(KEY_SHOW_PHONE, newProfile.showPhone)
            .putBoolean(KEY_SHOW_ADDRESS, newProfile.showAddress)
            .apply()
        profile = newProfile
    }

    /** Ссылка на выбранную фотографию (постоянное разрешение уже взято при выборе). */
    fun avatarUri(): Uri? = profile.avatar.takeIf { it.isNotBlank() }?.let(Uri::parse)

    companion object {
        private const val PREFS_NAME = "profile"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_NAME = "full_name"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PHONE = "phone"
        private const val KEY_ABOUT = "about"
        private const val KEY_SHOW_PHONE = "show_phone"
        private const val KEY_SHOW_ADDRESS = "show_address"

        /** Ограничение длины поля «О себе» в символах. */
        const val ABOUT_MAX = 100
    }
}
