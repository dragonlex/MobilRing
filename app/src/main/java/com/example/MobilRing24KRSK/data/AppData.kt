package com.example.MobilRing24KRSK.data

/**
 * Модели данных приложения.
 *
 * Здесь описано, как объявление и уведомление выглядят внутри приложения.
 * Важно: в самой базе Firebase ключи записаны строчными буквами
 * (name, location, info, userId, eventDate) — см. FirebaseRepo.
 */

/**
 * Уведомление о найденном совпадении — то, что видно в разделе «Уведомления».
 *
 * @param id ключ пары в узле matches
 * @param title заголовок карточки («Возможное совпадение»)
 * @param message готовый текст: чья заявка с чем совпала
 * @param adId ID собственной заявки пользователя
 * @param date дата создания пары в виде строки для показа
 * @param createdAt та же дата в миллисекундах — по ней сортируем список
 * @param otherAd объявление второй стороны; по нему открывается экран просмотра
 */
data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val adId: String,
    val date: String,
    val createdAt: Long = 0L,
    val otherAd: AdItem? = null
)

/**
 * Одно объявление (пропажа или находка) в том виде, в котором его рисует интерфейс.
 *
 * @param date дата публикации строкой (dd.MM.yyyy)
 * @param createdAt дата публикации в миллисекундах — для сортировки ленты
 * @param eventDate дата потери/находки в миллисекундах (полночь по местному времени отправителя)
 * @param eventDateText та же дата строкой — её и показываем в карточке
 * @param ownerName/ownerPhone/ownerAddress/ownerAbout данные владельца из узла users
 * @param category категория вещи (одежда, ключи, …) — из списка [AdCategories]
 * @param photo снимок вещи в base64 (JPEG, сжатие на телефоне); пустая строка — снимка нет
 */
data class AdItem(
    val id: String,
    val type: AdType,
    val title: String,
    val place: String,
    val date: String,
    val isMine: Boolean,
    val createdAt: Long = 0L,
    val description: String = "",
    val closed: Boolean = false,
    val eventDate: Long = 0L,
    val eventDateText: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val ownerAddress: String = "",
    val ownerAbout: String = "",
    val category: String = "",
    val ownerId: String = "",
    val photo: String = ""
)

/**
 * Список категорий вещей. Тот же список используется помощником описаний на сервере
 * (`assist.py`), поэтому названия строк должны совпадать буква в букву.
 */
object AdCategories {
    const val OTHER = "другое"

    val ALL: List<String> = listOf(
        "одежда",
        "обувь",
        "ключи",
        "телефон и гаджеты",
        "рюкзак и сумка",
        "документы и карты",
        "деньги и кошелёк",
        "учебники и книги",
        "спортивная форма",
        "украшения и аксессуары",
        OTHER
    )
}

/**
 * Тип объявления. Значение [node] — имя узла в Firebase, куда пишется объявление,
 * оно же используется матчером на сервере.
 */
enum class AdType(val label: String, val node: String) {
    LOST("ПОТЕРЯ", "lost"),
    FOUND("НАХОДКА", "found")
}
