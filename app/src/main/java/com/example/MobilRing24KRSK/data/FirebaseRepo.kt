/**
 * Работа с Firebase Realtime Database.
 *
 * Структура базы:
 *   lost/{key}   — пропажи: id, name, location, info, date, eventDate, closed, userId
 *   found/{key}  — находки (те же поля)
 *   users/{uid}  — контакты автора: name, phone, address, about
 *   matches/{k}  — пары «пропажа ↔ находка», их пишет внешний matcher.py на сервере
 *
 * В этой части файла: модели записей, вход в Firebase, публикация объявления
 * и чтение ленты с уведомлениями (класс DbFeed, ниже).
 */
package com.example.MobilRing24KRSK.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdRecord(
    var id: String = "",
    var name: String = "",
    var location: String = "",
    var date: Long = 0L,
    var eventDate: Long = 0L,
    var info: String = "",
    var category: String = "",
    var closed: Boolean = false,
    var userId: String = "",
    var photo: String = ""
)

data class UserRecord(
    var name: String = "",
    var phone: String = "",
    var address: String = "",
    var about: String = ""
)

data class MatchPair(
    val key: String,
    val findId: String,
    val lostId: String,
    val createdAt: Long
)

/**
 * Вход в Firebase без пароля и без регистрации.
 *
 * Приложение вызывает [ensure], а он один раз выполняет signInAnonymously.
 * Пока идёт вход, обращения к базе ставить бессмысленно (правила требуют auth != null),
 * поэтому они копятся в [pending] и выполняются разом в [flush].
 * На медленных устройствах вход может зависнуть — на этот случай есть таймаут,
 * чтобы интерфейс не «залипал» на кнопке «ОТПРАВКА…».
 */
object AuthGate {

    private const val TAG = "MobilRingAuth"
    private const val TIMEOUT_MS = 20000L

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var ready = false
    private var inFlight = false
    private val pending = mutableListOf<(Boolean) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    fun userId(): String = auth.currentUser?.uid.orEmpty()

    fun ensure(onReady: (Boolean) -> Unit) {
        if (ready || auth.currentUser != null) {
            ready = true
            Log.i(TAG, "ensure: уже авторизован uid=${userId()}")
            onReady(true)
            return
        }

        pending.add(onReady)
        if (inFlight) {
            Log.i(TAG, "ensure: вход уже выполняется, ждём")
            return
        }

        inFlight = true
        Log.i(TAG, "ensure: запускаю signInAnonymously")
        handler.postDelayed(
            {
                if (inFlight) {
                    Log.e(TAG, "signInAnonymously: таймаут ${TIMEOUT_MS}мс")
                    inFlight = false
                    flush(false)
                }
            },
            TIMEOUT_MS
        )
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                Log.i(TAG, "signInAnonymously: успех uid=${result.user?.uid}")
                ready = true
                inFlight = false
                flush(true)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "signInAnonymously: ошибка ${error.javaClass.simpleName}", error)
                inFlight = false
                flush(false)
            }
    }

    private fun flush(success: Boolean) {
        val callbacks = pending.toList()
        pending.clear()
        callbacks.forEach { it(success) }
    }
}

/**
 * Публикация объявлений в базу и создание «живой» ленты.
 *
 * [submit] — отправка формы: сначала проверяет вход, потом пишет запись.
 * [feed] — объект DbFeed, который слушает базу и сам сообщает об изменениях.
 */
