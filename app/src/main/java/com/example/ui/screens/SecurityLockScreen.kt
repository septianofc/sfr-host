package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AppLockManager
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.StatusOfflineRed

/**
 * SecurityLockScreen invokes ONLY native Android authentication (Fingerprint, Device PIN, Pattern, or Password).
 * No custom app PINs or keyboard. If user cancels or presses back, the app exits cleanly via finishAffinity().
 */
@Composable
fun SecurityLockScreen(
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDeviceSecure by remember { mutableStateOf(appLockManager.isDeviceSecure()) }
    var hasPromptedOnce by remember { mutableStateOf(false) }

    // Intercept Back button: always cleanly exit the app
    BackHandler {
        activity?.finishAffinity()
    }

    fun requestAuthentication() {
        if (activity == null) return
        if (!appLockManager.isDeviceSecure()) {
            isDeviceSecure = false
            errorMessage = "Perangkat HP Anda belum memiliki Kunci Layar (PIN, Pola, atau Sandi). Silakan aktifkan pengaman layar di Pengaturan HP untuk menggunakan proteksi aplikasi."
            return
        }

        appLockManager.authenticateWithDevice(
            activity = activity,
            onSuccess = {
                onUnlocked()
            },
            onCancel = {
                // When canceled by user or back pressed, exit cleanly without looping
                activity.finishAffinity()
            },
            onError = { err ->
                errorMessage = err
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!hasPromptedOnce) {
            hasPromptedOnce = true
            requestAuthentication()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SfrPrimaryBlue.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SfrPrimaryBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SFR Host Dilindungi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Gunakan Sidik Jari, PIN, Pola, atau Sandi bawaan perangkat Anda untuk membuka kunci.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!isDeviceSecure) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pengaman Layar HP Belum Aktif",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HP Anda saat ini tidak memiliki PIN, Pola, atau Sandi layar. Aktifkan kunci layar di pengaturan sistem.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Pengaturan Keamanan HP")
                        }
                    }
                }
            } else {
                errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = err,
                            color = StatusOfflineRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Authenticate Button
                Button(
                    onClick = { requestAuthentication() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .testTag("unlock_biometric_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Kunci Layar", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel / Exit Button
            OutlinedButton(
                onClick = {
                    activity?.finishAffinity()
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .testTag("cancel_exit_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Batal & Keluar")
            }
        }
    }
}
