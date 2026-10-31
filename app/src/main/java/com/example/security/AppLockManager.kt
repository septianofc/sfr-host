package com.example.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * AppLockManager uses ONLY native Android device credentials and biometrics:
 * - Device Lockscreen PIN
 * - Device Lockscreen Pattern
 * - Device Lockscreen Password
 * - Fingerprint / Face Biometric
 * No custom app-specific PINs, no app-specific PIN keyboards.
 */
class AppLockManager(private val context: Context) {

    private val KEY_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")

    val isLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK_ENABLED] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = enabled
        }
    }

    fun isDeviceSecure(): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure ?: false
    }

    fun authenticateWithDevice(
        activity: Activity,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
        onError: (String) -> Unit
    ) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        if (km == null || !km.isDeviceSecure) {
            onError("Perangkat Anda belum memiliki proteksi layar (PIN/Pola/Sandi). Silakan aktifkan kunci layar di Pengaturan Android Anda terlebih dahulu.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val promptBuilder = BiometricPrompt.Builder(activity)
                    .setTitle("SFR Host")
                    .setSubtitle("Autentikasi Keamanan Perangkat")
                    .setDescription("Gunakan sidik jari, PIN, pola, atau kata sandi HP Anda untuk melanjutkan.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    promptBuilder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    promptBuilder.setDeviceCredentialAllowed(true)
                }

                val prompt = promptBuilder.build()
                val cancellationSignal = CancellationSignal()
                cancellationSignal.setOnCancelListener {
                    onCancel()
                }

                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            // 5: BIOMETRIC_ERROR_CANCELED, 10: USER_CANCELED, 13: NEGATIVE_BUTTON
                            if (errorCode == 5 || errorCode == 10 || errorCode == 13) {
                                onCancel()
                            } else {
                                onError(errString?.toString() ?: "Autentikasi dibatalkan.")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // System allows multiple attempts before failing
                        }
                    }
                )
                return
            } catch (ignored: Exception) {
                // Fallback to KeyguardManager intent
            }
        }

        // Universal Fallback for API 26-28 or when BiometricPrompt throws
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent(
            "SFR Host",
            "Gunakan PIN, Pola, atau Sandi bawaan HP untuk membuka aplikasi."
        )
        if (intent != null) {
            activity.startActivityForResult(intent, REQUEST_CODE_CONFIRM_CREDENTIAL)
        } else {
            onError("Tidak dapat menampilkan dialog autentikasi perangkat.")
        }
    }

    companion object {
        const val REQUEST_CODE_CONFIRM_CREDENTIAL = 9981
    }
}
