package dev.atick.shorts.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Etat du verrou, tel qu'il est enregistre.
 *
 * - [unlockStartsAt] / [unlockEndsAt] : fenetre pendant laquelle les reglages sont ouverts.
 *   Le bon code ne deverrouille pas tout de suite : il lance une attente de [WAIT_MS].
 * - [blockedUntil] : apres trop d'erreurs, plus aucune saisie avant cette date.
 * - [guardOpenUntil] : le bon code ouvre tout de suite, jusqu'a cette date, les protections
 *   hors d'Instagram (Parametres, desinstallation). Le blocage des Reels, lui,
 *   garde l'attente de [LockManager.WAIT_MS].
 */
data class LockState(
    val hasCode: Boolean = false,
    val unlockStartsAt: Long = 0L,
    val unlockEndsAt: Long = 0L,
    val failedAttempts: Int = 0,
    val blockedUntil: Long = 0L,
    val guardOpenUntil: Long = 0L,
) {
    /** Vrai quand les reglages sont proteges (code defini et hors fenetre de deverrouillage). */
    fun isLocked(now: Long = System.currentTimeMillis()): Boolean =
        hasCode && now !in unlockStartsAt until unlockEndsAt

    /** Vrai quand les pages des Parametres qui montrent EndScroll doivent etre refermees. */
    fun isGuardLocked(now: Long = System.currentTimeMillis()): Boolean =
        isLocked(now) && now >= guardOpenUntil

    fun isWaitingForUnlock(now: Long = System.currentTimeMillis()): Boolean =
        hasCode && now < unlockStartsAt
}

sealed interface UnlockResult {
    /** Code bon : le deverrouillage commencera a [startsAt]. */
    data class Accepted(val startsAt: Long) : UnlockResult
    data class Rejected(val blockedUntil: Long) : UnlockResult
    data class TooSoon(val blockedUntil: Long) : UnlockResult
}

/**
 * Code de verrouillage : haché en PBKDF2 avec un sel aleatoire, jamais stocke en clair.
 * Le code saisi est efface de la memoire des qu'il a ete verifie.
 */
class LockManager(private val context: Context) {

    private val hashKey = stringPreferencesKey("lock.hash")
    private val saltKey = stringPreferencesKey("lock.salt")
    private val unlockStartsKey = longPreferencesKey("lock.unlock_starts")
    private val unlockEndsKey = longPreferencesKey("lock.unlock_ends")
    private val failedKey = intPreferencesKey("lock.failed")
    private val blockedUntilKey = longPreferencesKey("lock.blocked_until")
    private val guardOpenUntilKey = longPreferencesKey("lock.guard_open_until")

    val state: Flow<LockState> = context.dataStore.data.map { p ->
        LockState(
            hasCode = p[hashKey] != null,
            unlockStartsAt = p[unlockStartsKey] ?: 0L,
            unlockEndsAt = p[unlockEndsKey] ?: 0L,
            failedAttempts = p[failedKey] ?: 0,
            blockedUntil = p[blockedUntilKey] ?: 0L,
            guardOpenUntil = p[guardOpenUntilKey] ?: 0L,
        )
    }

    /** Definit (ou remplace) le code. A n'appeler que si le verrou est ouvert. */
    suspend fun setCode(code: CharArray) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(code, salt)
        code.fill('\u0000')
        context.dataStore.edit { p ->
            p[hashKey] = b64(hash)
            p[saltKey] = b64(salt)
            p[failedKey] = 0
            p[blockedUntilKey] = 0L
            // Le verrou s'applique tout de suite
            p[unlockStartsKey] = 0L
            p[unlockEndsKey] = 0L
            p[guardOpenUntilKey] = 0L
        }
        hash.fill(0)
    }

    /** Supprime le code. A n'appeler que si le verrou est ouvert. */
    suspend fun removeCode() {
        context.dataStore.edit { p ->
            p.remove(hashKey)
            p.remove(saltKey)
            p[failedKey] = 0
            p[blockedUntilKey] = 0L
            p[unlockStartsKey] = 0L
            p[unlockEndsKey] = 0L
            p[guardOpenUntilKey] = 0L
        }
    }

    /** Referme le verrou avant la fin de la fenetre. */
    suspend fun relock() {
        context.dataStore.edit { p ->
            p[unlockStartsKey] = 0L
            p[unlockEndsKey] = 0L
            p[guardOpenUntilKey] = 0L
        }
    }

    suspend fun tryUnlock(code: CharArray): UnlockResult {
        val now = System.currentTimeMillis()
        val current = state.first()
        if (now < current.blockedUntil) {
            code.fill('\u0000')
            return UnlockResult.TooSoon(current.blockedUntil)
        }

        val prefs = context.dataStore.data.first()
        val storedHash = prefs[hashKey]?.let(::unb64)
        val salt = prefs[saltKey]?.let(::unb64)
        val candidate = if (salt != null) derive(code, salt) else ByteArray(0)
        code.fill('\u0000')
        val ok = storedHash != null && MessageDigest.isEqual(storedHash, candidate)
        candidate.fill(0)

        return if (ok) {
            val startsAt = now + WAIT_MS
            context.dataStore.edit { p ->
                p[failedKey] = 0
                p[blockedUntilKey] = 0L
                p[unlockStartsKey] = startsAt
                p[unlockEndsKey] = startsAt + OPEN_MS
                p[guardOpenUntilKey] = now + GUARD_OPEN_MS
            }
            UnlockResult.Accepted(startsAt)
        } else {
            val failed = current.failedAttempts + 1
            val blockedUntil = now + penaltyFor(failed)
            context.dataStore.edit { p ->
                p[failedKey] = failed
                p[blockedUntilKey] = blockedUntil
            }
            UnlockResult.Rejected(blockedUntil)
        }
    }

    private fun derive(code: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = Base64.getDecoder().decode(text)

    companion object {
        const val MIN_CODE_LENGTH = 12

        /** Attente entre le bon code et l'ouverture des reglages. */
        const val WAIT_MS = 15 * 60_000L

        /** Duree pendant laquelle les reglages restent ouverts. */
        const val OPEN_MS = 5 * 60_000L

        /** Duree pendant laquelle les Parametres sont libres, des le bon code. */
        const val GUARD_OPEN_MS = 15 * 60_000L

        private const val ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val FREE_ATTEMPTS = 3
        private const val MAX_PENALTY_MS = 24 * 60 * 60_000L

        /** 3 essais libres, puis 1 min, 2, 4, 8... jusqu'a 24 h. */
        fun penaltyFor(failedAttempts: Int): Long {
            if (failedAttempts < FREE_ATTEMPTS) return 0L
            val exponent = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(20)
            return (60_000L shl exponent).coerceAtMost(MAX_PENALTY_MS)
        }

        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

        /** Code aleatoire de 20 caracteres, a confier a quelqu'un d'autre. */
        fun randomCode(length: Int = 20): CharArray {
            val rng = SecureRandom()
            return CharArray(length) { CODE_ALPHABET[rng.nextInt(CODE_ALPHABET.length)] }
        }
    }
}
