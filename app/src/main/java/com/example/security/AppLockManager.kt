package com.example.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class AppLockManager(private val context: Context) {

    private val KEY_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val KEY_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
    private val KEY_USE_BIOMETRIC = booleanPreferencesKey("app_lock_use_biometric")

    val isLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_ENABLED] ?: false }
    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_USE_BIOMETRIC] ?: false }
    val hasPinSet: Flow<Boolean> = context.dataStore.data.map { !it[KEY_PIN_HASH].isNullOrEmpty() }

    suspend fun setPin(pin: String) {
        val hash = hashPin(pin)
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
        }
    }

    suspend fun verifyPin(input: String): Boolean {
        val inputHash = hashPin(input)
        var matched = false
        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_PIN_HASH]
            matched = (stored != null && stored == inputHash)
        }
        return matched
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_BIOMETRIC] = enabled
        }
    }

    fun promptBiometric(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val biometricPrompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                .setTitle("SFR Host Security")
                .setSubtitle("Autentikasi Sidik Jari untuk Mengakses Server")
                .setDescription("Gunakan sensor biometrik atau PIN untuk membuka aplikasi.")
                .setNegativeButton("Batal / Keluar", activity.mainExecutor) { _, _ ->
                    onCancel()
                }
                .build()

            val cancellationSignal = CancellationSignal()
            cancellationSignal.setOnCancelListener {
                onCancel()
            }

            biometricPrompt.authenticate(
                cancellationSignal,
                activity.mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        // Standard Android Biometric error codes:
                        // 5: BIOMETRIC_ERROR_CANCELED, 10: BIOMETRIC_ERROR_USER_CANCELED, 13: BIOMETRIC_ERROR_NEGATIVE_BUTTON
                        if (errorCode == 5 || errorCode == 10 || errorCode == 13) {
                            onCancel()
                        } else {
                            onFailed(errString?.toString() ?: "Autentikasi biometrik dibatalkan.")
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailed("Sidik jari tidak cocok. Silakan gunakan PIN atau coba lagi.")
                    }
                }
            )
        } else {
            onFailed("Perangkat tidak mendukung biometrik.")
        }
    }

    private fun hashPin(pin: String): String {
        val salt = "SFR_HOST_SALT_2026"
        val bytes = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
