package coop.macambira.leitedia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CachedCredentials(val loginId: String, val password: String)
data class CachedSession(val profile: UserProfile, val license: LicenseStatus)

class OfflineSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("leitedia_secure_session", Context.MODE_PRIVATE)
    private val alias = "leitedia_offline_session"

    fun save(
        profile: UserProfile,
        license: LicenseStatus,
        loginId: String,
        password: String,
        keepLoggedIn: Boolean? = null,
    ) {
        val encrypted = encrypt(password)
        val autoLogin = keepLoggedIn ?: shouldAutoLogin()
        prefs.edit()
            .putString("login_id", loginId.trim().uppercase())
            .putString("password", encrypted.first)
            .putString("iv", encrypted.second)
            .putString("id", profile.id)
            .putString("cooperative_id", profile.cooperativeId)
            .putString("full_name", profile.fullName)
            .putString("role", profile.role)
            .putBoolean("active", profile.active)
            .putString("license_status", license.status)
            .putString("trial_ends_at", license.trialEndsAt)
            .putInt("days_remaining", license.daysRemaining)
            .putBoolean("license_active", license.active)
            .putLong("cached_at", System.currentTimeMillis())
            .putBoolean("auto_login", autoLogin)
            .apply()
    }

    fun restore(loginId: String, password: String): CachedSession? {
        val credentials = credentials() ?: return null
        if (!credentials.loginId.equals(loginId.trim(), ignoreCase = true) || credentials.password != password) return null
        val status = prefs.getString("license_status", "trial") ?: "trial"
        val trialEnd = prefs.getString("trial_ends_at", "").orEmpty()
        val licenseActive = AppRules.isOfflineSessionValid(prefs.getLong("cached_at", 0), status, trialEnd, prefs.getBoolean("license_active", false), System.currentTimeMillis())
        if (!licenseActive || !prefs.getBoolean("active", false)) return null
        return CachedSession(
            UserProfile(
                id = prefs.getString("id", null) ?: return null,
                cooperativeId = prefs.getString("cooperative_id", null) ?: return null,
                loginId = prefs.getString("login_id", loginId) ?: loginId,
                fullName = prefs.getString("full_name", null) ?: return null,
                role = prefs.getString("role", "user") ?: "user",
                active = true
            ),
            LicenseStatus(status, trialEnd, prefs.getInt("days_remaining", 0), true)
        )
    }

    fun credentials(): CachedCredentials? {
        val login = prefs.getString("login_id", null) ?: return null
        val encrypted = prefs.getString("password", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        return runCatching { CachedCredentials(login, decrypt(encrypted, iv)) }.getOrNull()
    }

    fun credentialsForAutoLogin(): CachedCredentials? =
        if (shouldAutoLogin()) credentials() else null

    fun shouldAutoLogin(): Boolean = prefs.getBoolean("auto_login", false)

    fun clear() { prefs.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(value: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)))
    }
}
