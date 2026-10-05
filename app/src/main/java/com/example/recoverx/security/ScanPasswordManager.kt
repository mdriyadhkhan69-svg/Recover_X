package com.example.recoverx.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stored value = HMAC-SHA256( AndroidKeystoreKey, PBKDF2-HMAC-SHA256(password, randomSalt) ).
 * No plaintext, no reset path, no default password. The Keystore key is non-extractable, so
 * the stored tag is useless off-device. Repeated failures cause an escalating lockout.
 */
class ScanPasswordManager private constructor(context: Context) {

    sealed class VerifyResult {
        object Ok : VerifyResult()
        object Wrong : VerifyResult()
        data class LockedOut(val seconds: Long) : VerifyResult()
    }

    private val prefs = context.applicationContext.getSharedPreferences("scan_security", Context.MODE_PRIVATE)

    @Synchronized
    fun hasPassword(): Boolean {
        val present = prefs.contains(KEY_TAG) && prefs.contains(KEY_SALT)
        if (present && !keyExists()) { // Keystore key lost (e.g. restored backup): unverifiable
            prefs.edit().clear().apply()
            return false
        }
        return present
    }

    @Synchronized
    fun setInitialPassword(newPassword: String): Boolean {
        if (hasPassword()) return false
        store(newPassword)
        return true
    }

    /** Current password is verified first; nothing changes unless it is correct. */
    @Synchronized
    fun changePassword(current: String, newPassword: String): VerifyResult {
        val r = verify(current)
        if (r is VerifyResult.Ok) store(newPassword)
        return r
    }

    @Synchronized
    fun verify(password: String): VerifyResult {
        if (!hasPassword()) return VerifyResult.Ok
        val now = System.currentTimeMillis()
        val lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        if (now < lockUntil) return VerifyResult.LockedOut((lockUntil - now + 999) / 1000)

        val matches = try {
            val salt = Base64.decode(prefs.getString(KEY_SALT, "")!!, Base64.NO_WRAP)
            val expected = Base64.decode(prefs.getString(KEY_TAG, "")!!, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derive(password, salt))
        } catch (e: Exception) { false }

        if (matches) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
            return VerifyResult.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val lock = if (fails >= 5) now + minOf(30_000L shl minOf(fails - 5, 7), 3_600_000L) else 0L
        prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCK_UNTIL, lock).apply()
        return VerifyResult.Wrong
    }

    private fun store(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val tag = derive(password, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_TAG, Base64.encodeToString(tag, Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val dk = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keystoreKey())
        return mac.doFinal(dk)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun keyExists(): Boolean = try { keyStore().containsAlias(ALIAS) } catch (e: Exception) { false }

    private fun keystoreKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return kg.generateKey()
    }

    companion object {
        private const val ALIAS = "recoverx_scan_pw_key"
        private const val KEY_SALT = "salt"
        private const val KEY_TAG = "tag"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"

        @Volatile private var INSTANCE: ScanPasswordManager? = null
        fun get(context: Context): ScanPasswordManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: ScanPasswordManager(context).also { INSTANCE = it } }
    }
}