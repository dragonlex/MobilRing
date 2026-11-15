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
    val photo: String = "",
    /** Автор скрыл телефон в настройках приватности — номер не показываем и звонить некуда. */
    val ownerPhoneHidden: Boolean = false,
    /** Возврат подтверждён проверочным кодом при передаче вещи. */
    val verifiedReturn: Boolean = false,
    /** UID пользователя, который нашёл и вернул вещь (для рейтинга и благодарностей). */
    val finderId: String = "",
    val finderName: String = "",
    /** UID владельца, получившего свою вещь обратно. */
    val receiverId: String = "",
    val returnedAt: Long = 0L
) {
    /** Человекочитаемый статус для ленты и личного кабинета по ТЗ. */
    val statusLabel: String
        get() = when {
            closed && verifiedReturn -> "ВОЗВРАЩЕНО · ПО КОДУ"
            closed -> "ВОЗВРАЩЕНО"
            type == AdType.FOUND -> "ИЩЕТ ВЛАДЕЛЬЦА"
            else -> "В ПОИСКЕ"
        }
}

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

/**
 * Типовые зоны учебного заведения для быстрого выбора места при подаче заявки
 * и построения тепловой карты помещений («Зоны, где чаще забывают вещи»).
 */
data class CampusZone(
    val id: String,
    val title: String,
    val shortTitle: String,
    val floorLabel: String,
    val keywords: List<String>
)

object CampusZones {
    val ALL: List<CampusZone> = listOf(
        CampusZone(
            id = "wardrobe",
            title = "Раздевалка / Гардероб",
            shortTitle = "Гардероб",
            floorLabel = "1 этаж",
            keywords = listOf("раздевалк", "гардероб", "вешалк", "шкафчик", "сменк")
        ),
        CampusZone(
            id = "gym",
            title = "Спортзал",
            shortTitle = "Спортзал",
            floorLabel = "1 этаж",
            keywords = listOf("спортзал", "физр", "физкульт", "тренажер", "зал спорт")
        ),
        CampusZone(
            id = "canteen",
            title = "Столовая / Буфет",
            shortTitle = "Столовая",
            floorLabel = "1 этаж",
            keywords = listOf("столов", "буфет", "кафе", "обед", "кулер")
        ),
        CampusZone(
            id = "lobby",
            title = "Холл и вход (1 этаж)",
            shortTitle = "Холл / Вход",
            floorLabel = "1 этаж",
            keywords = listOf("холл", "вход", "вахт", "турникет", "фойе", "1 этаж", "первый этаж", "крыльц")
        ),
        CampusZone(
            id = "corridor",
            title = "Коридор и рекреация",
            shortTitle = "Коридор",
            floorLabel = "1–3 этаж",
            keywords = listOf("коридор", "рекреац", "лестниц", "подоконник", "лавочк", "диван", "туалет", "умывальн")
        ),
        CampusZone(
            id = "classrooms",
            title = "Учебные кабинеты",
            shortTitle = "Кабинеты",
            floorLabel = "2–3 этаж",
            keywords = listOf("кабинет", "каб", "аудитор", "класс", "парт", "лаборатор", "2 этаж", "3 этаж", "этаж")
        ),
        CampusZone(
            id = "library",
            title = "Библиотека / Коворкинг",
            shortTitle = "Библиотека",
            floorLabel = "2 этаж",
            keywords = listOf("библиотек", "читальн", "коворкинг", "медиатек")
        ),
        CampusZone(
            id = "assembly",
            title = "Актовый зал",
            shortTitle = "Актовый зал",
            floorLabel = "2 этаж",
            keywords = listOf("актов", "сцен", "концерт")
        ),
        CampusZone(
            id = "yard",
            title = "Двор / Стадион",
            shortTitle = "Двор / Стадион",
            floorLabel = "Территория",
            keywords = listOf("двор", "стадион", "площадк", "улиц", "парковк", "велопарковк", "ворота")
        )
    )

    /** Определяет зону по произвольному тексту места (или возвращает null, если место нестандартное). */
    fun matchZone(rawPlace: String): CampusZone? {
        val norm = rawPlace.trim().lowercase().replace('ё', 'е')
        if (norm.isBlank()) return null
        ALL.firstOrNull { it.title.lowercase().replace('ё', 'е') == norm }?.let { return it }
        return ALL.firstOrNull { zone ->
            zone.keywords.any { kw -> norm.contains(kw) }
        }
    }
}
