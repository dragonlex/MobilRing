package com.example.MobilRing24KRSK.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Заявка на описание вещи — то, что приложение кладёт в узел `assist`.
 *
 * Сервис подсказок (`assist.py` на компьютере) держит подписку на этот узел:
 * видит запись со статусом [STATUS_PENDING], генерирует текст и дописывает
 * его в ту же запись вместе со статусом [STATUS_DONE].
 */
data class AssistRequest(
    var id: String = "",
    var uid: String = "",
    var type: String = "",
    var title: String = "",
    var place: String = "",
    var category: String = "",
    var status: String = AssistRepo.STATUS_PENDING,
    var createdAt: Long = 0L
)

/**
 * Подсказка описания через базу, без прямого HTTP-запроса.
 *
 * Схема обмена:
 *  1. [ask] кладёт заявку `assist/{key}` со status=pending и сразу подписывается на неё.
 *  2. Сервис замечает заявку, ставит status=running, генерирует текст и пишет
 *     `text` + status=done (или status=error с текстом ошибки).
 *  3. Приложение получает ответ, показывает «ОК», удаляет заявку и отписывается.
 *
 * Возвращённая функция — отмена: снимает слушателя и убирает заявку из базы.
 */
class AssistRepo(private val database: FirebaseDatabase = FirebaseDatabase.getInstance()) {

    fun ask(
        type: AdType,
        title: String,
        place: String,
        category: String,
        onResult: (success: Boolean, message: String) -> Unit
    ): () -> Unit {
        var cancelled = false
        var ref: DatabaseReference? = null
        var listener: ValueEventListener? = null
        lateinit var cancel: () -> Unit

        cancel = {
            cancelled = true
            val currentRef = ref
            val currentListener = listener
            if (currentRef != null && currentListener != null) {
                currentRef.removeEventListener(currentListener)
            }
            currentRef?.removeValue()
        }

        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "Не удалось войти в Firebase")
                return@ensure
            }
            if (cancelled) return@ensure

            val newRef = database.getReference(NODE).push()
            val key = newRef.key
            if (key == null) {
                onResult(false, "Firebase не вернул ключ заявки")
                return@ensure
            }
            ref = newRef

            val valueListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child(FIELD_STATUS).getValue(String::class.java).orEmpty()
                    when (status) {
                        STATUS_DONE -> {
                            val text = snapshot.child(FIELD_TEXT)
                                .getValue(String::class.java).orEmpty().trim()
                            cancel()
                            if (text.isBlank()) {
                                onResult(false, "Сервис вернул пустую подсказку")
                            } else {
                                onResult(true, limitWords(text))
                            }
                        }

                        STATUS_ERROR -> {
                            val error = snapshot.child(FIELD_ERROR)
                                .getValue(String::class.java).orEmpty().trim()
                            cancel()
                            onResult(false, error.ifBlank { "Не удалось составить описание" })
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    cancel()
                    onResult(false, error.message)
                }
            }
            listener = valueListener
            newRef.addValueEventListener(valueListener)

            val record = AssistRequest(
                id = key,
                uid = AuthGate.userId(),
                type = type.node,
                title = title.trim(),
                place = place.trim(),
                category = category.trim(),
                status = STATUS_PENDING,
                createdAt = System.currentTimeMillis()
            )
            newRef.setValue(record).addOnFailureListener { error ->
                cancel()
                onResult(false, error.message ?: "Не удалось отправить заявку")
            }
        }

        return cancel
    }

    companion object {
        const val NODE = "assist"
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"

        /** Сколько слов максимум показываем — столько же ограничивает сервис. */
        const val MAX_WORDS = 110

        private const val FIELD_STATUS = "status"
        private const val FIELD_TEXT = "text"
        private const val FIELD_ERROR = "error"

        /** Страховка на случай, если сервис пришлёт текст длиннее лимита. */
        fun limitWords(text: String, limit: Int = MAX_WORDS): String {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size <= limit) return text.trim()
            return words.take(limit).joinToString(" ").trimEnd(',', ';', ':', '—') + "…"
        }
    }
}