class FirebaseRepo(context: Context) {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    /**
     * Отправляет объявление. Колбэк [onResult] вызывается один раз:
     * либо с ключом созданной записи, либо с текстом ошибки.
     */
    fun submit(
        type: AdType,
        title: String,
        place: String,
        description: String,
        category: String,
        eventDate: Long,
        profile: Profile,
        photo: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "Не удалось войти в Firebase анонимно")
                return@ensure
            }
            pushAd(type, title, place, description, category, eventDate, profile, photo, onResult)
        }
    }

    /**
     * Запись объявления в узел lost/found.
     *
     * Заодно перезаписывает контакты в users/{uid}: если человек поменял телефон,
     * старые объявления начнут показывать новый номер — правим в одном месте.
     */
    private fun pushAd(
        type: AdType,
        title: String,
        place: String,
        description: String,
        category: String,
        eventDate: Long,
        profile: Profile,
        photo: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val node = database.getReference(type.node)
        val newRef = node.push()
        val key = newRef.key
        if (key == null) {
            onResult(false, "Firebase не вернул ключ объявления")
            return
        }

        val record = AdRecord(
            id = key,
            name = title.trim(),
            location = place.trim(),
            date = System.currentTimeMillis(),
            eventDate = if (eventDate > 0L) eventDate else System.currentTimeMillis(),
            info = description.trim(),
            category = category.trim(),
            closed = false,
            userId = AuthGate.userId(),
            photo = photo.trim()
        )

        val user = UserRecord(
            name = profile.fullName.trim(),
            phone = profile.phone.trim(),
            address = profile.address.trim(),
            about = profile.about.trim()
        )

        val uid = AuthGate.userId()
        if (uid.isNotBlank()) {
            database.getReference(NODE_USERS).child(uid).setValue(user)
        }

        newRef.setValue(record)
            .addOnSuccessListener {
                onResult(true, key)
            }
            .addOnFailureListener { error ->
                onResult(false, error.message ?: "Неизвестная ошибка Firebase")
            }
    }

    fun feed(): DbFeed = DbFeed(database)

    /**
     * Ставит или снимает отметку «вещь найдена / возвращена».
     *
     * Меняется только поле closed у одной записи — сама заявка и история переписки
     * остаются на месте, поэтому объявление легко открыть снова.
     */
    fun setClosed(
        type: AdType,
        key: String,
        closed: Boolean,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        database.getReference(type.node).child(key).child("closed").setValue(closed)
            .addOnSuccessListener { onResult(true, "") }
            .addOnFailureListener { error ->
                onResult(false, error.message ?: "Не удалось изменить статус объявления")
            }
    }

    /**
     * Удаляет своё объявление вместе со всеми парами, где оно участвовало.
     *
     * Если оставить пары, в уведомлениях второй стороны появились бы ссылки
     * на несуществующую заявку. Поэтому сначала собираем связанные ключи из matches,
     * а потом одной операцией updateChildren удаляем и заявку, и пары.
     * Проверка «это моё объявление» делается на экране: кнопка показывается только владельцу.
     */
    fun deleteAd(type: AdType, key: String, onResult: (success: Boolean, message: String) -> Unit) {
        database.getReference(DbFeed.NODE_MATCHES).get()
            .addOnSuccessListener { snapshot ->
                val related = snapshot.children.mapNotNull { pair ->
                    val lostId = pair.child("lostId").value?.toString().orEmpty()
                    val findId = pair.child("findId").value?.toString().orEmpty()
                    pair.key?.takeIf { lostId == key || findId == key }
                }

                val updates = hashMapOf<String, Any?>()
                updates["${type.node}/$key"] = null
                related.forEach { updates["${DbFeed.NODE_MATCHES}/$it"] = null }

                database.reference.updateChildren(updates)
                    .addOnSuccessListener { onResult(true, key) }
                    .addOnFailureListener { error ->
                        onResult(false, error.message ?: "Не удалось удалить объявление")
                    }
            }
            .addOnFailureListener { error ->
                onResult(false, error.message ?: "Не удалось прочитать совпадения")
            }
    }

    companion object {
        const val NODE_USERS = "users"
    }
}

/**
 * «Живая» лента: подписка на узлы базы.
 *
 * Экран вызывает [start] и получает готовые списки объявлений и уведомлений,
 * а в [stop] отписывается — иначе слушатели останутся висеть после закрытия экрана
 * и будут впустую тянуть данные (и мешать в тестах).
 *
 * При любом изменении любого узла вызывается [publish]: он пересобирает списки
 * из того, что уже прочитано, и отдаёт их интерфейсу.
 */
