package com.example.MobilRing24KRSK.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

/** Запись проверочного 6-значного кода для безопасного подтверждения возврата вещи. */
data class ReturnCodeRecord(
    var adId: String = "",
    var adType: String = "",
    var code: String = "",
    var ownerId: String = "",
    var ownerName: String = "",
    var createdAt: Long = 0L,
    var used: Boolean = false,
    var usedBy: String = "",
    var usedByName: String = "",
    var usedAt: Long = 0L
)

/** Благодарность и оценка нашедшему от владельца вещи. */
data class ThanksRecord(
    var id: String = "",
    var adId: String = "",
    var adTitle: String = "",
    var fromUserId: String = "",
    var fromName: String = "",
    var toUserId: String = "",
    var toName: String = "",
    var stars: Int = 5,
    var badge: String = "",
    var comment: String = "",
    var verifiedByCode: Boolean = false,
    var createdAt: Long = 0L
)

/** Сводка репутации и рейтинга надёжности пользователя. */
data class UserTrustSummary(
    val userId: String,
    val userName: String = "",
    val verifiedReturns: Int = 0,
    val closedFinds: Int = 0,
    val thanksCount: Int = 0,
    val averageStars: Double = 0.0,
    val recentThanks: List<ThanksRecord> = emptyList()
) {
    /** Баллы надёжности: учитывают подтверждённые кодом возвраты, закрытые находки и благодарности. */
    val trustPoints: Int
        get() = verifiedReturns * 25 + closedFinds * 10 + thanksCount * 15 +
            if (thanksCount > 0) (averageStars * 4).toInt() else 0

    /** Индекс надёжности (в процентах 50..100%) для наглядного отображения в профиле и карточке. */
    val reliabilityPercent: Int
        get() {
            if (verifiedReturns == 0 && closedFinds == 0 && thanksCount == 0) return 75
            val base = 78 + verifiedReturns * 6 + closedFinds * 2 + thanksCount * 3
            val starAdjust = if (thanksCount > 0) ((averageStars - 4.0) * 6.0).toInt() else 0
            return (base + starAdjust).coerceIn(50, 100)
        }

    /** Уровень (звание) участника школьного бюро находок. */
    val rankTitle: String
        get() = when {
            trustPoints >= 120 -> "Хранитель школы"
            trustPoints >= 60 -> "Надёжный помощник"
            trustPoints >= 20 -> "Отзывчивый участник"
            else -> "Участник бюро"
        }
}

object ThanksBadges {
    val ALL = listOf(
        "Быстро вернул вещь",
        "В целости и сохранности",
        "Вежливый и честный",
        "Удобно договорились в чате"
    )
}

/**
 * Репозиторий инновационных механик доверия:
 *  1) подтверждение возврата вещи через 6-значный проверочный код (`returnCodes/{adId}`);
 *  2) благодарности и оценки нашедшим (`thanks/{finderUid}/{thankId}`);
 *  3) расчёт рейтинга надёжности пользователя и доски почёта.
 */
