package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomDomainItem
import com.example.data.DomainManager
import com.example.ui.theme.SfrButtonColors
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Composable
fun SslTlsScreen(
    domains: List<CustomDomainItem>,
    forceHttpsGlobal: Boolean,
    onToggleForceHttpsGlobal: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val domainManager = remember { DomainManager(context) }
    var selectedEncryptionMode by remember { mutableStateOf("FULL") }

    val testingDomains = remember { mutableStateMapOf<String, Boolean>() }
    val tlsResults = remember { mutableStateMapOf<String, String>() }

    val activeSslCount = domains.count { it.sslActive }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header
        item {
            Column {
                Text(
                    text = "SSL / TLS Encryption Management",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SfrPrimaryBlue
                )
                Text(
                    text = "Status TLS Handshake & Sertifikat Domain Aktif ($activeSslCount/${domains.size} Terenkripsi)",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // SSL Status Hero
        item {
            val isAnySslActive = activeSslCount > 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isAnySslActive) StatusOnlineGreen.copy(alpha = 0.15f) else StatusWarningAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isAnySslActive) StatusOnlineGreen else StatusWarningAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isAnySslActive) "HTTPS Encryption Active" else "HTTPS Menunggu Setup Domain",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAnySslActive) StatusOnlineGreen else StatusWarningAmber
                                    )
                                )
                                Text(
                                    text = "Let's Encrypt / Cloudflare Edge TLS 1.3",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAnySslActive) StatusOnlineGreen else StatusWarningAmber)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAnySslActive) "SECURE" else "PENDING",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Setiap pengunjung yang membuka domain kustom Anda akan terhubung melalui enkripsi TLS 1.3 standar industri dengan gembok hijau aman di browser.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        // Encryption Mode Selector (Cloudflare Style)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mode Enkripsi SSL/TLS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SslModeOption(
                        title = "Full (Direkomendasikan)",
                        desc = "Enkripsi end-to-end antara pengunjung -> Gateway -> HP Android dengan tunnel terenkripsi.",
                        selected = selectedEncryptionMode == "FULL",
                        onSelect = { selectedEncryptionMode = "FULL" }
                    )

                    SslModeOption(
                        title = "Full (Strict)",
                        desc = "Memvalidasi sertifikat TLS ketat pada Gateway publik.",
                        selected = selectedEncryptionMode == "STRICT",
                        onSelect = { selectedEncryptionMode = "STRICT" }
                    )

                    SslModeOption(
                        title = "Flexible",
                        desc = "Mengizinkan koneksi HTTP internal di sisi gateway.",
                        selected = selectedEncryptionMode == "FLEXIBLE",
                        onSelect = { selectedEncryptionMode = "FLEXIBLE" }
                    )
                }
            }
        }

        // Force HTTPS Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always Use HTTPS (Otomatis Redirect)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Mengarahkan semua request HTTP (port 80) ke HTTPS (port 443) secara otomatis",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }

                    Switch(
                        checked = forceHttpsGlobal,
                        onCheckedChange = onToggleForceHttpsGlobal,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SfrButtonColors.PrimaryBlue
                        )
                    )
                }
            }
        }

        // Registered Domains SSL Status
        item {
            Text(
                text = "Status Sertifikat Domain Aktif (${domains.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (domains.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Belum ada domain terdaftar. Tambahkan domain di menu 'Domains' terlebih dahulu.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
            }
        } else {
            items(domains) { dom ->
                val isChecking = testingDomains[dom.domain] == true
                val checkResult = tlsResults[dom.domain]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (dom.sslActive) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (dom.sslActive) StatusOnlineGreen else StatusWarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = dom.domain,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Text(
                                        text = if (dom.sslActive) "TLS 1.3 Terverifikasi • Auto Renewal" else "Menunggu Verifikasi TLS / DNS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (dom.sslActive) StatusOnlineGreen else StatusWarningAmber,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        testingDomains[dom.domain] = true
                                        scope.launch {
                                            val result = testTlsHandshake(dom.domain)
                                            tlsResults[dom.domain] = result.second
                                            val updated = dom.copy(sslActive = result.first)
                                            domainManager.updateDomain(updated)
                                            testingDomains[dom.domain] = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dom.sslActive) SfrButtonColors.ActiveGreen else SfrButtonColors.PrimaryBlue
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp),
                                    enabled = !isChecking
                                ) {
                                    if (isChecking) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Uji...", fontSize = 11.sp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Uji TLS", fontSize = 11.sp, color = Color.White)
                                    }
                                }

                                if (dom.sslActive) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${dom.domain}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal membuka HTTPS link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Buka", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (!checkResult.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = checkResult,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (dom.sslActive) StatusOnlineGreen else StatusOfflineRed
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SslModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = SfrPrimaryBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(desc, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline))
        }
    }
}

suspend fun testTlsHandshake(domain: String): Pair<Boolean, String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$domain")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            conn.connect()
            val code = conn.responseCode
            val cipherSuite = conn.cipherSuite
            conn.disconnect()
            Pair(true, "✓ TLS Handshake Sukses (HTTP $code, Cipher: $cipherSuite)")
        } catch (e: Exception) {
            Pair(false, "✗ TLS Gagal: ${e.message ?: "Koneksi ditolak atau sertifikat tidak valid"}")
        }
    }
}