class DbFeed(
    private val database: FirebaseDatabase
) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var lostRecords: Map<String, AdRecord> = emptyMap()
    private var foundRecords: Map<String, AdRecord> = emptyMap()
    private var users: Map<String, UserRecord> = emptyMap()
    private var matches: List<MatchPair> = emptyList()

    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    private var onAds: ((List<AdItem>) -> Unit)? = null
    private var onNotifications: ((List<NotificationItem>) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun start(
        onAds: (List<AdItem>) -> Unit,
        onNotifications: (List<NotificationItem>) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        stop()
        this.onAds = onAds
        this.onNotifications = onNotifications
        this.onError = onError

        AuthGate.ensure { authorized ->
            if (!authorized) {
                onError("Не удалось войти в Firebase анонимно")
                return@ensure
            }
            listen(AdType.LOST.node) { snapshot -> lostRecords = readAds(snapshot) }
            listen(AdType.FOUND.node) { snapshot -> foundRecords = readAds(snapshot) }
            listen(FirebaseRepo.NODE_USERS) { snapshot -> users = readUsers(snapshot) }
            listen(NODE_MATCHES) { snapshot -> matches = readMatches(snapshot) }
        }
    }

    fun stop() {
        listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        listeners.clear()
        lostRecords = emptyMap()
        foundRecords = emptyMap()
        users = emptyMap()
        matches = emptyList()
        onAds = null
        onNotifications = null
        onError = null
    }

    private fun listen(node: String, apply: (DataSnapshot) -> Unit) {
        val ref = database.getReference(node)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                apply(snapshot)
                publish()
            }

            override fun onCancelled(error: DatabaseError) {
                onError?.invoke("Firebase: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        listeners.add(ref to listener)
    }

    private fun publish() {
        onAds?.invoke(buildAds())
        onNotifications?.invoke(buildNotifications())
    }

    private fun buildAds(): List<AdItem> {
        val myId = AuthGate.userId()
        val items = mutableListOf<AdItem>()

        lostRecords.forEach { (id, record) ->
            items.add(record.toItem(id, AdType.LOST, isMine(record, myId)))
        }
        foundRecords.forEach { (id, record) ->
            items.add(record.toItem(id, AdType.FOUND, isMine(record, myId)))
        }

        return items.sortedByDescending { it.createdAt }
    }

    private fun isMine(record: AdRecord, myId: String): Boolean =
        myId.isNotBlank() && record.userId == myId

    /**
     * Превращает пары из matches в уведомления для текущего пользователя.
     *
     * Пара показывается только тому, чья заявка в ней участвует. Если пользователь
     * указал и пропажу, и находку — текст будет «ваша пропажа … и ваша находка …».
     * В [NotificationItem.otherAd] кладём объявление второй стороны: по нему
     * из уведомления открывается экран просмотра и кнопка звонка.
     */
    private fun buildNotifications(): List<NotificationItem> {
        val myId = AuthGate.userId()
        if (myId.isBlank()) return emptyList()

        val result = mutableListOf<NotificationItem>()

        matches.forEach { pair ->
            val minePairLost = lostRecords[pair.lostId]?.userId == myId
            val minePairFound = foundRecords[pair.findId]?.userId == myId
            if (!minePairLost && !minePairFound) return@forEach

            val lostAd = lostRecords[pair.lostId]
            val foundAd = foundRecords[pair.findId]

            val lostTitle = lostAd?.name?.takeIf { it.isNotBlank() } ?: "заявка ${pair.lostId}"
            val foundTitle = foundAd?.name?.takeIf { it.isNotBlank() } ?: "заявка ${pair.findId}"

            val message = when {
                minePairLost && minePairFound ->
                    "Ваша пропажа «$lostTitle» и ваша находка «$foundTitle» признаны совпадением."
                minePairLost ->
                    "Ваша пропажа «$lostTitle» совпала с находкой «$foundTitle»."
                else ->
                    "Ваша находка «$foundTitle» совпала с пропажей «$lostTitle»."
            }

            val date = if (pair.createdAt > 0L) {
                dateFormat.format(Date(pair.createdAt))
            } else {
                "—"
            }

            val otherAd = if (minePairLost) {
                foundAd?.toItem(pair.findId, AdType.FOUND, false)
            } else {
                lostAd?.toItem(pair.lostId, AdType.LOST, false)
            }

            result.add(
                NotificationItem(
                    id = pair.key,
                    title = "Возможное совпадение",
                    message = message,
                    adId = if (minePairLost) pair.lostId else pair.findId,
                    date = date,
                    createdAt = pair.createdAt,
                    otherAd = otherAd
                )
            )
        }

        return result.sortedByDescending { it.createdAt }
    }

    private fun AdRecord.toItem(id: String, type: AdType, mine: Boolean): AdItem {
        val owner = users[userId]
        return AdItem(
            id = id,
            type = type,
            title = name.ifBlank { "(без названия)" },
            place = location,
            date = if (date > 0L) dateFormat.format(Date(date)) else "—",
            isMine = mine,
            createdAt = date,
            description = info,
            closed = closed,
            eventDate = eventDate,
            eventDateText = if (eventDate > 0L) dateFormat.format(Date(eventDate)) else "",
            ownerName = owner?.name.orEmpty(),
            ownerPhone = owner?.phone.orEmpty(),
            ownerAddress = owner?.address.orEmpty(),
            ownerAbout = owner?.about.orEmpty(),
            category = category,
            ownerId = userId,
            photo = photo
        )
    }

    private fun readUsers(snapshot: DataSnapshot): Map<String, UserRecord> {
        val result = linkedMapOf<String, UserRecord>()
        for (child in snapshot.children) {
            val key = child.key ?: continue
            val record = child.getValue(UserRecord::class.java) ?: continue
            result[key] = record
        }
        return result
    }

    private fun readAds(snapshot: DataSnapshot): Map<String, AdRecord> {
        val result = linkedMapOf<String, AdRecord>()
        for (child in snapshot.children) {
            val key = child.key ?: continue
            val record = child.getValue(AdRecord::class.java) ?: continue
            if (record.id.isBlank()) record.id = key
            result[key] = record
        }
        return result
    }

    private fun readMatches(snapshot: DataSnapshot): List<MatchPair> {
        val result = mutableListOf<MatchPair>()
        for (child in snapshot.children) {
            val findId = child.child("findId").value?.toString()?.trim().orEmpty()
            val lostId = child.child("lostId").value?.toString()?.trim().orEmpty()
            if (findId.isBlank() || lostId.isBlank()) continue

            val created = (child.child("createdAt").value as? Number)?.toLong()
                ?: (child.child("created_at").value as? Number)?.toLong()
                ?: (child.child("time").value as? Number)?.toLong()
                ?: 0L

            result.add(
                MatchPair(
                    key = child.key ?: "$lostId-$findId",
                    findId = findId,
                    lostId = lostId,
                    createdAt = created
                )
            )
        }
        return result
    }

    companion object {
        const val NODE_MATCHES = "matches"
    }
}