class ReturnAndTrustRepo {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    /**
     * Получает существующий активный код возврата для объявления или создаёт новый 6-значный код.
     * Код генерирует сторона, которая ПОЛУЧАЕТ свою вещь обратно (владелец вещи),
     * и называет его при личной встрече нашедшему.
     */
    fun getOrCreateReturnCode(
        ad: AdItem,
        requesterName: String,
        onResult: (success: Boolean, code: String, message: String) -> Unit
    ) {
        if (ad.id.isBlank()) {
            onResult(false, "", "Некорректный идентификатор объявления")
            return
        }
        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "", "Не удалось войти в Firebase")
                return@ensure
            }
            val uid = AuthGate.userId()
            val ref = database.getReference(NODE_CODES).child(ad.id)
            ref.get()
                .addOnSuccessListener { snapshot ->
                    val existing = snapshot.getValue(ReturnCodeRecord::class.java)
                    if (existing != null && !existing.used && existing.code.length == 6 && existing.ownerId == uid) {
                        onResult(true, existing.code, "")
                        return@addOnSuccessListener
                    }
                    val generated = Random.nextInt(100000, 1000000).toString()
                    val record = ReturnCodeRecord(
                        adId = ad.id,
                        adType = ad.type.node,
                        code = generated,
                        ownerId = uid,
                        ownerName = requesterName.trim(),
                        createdAt = System.currentTimeMillis(),
                        used = false
                    )
                    ref.setValue(record)
                        .addOnSuccessListener { onResult(true, generated, "") }
                        .addOnFailureListener { e ->
                            onResult(false, "", e.message ?: "Не удалось сохранить код возврата")
                        }
                }
                .addOnFailureListener { e ->
                    onResult(false, "", e.message ?: "Не удалось проверить код возврата")
                }
        }
    }

    /**
     * Проверяет 6-значный код, введённый нашедшим при передаче вещи владельцу.
     * При совпадении переводит объявление в статус «Возвращено по коду» и фиксирует участника возврата.
     */
    fun confirmReturnWithCode(
        ad: AdItem,
        enteredCode: String,
        finderProfileName: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val cleanCode = enteredCode.filter { it.isDigit() }
        if (cleanCode.length != 6) {
            onResult(false, "Введите 6 цифр проверочного кода")
            return
        }
        if (ad.id.isBlank()) {
            onResult(false, "Некорректный ID объявления")
            return
        }

        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "Не удалось войти в Firebase")
                return@ensure
            }
            val currentUid = AuthGate.userId()
            val codeRef = database.getReference(NODE_CODES).child(ad.id)
            codeRef.get()
                .addOnSuccessListener { snapshot ->
                    val record = snapshot.getValue(ReturnCodeRecord::class.java)
                    when {
                        record == null || record.code.isBlank() -> {
                            onResult(false, "Владелец ещё не сформировал проверочный код для этой заявки")
                        }
                        record.used -> {
                            onResult(false, "Этот проверочный код уже был использован")
                        }
                        record.code != cleanCode -> {
                            onResult(false, "Неверный проверочный код. Проверьте 6 цифр у владельца вещи")
                        }
                        record.ownerId == currentUid -> {
                            onResult(
                                false,
                                "Код должен ввести второй участник встречи (нашедший вещь) со своего устройства"
                            )
                        }
                        else -> {
                            val now = System.currentTimeMillis()
                            // Нашедший — тот, кто вводит код (для НАХОДКИ это автор объявления, для ПОТЕРИ — откликнувшийся).
                            val finderId = if (ad.type == AdType.FOUND) ad.ownerId.ifBlank { currentUid } else currentUid
                            val finderName = if (ad.type == AdType.FOUND) {
                                ad.ownerName.ifBlank { finderProfileName.trim() }
                            } else {
                                finderProfileName.trim()
                            }
                            val receiverId = record.ownerId

                            val updates = hashMapOf<String, Any?>(
                                "${NODE_CODES}/${ad.id}/used" to true,
                                "${NODE_CODES}/${ad.id}/usedBy" to currentUid,
                                "${NODE_CODES}/${ad.id}/usedByName" to finderName,
                                "${NODE_CODES}/${ad.id}/usedAt" to now,
                                "${ad.type.node}/${ad.id}/closed" to true,
                                "${ad.type.node}/${ad.id}/verifiedReturn" to true,
                                "${ad.type.node}/${ad.id}/finderId" to finderId,
                                "${ad.type.node}/${ad.id}/finderName" to finderName,
                                "${ad.type.node}/${ad.id}/receiverId" to receiverId,
                                "${ad.type.node}/${ad.id}/returnedAt" to now
                            )
                            database.reference.updateChildren(updates)
                                .addOnSuccessListener {
                                    onResult(true, "Возврат подтверждён! Статус обновлён, рейтинг начислен.")
                                }
                                .addOnFailureListener { e ->
                                    onResult(false, e.message ?: "Ошибка записи подтверждения")
                                }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, e.message ?: "Не удалось проверить код в базе")
                }
        }
    }

    /** Отправляет благодарность и оценку (1..5 ★) пользователю, вернувшему вещь. */
    fun sendThanks(
        ad: AdItem,
        toUserId: String,
        toName: String,
        fromName: String,
        stars: Int,
        badge: String,
        comment: String,
        verifiedByCode: Boolean,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (toUserId.isBlank()) {
            onResult(false, "Не удалось определить получателя благодарности")
            return
        }
        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "Не удалось войти в Firebase")
                return@ensure
            }
            val fromUid = AuthGate.userId()
            if (fromUid.isBlank()) {
                onResult(false, "Нет идентификатора пользователя")
                return@ensure
            }
            if (fromUid == toUserId) {
                onResult(false, "Нельзя отправить благодарность самому себе")
                return@ensure
            }

            val thankKey = "${ad.id}_$fromUid".replace(".", "_")
            val record = ThanksRecord(
                id = thankKey,
                adId = ad.id,
                adTitle = ad.title,
                fromUserId = fromUid,
                fromName = fromName.trim().ifBlank { "Участник школы" },
                toUserId = toUserId,
                toName = toName.trim(),
                stars = stars.coerceIn(1, 5),
                badge = badge.trim(),
                comment = comment.trim().take(240),
                verifiedByCode = verifiedByCode,
                createdAt = System.currentTimeMillis()
            )
            database.getReference(NODE_THANKS)
                .child(toUserId)
                .child(thankKey)
                .setValue(record)
                .addOnSuccessListener {
                    onResult(true, "Спасибо! Благодарность добавлена в рейтинг нашедшего")
                }
                .addOnFailureListener { e ->
                    onResult(false, e.message ?: "Не удалось отправить благодарность")
                }
        }
    }

    /** Загружает рейтинг надёжности и отзывы конкретного пользователя. */
    fun loadUserTrust(userId: String, onResult: (UserTrustSummary) -> Unit) {
        if (userId.isBlank()) {
            onResult(UserTrustSummary(userId = ""))
            return
        }
        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(UserTrustSummary(userId = userId))
                return@ensure
            }
            database.reference.get()
                .addOnSuccessListener { root ->
                    onResult(buildSummaryForUser(root, userId))
                }
                .addOnFailureListener {
                    onResult(UserTrustSummary(userId = userId))
                }
        }
    }

    /** Подписка на живой рейтинг всех активных помощников (для Доски почёта и Личного кабинета). */
    fun observeLeaderboard(onUpdate: (List<UserTrustSummary>) -> Unit): () -> Unit {
        val ref: DatabaseReference = database.reference
        var activeListener: ValueEventListener? = null

        AuthGate.ensure { authorized ->
            if (!authorized) {
                onUpdate(emptyList())
                return@ensure
            }
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(buildLeaderboard(snapshot))
                }

                override fun onCancelled(error: DatabaseError) {
                    onUpdate(emptyList())
                }
            }
            activeListener = listener
            ref.addValueEventListener(listener)
        }

        return {
            activeListener?.let { ref.removeEventListener(it) }
        }
    }

    private fun buildSummaryForUser(root: DataSnapshot, userId: String): UserTrustSummary {
        val userName = root.child(FirebaseRepo.NODE_USERS).child(userId).child("name")
            .getValue(String::class.java).orEmpty()

        var verifiedCount = 0
        var closedFinds = 0

        for (nodeName in listOf(AdType.FOUND.node, AdType.LOST.node)) {
            for (adSnap in root.child(nodeName).children) {
                val closed = adSnap.child("closed").getValue(Boolean::class.java) ?: false
                val verified = adSnap.child("verifiedReturn").getValue(Boolean::class.java) ?: false
                val finderId = adSnap.child("finderId").getValue(String::class.java).orEmpty()
                val authorId = adSnap.child("userId").getValue(String::class.java).orEmpty()

                if (verified && finderId == userId) {
                    verifiedCount++
                } else if (nodeName == AdType.FOUND.node && closed && authorId == userId) {
                    closedFinds++
                }
            }
        }

        val thanksList = mutableListOf<ThanksRecord>()
        for (item in root.child(NODE_THANKS).child(userId).children) {
            val rec = item.getValue(ThanksRecord::class.java) ?: continue
            thanksList.add(rec)
        }
        thanksList.sortByDescending { it.createdAt }

        val avgStars = if (thanksList.isNotEmpty()) {
            thanksList.map { it.stars.coerceIn(1, 5) }.average()
        } else {
            0.0
        }

        return UserTrustSummary(
            userId = userId,
            userName = userName,
            verifiedReturns = verifiedCount,
            closedFinds = closedFinds,
            thanksCount = thanksList.size,
            averageStars = avgStars,
            recentThanks = thanksList
        )
    }

    private fun buildLeaderboard(root: DataSnapshot): List<UserTrustSummary> {
        val candidateIds = linkedSetOf<String>()
        for (u in root.child(FirebaseRepo.NODE_USERS).children) {
            u.key?.let { candidateIds.add(it) }
        }
        for (t in root.child(NODE_THANKS).children) {
            t.key?.let { candidateIds.add(it) }
        }
        for (nodeName in listOf(AdType.FOUND.node, AdType.LOST.node)) {
            for (adSnap in root.child(nodeName).children) {
                adSnap.child("finderId").getValue(String::class.java)?.takeIf { it.isNotBlank() }?.let {
                    candidateIds.add(it)
                }
                adSnap.child("userId").getValue(String::class.java)?.takeIf { it.isNotBlank() }?.let {
                    candidateIds.add(it)
                }
            }
        }

        return candidateIds
            .map { uid -> buildSummaryForUser(root, uid) }
            .filter { it.verifiedReturns > 0 || it.closedFinds > 0 || it.thanksCount > 0 }
            .sortedWith(
                compareByDescending<UserTrustSummary> { it.trustPoints }
                    .thenByDescending { it.verifiedReturns }
                    .thenByDescending { it.thanksCount }
            )
    }

    companion object {
        const val NODE_CODES = "returnCodes"
        const val NODE_THANKS = "thanks"

        fun formatCode(code: String): String {
            val clean = code.filter { it.isDigit() }
            return if (clean.length == 6) "${clean.substring(0, 3)} ${clean.substring(3, 6)}" else code
        }
    }
}
