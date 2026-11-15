package com.example.MobilRing24KRSK.data

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlin.random.Random

/**
 * Ключ восстановления аккаунта.
 *
 * Сейчас вход в приложение анонимный: Firebase выдаёт uid на конкретном телефоне,
 * и с другого устройства этот же аккаунт открыть нельзя. Чтобы объявления
 * и переписка не пропадали вместе с телефоном, анонимный аккаунт один раз
 * привязывается к паре «почта + пароль», построенной из ключа:
 *
 *   ключ K4RT-9M2P-… → почта `k4rt9m2p…@mobilring.app`, пароль — сам ключ (16 символов).
 *
 * Почта никуда не отправляется и существует только как логин Firebase — письма
 * на неё не приходят. На новом телефоне человек вводит ключ, приложение выполняет
 * signInWithEmailAndPassword и возвращается в тот же uid со всеми объявлениями.
 *
 * 16 символов из 32-буквенного алфавита — это 80 бит; подобрать ключ перебором нельзя.
 * Из алфавита убраны похожие знаки (0/O, 1/I/L), чтобы ключ было легко продиктовать.
 */
object RecoveryKey {

    private const val PREFS_NAME = "recovery"
    private const val KEY_STORED = "key"

    const val LENGTH = 16
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val DOMAIN = "mobilring.app"

    /** Создаёт новый случайный ключ без дефисов. */
    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }

    /** Оставляет от введённой строки только символы алфавита и переводит в верхний регистр. */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }

    fun isValid(raw: String): Boolean = normalize(raw).length == LENGTH

    /** Ключ для показа: группами по четыре символа — K4RT-9M2P-7XQV-3BND. */
    fun format(raw: String): String = normalize(raw).chunked(4).joinToString("-")

    /** Логин в Firebase, построенный из ключа. */
    fun email(raw: String): String = normalize(raw).lowercase() + "@" + DOMAIN

    fun stored(context: Context): String =
        prefs(context).getString(KEY_STORED, "").orEmpty()

    fun save(context: Context, key: String) {
        prefs(context).edit().putString(KEY_STORED, normalize(key)).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().remove(KEY_STORED).apply()
    }

    /**
     * Привязывает текущий анонимный аккаунт к ключу. Вызывается один раз,
     * из профиля, кнопкой «СОЗДАТЬ КЛЮЧ».
     */
    fun create(context: Context, onResult: (success: Boolean, message: String) -> Unit) {
        AuthGate.ensure { authorized ->
            if (!authorized) {
                onResult(false, "Не удалось войти в Firebase")
                return@ensure
            }
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                onResult(false, "Нет текущего пользователя")
                return@ensure
            }
            val key = generate()
            val credential = EmailAuthProvider.getCredential(email(key), key)
            user.linkWithCredential(credential)
                .addOnSuccessListener {
                    save(context, key)
                    onResult(true, format(key))
                }
                .addOnFailureListener { error ->
                    onResult(false, describe(error))
                }
        }
    }

    /**
     * Вход по ключу на другом телефоне: текущая (анонимная) сессия заменяется
     * аккаунтом, к которому привязан ключ.
     */
    fun signIn(raw: String, onResult: (success: Boolean, message: String) -> Unit) {
        if (!isValid(raw)) {
            onResult(false, "Ключ состоит из 16 символов")
            return
        }
        val key = normalize(raw)
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email(key), key)
            .addOnSuccessListener { onResult(true, key) }
            .addOnFailureListener { error -> onResult(false, describe(error)) }
    }

    /** Тексты ошибок Firebase — по-русски и по делу. */
    private fun describe(error: Exception): String {
        val code = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty()
        val raw = error.message.orEmpty()
        return when {
            code.contains("operation-not-allowed") || raw.contains("OPERATION_NOT_ALLOWED") ->
                "В консоли Firebase не включён вход «Электронная почта/пароль»"
            code.contains("email-already-in-use") || raw.contains("EMAIL_ALREADY_IN_USE") ->
                "Ключ уже занят, нажмите ещё раз"
            code.contains("wrong-password") || code.contains("invalid-credential") ||
                    raw.contains("INVALID_LOGIN_CREDENTIALS") || raw.contains("WEAK_PASSWORD") ->
                "Ключ не подошёл"
            code.contains("network") || raw.contains("network", ignoreCase = true) ->
                "Нет связи с Firebase, проверьте интернет"
            else -> raw.ifBlank { "Неизвестная ошибка Firebase" }
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
