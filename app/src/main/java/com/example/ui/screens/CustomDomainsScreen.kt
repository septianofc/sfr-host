package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomDomainItem
import com.example.files.WebsiteProject
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDomainsScreen(
    domains: List<CustomDomainItem>,
    websites: List<WebsiteProject>,
    gatewayHost: String,
    onAddDomain: (String, String, String, String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onVerifyDns: (String) -> Unit,
    onToggleHttps: (CustomDomainItem) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var verifyingDomain by remember { mutableStateOf<String?>(null) }
    var showDnsHelperCard by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Domain & Subdomain",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = SfrPrimaryBlue
                    )
                    Text(
                        text = "Kelola Domain Utama, Addon Domain, dan Subdomain",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_custom_domain_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp)
                }
            }
        }

        // DNS Guide
        item {
            val targetLabel = if (gatewayHost.isNotEmpty()) gatewayHost else "hubungkan-tunnel-anda.trycloudflare.com"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDnsHelperCard = !showDnsHelperCard },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = SfrSecondaryOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Panduan DNS & Cloudflare Tunnel",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Icon(
                            imageVector = if (showDnsHelperCard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showDnsHelperCard) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Untuk menghubungkan domain Anda (misal: septianfr.web.id), tambahkan DNS Record di penyedia domain Anda (Cloudflare, Niagahoster, Rumahweb, dll):",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Tipe: CNAME atau A Record", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Nama: @ atau www (atau subdomain)", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text("Target: $targetLabel", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SfrPrimaryBlue, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Domains List
        if (domains.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum ada domain atau subdomain",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Klik 'Tambah' untuk menambahkan domain utama, addon domain, atau subdomain.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        } else {
            items(domains, key = { it.domain }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.domain,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(SfrPrimaryBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(item.domainType, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SfrPrimaryBlue)
                                    }
                                }

                                Text(
                                    text = if (item.hostingMode == "SEPARATE") "Hosting Terpisah • DocRoot: ${item.documentRootPath}"
                                           else "Berbagi Hosting (${item.websiteName}) • DocRoot: ${item.documentRootPath}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                )
                            }

                            IconButton(onClick = { onRemoveDomain(item.domain) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SfrPrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Force HTTPS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = item.forceHttps,
                                    onCheckedChange = { onToggleHttps(item) },
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { onVerifyDns(item.domain) },
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cek DNS", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Tambah Domain / Subdomain
    if (showAddDialog) {
        var domainInput by remember { mutableStateOf("") }
        var domainType by remember { mutableStateOf("MAIN") } // MAIN, ADDON, SUBDOMAIN
        var hostingMode by remember { mutableStateOf("SEPARATE") } // SEPARATE, SHARED
        var selectedSharedWeb by remember { mutableStateOf(websites.firstOrNull()?.name ?: "website1") }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Domain / Subdomain") },
            text = {
                Column {
                    // Type selector
                    Text("Tipe Domain:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("MAIN" to "Utama", "ADDON" to "Addon", "SUBDOMAIN" to "Subdomain").forEach { (typeKey, label) ->
                            FilterChip(
                                selected = domainType == typeKey,
                                onClick = { domainType = typeKey },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Nama Domain / Subdomain") },
                        placeholder = { Text(if (domainType == "SUBDOMAIN") "blog.septianfr.web.id" else "tokoku.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Konfigurasi Hosting:", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Radio 1: Hosting Terpisah
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hostingMode = "SEPARATE" }
                    ) {
                        RadioButton(
                            selected = hostingMode == "SEPARATE",
                            onClick = { hostingMode = "SEPARATE" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Hosting Terpisah (Rekomendasi)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Membuat direktori dan file publik sendiri di hosting/websites/", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Radio 2: Berbagi Hosting
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hostingMode = "SHARED" }
                    ) {
                        RadioButton(
                            selected = hostingMode == "SHARED",
                            onClick = { hostingMode = "SHARED" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Berbagi Hosting", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Menggunakan document root website yang sudah ada", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    if (hostingMode == "SHARED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pilih Hosting Tujuan:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedSharedWeb,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                websites.forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w.name) },
                                        onClick = {
                                            selectedSharedWeb = w.name
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (domainInput.isNotBlank()) {
                            onAddDomain(domainInput, domainType, hostingMode, selectedSharedWeb)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Batal") }
            }
        )
    }
}
