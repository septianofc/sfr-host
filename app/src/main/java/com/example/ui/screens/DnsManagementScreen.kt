package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class DnsRecordItem(
    val type: String,
    val name: String,
    val target: String,
    val proxyStatus: String = "Proxied"
)

@Composable
fun DnsManagementScreen(
    domains: List<CustomDomainItem>,
    gatewayHost: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var testDomainInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingDns by remember { mutableStateOf(false) }

    val sampleRecords = remember(domains, gatewayHost) {
        val target = if (gatewayHost.isBlank()) "vps-gateway-anda.com" else gatewayHost
        val list = mutableListOf<DnsRecordItem>()
        if (domains.isEmpty()) {
            list.add(DnsRecordItem("CNAME", "@", target, "Proxied"))
            list.add(DnsRecordItem("CNAME", "www", target, "Proxied"))
            list.add(DnsRecordItem("TXT", "_sfrhost-verify", "sfrhost-verify=a1b2c3d4", "DNS Only"))
        } else {
            domains.forEach { d ->
                list.add(DnsRecordItem("CNAME", d.domain, target, "Proxied"))
                list.add(DnsRecordItem("CNAME", "www.${d.domain}", target, "Proxied"))
                list.add(DnsRecordItem("TXT", "_sfrhost-verify.${d.domain}", d.verificationToken, "DNS Only"))
            }
        }
        list
    }

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
                    text = "Cloudflare & DNS Records Manager",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Konfigurasi rekaman DNS domain Anda agar terhubung dengan Gateway publik",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // Live DNS Diagnostics Tool
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alat Cek Propagasi DNS Real-Time",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = testDomainInput,
                            onValueChange = { testDomainInput = it },
                            placeholder = { Text("domainanda.com") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (testDomainInput.isNotBlank()) {
                                    isTestingDns = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            try {
                                                val clean = testDomainInput.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
                                                val ips = InetAddress.getAllByName(clean).map { it.hostAddress }
                                                "✓ Berhasil terurai ke IP: ${ips.joinToString(", ")}"
                                            } catch (e: Exception) {
                                                "✕ Domain belum terurai atau DNS belum propagasi: ${e.message}"
                                            }
                                        }
                                        testResult = res
                                        isTestingDns = false
                                    }
                                }
                            },
                            enabled = !isTestingDns,
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isTestingDns) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Uji DNS")
                            }
                        }
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (testResult!!.startsWith("✓")) StatusOnlineGreen.copy(alpha = 0.12f) else StatusOfflineRed.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = testResult!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = if (testResult!!.startsWith("✓")) StatusOnlineGreen else StatusOfflineRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }

        // DNS Records Table (Cloudflare Style)
        item {
            Text(
                text = "Tabel Rekaman DNS yang Dibutuhkan",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        items(sampleRecords) { rec ->
            DnsRecordCard(rec = rec) { text ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("DNS Record", text))
                Toast.makeText(context, "Disalin: $text", Toast.LENGTH_SHORT).show()
            }
        }

        // CNAME Flattening Explanation Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SfrSecondaryOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Catatan CNAME Flattening (Apex / Root Domain)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Untuk root domain (misal: 'tokoku.com' tanpa 'www'), Cloudflare mendukung 'CNAME Flattening' secara otomatis. Jika Anda menggunakan DNS provider lain yang tidak mengizinkan CNAME pada root domain (@), gunakan subdomain (misal: 'www.tokoku.com' atau 'app.tokoku.com') atau arahkan A Record ke IP VPS Gateway SFR Host.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun DnsRecordCard(
    rec: DnsRecordItem,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (rec.type == "CNAME") SfrPrimaryBlue.copy(alpha = 0.15f) else SfrSecondaryOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rec.type,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (rec.type == "CNAME") SfrPrimaryBlue else SfrSecondaryOrange
                        )
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = rec.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        text = rec.target,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (rec.proxyStatus == "Proxied") SfrSecondaryOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rec.proxyStatus == "Proxied") {
                            Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = SfrSecondaryOrange, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = rec.proxyStatus,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rec.proxyStatus == "Proxied") SfrSecondaryOrange else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                IconButton(onClick = { onCopy(rec.target) }) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin Target", tint = SfrPrimaryBlue, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
