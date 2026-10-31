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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.DomainManager
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch

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
    val domainManager = remember { DomainManager(context) }

    var testDomainInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingDns by remember { mutableStateOf(false) }

    val sampleRecords = remember(domains) {
        val list = mutableListOf<DnsRecordItem>()
        domains.forEach { d ->
            val target = "${d.domain}.cdn.cloudflare.net"
            list.add(DnsRecordItem("CNAME", d.domain, target, "Proxied (DNS + WAF)"))
            list.add(DnsRecordItem("CNAME", "www.${d.domain}", target, "Proxied (DNS + WAF)"))
            list.add(DnsRecordItem("TXT", "_sfrhost-verify.${d.domain}", d.verificationToken, "DNS Only"))
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
                    text = "Cloudflare DNS & Propagation Tool",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SfrPrimaryBlue
                )
                Text(
                    text = "Verifikasi Rekaman CNAME, TXT, dan Propagasi DNS menggunakan Cloudflare DoH Resmi",
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
                        text = "Uji Resolusi DNS-over-HTTPS (DoH)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pemeriksaan langsung ke resolver 1.1.1.1 (Cloudflare DNS) tanpa cache lokal:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = testDomainInput,
                        onValueChange = { testDomainInput = it },
                        label = { Text("Nama Domain (misal: septianfr.web.id)") },
                        placeholder = { Text("domainanda.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (testDomainInput.isNotBlank()) {
                                isTestingDns = true
                                scope.launch {
                                    val report = domainManager.verifyDns(testDomainInput.trim())
                                    testResult = "Status: ${report.status}\n" +
                                            "CNAME: ${report.actualCname ?: "Tidak ditemukan"}\n" +
                                            "TXT: ${report.actualTxt ?: "Tidak ditemukan"}\n" +
                                            "IP (A Record): ${if (report.actualIps.isEmpty()) "Tidak ditemukan" else report.actualIps.joinToString(", ")}\n" +
                                            "HTTPS Terhubung: ${if (report.isHttpsWorking) "YA (SSL Aktif)" else "TIDAK"}\n" +
                                            "Diagnostik: ${report.detailMessage}"
                                    isTestingDns = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                        enabled = !isTestingDns
                    ) {
                        if (isTestingDns) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memeriksa DoH...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cek DoH Cloudflare")
                        }
                    }

                    testResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = res,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // Active Domain Records
        item {
            Text(
                text = "Rekaman DNS untuk Domain Aktif (${domains.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (sampleRecords.isEmpty()) {
            item {
                Text(
                    text = "Belum ada domain terdaftar. Tambahkan domain di menu 'Domains' terlebih dahulu.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(sampleRecords) { record ->
                DnsRecordRowCard(record = record)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun DnsRecordRowCard(record: DnsRecordItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SfrPrimaryBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(record.type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SfrPrimaryBlue)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(record.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Target: ${record.target}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("DNS Target", record.target))
                Toast.makeText(context, "Target disalin ke clipboard", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = SfrPrimaryBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
}
