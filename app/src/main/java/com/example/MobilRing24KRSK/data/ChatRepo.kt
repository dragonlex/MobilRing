package com.example.MobilRing24KRSK.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Одно сообщение переписки.
 *
 * @param senderId uid автора — по нему сообщение рисуется слева или справа
 * @param senderName имя на момент отправки: профиль могли поменять позже
 * @param at время отправки в миллисекундах, по нему же сортируем
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val at: Long = 0L
)

/**
 * Переписка в списке «Сообщения»: шапка чата без самих сообщений.
 *
 * @param adId объявление, из-за которого начался чат
 * @param otherUid второй участник — его имя берём из users, чтобы показать в списке
 */
data class ChatInfo(
    val id: String = "",
    val adId: String = "",
    val adTitle: String = "",
    val adType: String = "",
    val lastText: String = "",
    val lastAt: Long = 0L,
    val otherUid: String = "",
    val otherName: String = ""
)

/**
 * Переписка по объявлению.
 *
 * Узел базы: `chats/{chatId}` — шапка чата (участники, объявление, последнее сообщение),
 * `chats/{chatId}/messages/{pushId}` — сами сообщения.
 *
 * Ключ чата вычисляется из двух uid и номера объявления, поэтому обе стороны
 * открывают один и тот же узел, не создавая дубликатов. Порядок uid сортируется —
 * так ключ не зависит от того, кто начал переписку.
 */
class ChatRepo {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val chats = database.getReference(NODE_CHATS)

    /** Ключ чата: два uid в отсортированном порядке плюс номер объявления. */
    fun chatId(myUid: String, ownerUid: String, adId: String): String {
        val pair = listOf(myUid, ownerUid).sorted()
        return "${pair[0]}__${pair[1]}__$adId"
    }

    /**
     * Создаёт шапку чата, если её ещё нет.
     *
     * Повторный вызов безопасен: существующую шапку не трогаем, чтобы не затереть
     * последнее сообщение и дату создания.
     */
    fun ensureChat(
        chatId: String,
        ad: AdItem,
        myUid: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val ref = chats.child(chatId)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onResult(true)
                    return
                }
                val meta = hashMapOf<String, Any>(
                    "adId" to ad.id,
                    "adTitle" to ad.title,
                    "adType" to ad.type.node,
                    "createdAt" to System.currentTimeMillis(),
                    "participants" to hashMapOf(ad.ownerId to true, myUid to true)
                )
                ref.updateChildren(meta)
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener { onResult(false) }
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(false)
            }
        })
    }

    /** Подписка на сообщения чата; возвращённый объект нужно закрыть при выходе с экрана. */
    fun listenMessages(chatId: String, onMessages: (List<ChatMessage>) -> Unit): ChatListener {
        val ref = chats.child(chatId).child("messages")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull { child ->
                    val message = child.getValue(ChatMessage::class.java) ?: return@mapNotNull null
                    message.copy(id = child.key.orEmpty())
                }
                onMessages(items.sortedBy { it.at })
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        return ChatListener(ref, listener)
    }

    /** Отправляет сообщение и обновляет «последнее сообщение» в шапке чата. */
    fun send(
        chatId: String,
        uid: String,
        senderName: String,
        text: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Пустое сообщение")
            return
        }
        val now = System.currentTimeMillis()
        val body = hashMapOf<String, Any>(
            "senderId" to uid,
            "senderName" to senderName,
            "text" to trimmed,
            "at" to now
        )
        val ref = chats.child(chatId)
        ref.child("messages").push().setValue(body)
            .addOnSuccessListener {
                ref.updateChildren(hashMapOf<String, Any>("lastText" to trimmed, "lastAt" to now))
                onResult(true, "")
            }
            .addOnFailureListener { error ->
                onResult(false, error.message ?: "Не удалось отправить сообщение")
            }
    }

    companion object {
        const val NODE_CHATS = "chats"
    }
}

/** Пара «ссылка + слушатель»: нужна, чтобы отписаться от базы при выходе с экрана. */
class ChatListener(
    private val ref: com.google.firebase.database.DatabaseReference,
    private val listener: ValueEventListener
) {
    fun cancel() = ref.removeEventListener(listener)
}

/**
 * Список моих переписок: слушает `chats` и `users`, отдаёт готовые [ChatInfo].
 *
 * Фильтрация по участнику делается в коде: в школьном масштабе чатов немного,
 * а индекс в правилах базы для такого запроса не нужен.
 */
class ChatsFeed {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val chatsRef = database.getReference(ChatRepo.NODE_CHATS)
    private val usersRef = database.getReference(FirebaseRepo.NODE_USERS)

    private var rawChats: DataSnapshot? = null
    private var users: Map<String, UserRecord> = emptyMap()

    private var onChats: ((List<ChatInfo>) -> Unit)? = null
    private var myUid: String = ""

    private val chatsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            rawChats = snapshot
            publish()
        }

        override fun onCancelled(error: DatabaseError) = Unit
    }

    private val usersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val result = linkedMapOf<String, UserRecord>()
            for (child in snapshot.children) {
                val key = child.key ?: continue
                result[key] = child.getValue(UserRecord::class.java) ?: continue
            }
            users = result
            publish()
        }

        override fun onCancelled(error: DatabaseError) = Unit
    }

    fun start(uid: String, onChats: (List<ChatInfo>) -> Unit) {
        stop()
        myUid = uid
        this.onChats = onChats
        if (uid.isBlank()) return
        chatsRef.addValueEventListener(chatsListener)
        usersRef.addValueEventListener(usersListener)
    }

    fun stop() {
        chatsRef.removeEventListener(chatsListener)
        usersRef.removeEventListener(usersListener)
        rawChats = null
        users = emptyMap()
        onChats = null
    }

    private fun publish() {
        val snapshot = rawChats ?: return
        val callback = onChats ?: return

        val items = mutableListOf<ChatInfo>()
        for (child in snapshot.children) {
            val id = child.key ?: continue
            val participants = child.child("participants")
            if (!participants.hasChild(myUid)) continue

            val otherUid = participants.children.mapNotNull { it.key }.firstOrNull { it != myUid }.orEmpty()

            items.add(
                ChatInfo(
                    id = id,
                    adId = child.child("adId").getValue(String::class.java).orEmpty(),
                    adTitle = child.child("adTitle").getValue(String::class.java).orEmpty(),
                    adType = child.child("adType").getValue(String::class.java).orEmpty(),
                    lastText = child.child("lastText").getValue(String::class.java).orEmpty(),
                    lastAt = child.child("lastAt").getValue(Long::class.java) ?: 0L,
                    otherUid = otherUid,
                    otherName = users[otherUid]?.name.orEmpty()
                )
            )
        }
        callback(items.sortedByDescending { it.lastAt })
    }
}
