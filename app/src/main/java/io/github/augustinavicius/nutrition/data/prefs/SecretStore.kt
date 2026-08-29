package io.github.augustinavicius.nutrition.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores small secrets — currently just the GitHub token used to reach private releases —
 * encrypted with an AES key held in the Android Keystore, so the token is not readable from
 * a filesystem backup or an adb pull of the app's shared preferences.
 *
 * Failures are treated as "no secret" rather than crashes: a Keystore key can legitimately
 * disappear (device restore, lock-screen change on some OEMs), and the right recovery is to
 * ask the user to sign in again.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            require(blob.size > IV_LENGTH) { "ciphertext too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH))
            }
            String(cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt '$name'; clearing it", e)
            remove(name)
            null
        }
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            remove(name)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val blob = cipher.iv + encrypted
            prefs.edit { putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not encrypt '$name'", e)
        }
    }

    fun remove(name: String) = prefs.edit { remove(name) }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val GITHUB_TOKEN = "github_token"

        private const val TAG = "SecretStore"
        private const val PREFS_NAME = "nutrition_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nutrition_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
