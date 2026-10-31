package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomDomainItem
import com.example.files.WebsiteProject
import com.example.ui.theme.SfrButtonColors
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDomainsScreen(
    domains: List<CustomDomainItem>,
    websites: List<WebsiteProject>,
    gatewayHost: String,
    onAddDomain: (String, String, String, String, String?) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onVerifyDns: (String) -> Unit,
    onToggleHttps: (CustomDomainItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                        text = "Kelola Domain Utama, Addon Domain, dan Subdomain Anda",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrButtonColors.PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_custom_domain_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp, color = Color.White)
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
                                text = "Panduan Konfigurasi DNS & Verifikasi",
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
                                text = "Untuk menghubungkan domain Anda, tambahkan 2 DNS Record di registrar domain Anda (Cloudflare, Niagahoster, Domainesia, dsb):",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("1. Record Tunnel (CNAME):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("   Nama: @ / www (atau subdomain)", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text("   Target: $targetLabel", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SfrPrimaryBlue, fontWeight = FontWeight.Bold))

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("2. Record Verifikasi Kepemilikan (TXT):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("   Nama: _sfrhost-verify.<domain>", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text("   Value: sfrhost-verify=<TOKEN_DOMAIN>", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SfrSecondaryOrange, fontWeight = FontWeight.Bold))
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
                            text = "Belum Ada Domain atau Subdomain",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Klik 'Tambah' untuk menghubungkan domain utama, addon domain, atau subdomain Anda.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        } else {
            items(domains, key = { it.domain }) { item ->
                DomainItemCard(
                    item = item,
                    isVerifying = verifyingDomain == item.domain,
                    onVerifyDns = {
                        verifyingDomain = item.domain
                        scope.launch {
                            onVerifyDns(item.domain)
                            verifyingDomain = null
                        }
                    },
                    onRemove = { onRemoveDomain(item.domain) },
                    onToggleHttps = { onToggleHttps(item) }
                )
            }
        }
    }

    // Modal: Tambah Domain / Subdomain
    if (showAddDialog) {
        var domainInput by remember { mutableStateOf("") }
        var domainType by remember { mutableStateOf("MAIN") } // MAIN, ADDON, SUBDOMAIN
        var subdomainPrefix by remember { mutableStateOf("") }
        var selectedParentDomain by remember {
            val firstParent = domains.firstOrNull { it.domainType != "SUBDOMAIN" }?.domain ?: ""
            mutableStateOf(firstParent)
        }
        var hostingMode by remember { mutableStateOf("SEPARATE") } // SEPARATE, SHARED
        var selectedSharedWeb by remember { mutableStateOf(websites.firstOrNull()?.name ?: "website1") }
        var webDropdownExpanded by remember { mutableStateOf(false) }
        var parentDropdownExpanded by remember { mutableStateOf(false) }

        val eligibleParents = domains.filter { it.domainType != "SUBDOMAIN" }

        val finalDomainName = when (domainType) {
            "SUBDOMAIN" -> {
                val prefix = subdomainPrefix.trim().lowercase().removeSuffix(".")
                val parent = selectedParentDomain.trim().lowercase()
                if (prefix.isNotEmpty() && parent.isNotEmpty()) "$prefix.$parent" else prefix
            }
            else -> domainInput.trim().lowercase()
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Domain / Subdomain", fontWeight = FontWeight.Bold) },
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

                    Spacer(modifier = Modifier.height(10.dp))

                    if (domainType == "SUBDOMAIN") {
                        // Subdomain configuration
                        Text("Pilih Domain Induk (Parent Domain):", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (eligibleParents.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = parentDropdownExpanded,
                                onExpandedChange = { parentDropdownExpanded = !parentDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedParentDomain,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Domain Induk") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = parentDropdownExpanded,
                                    onDismissRequest = { parentDropdownExpanded = false }
                                ) {
                                    eligibleParents.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.domain) },
                                            onClick = {
                                                selectedParentDomain = p.domain
                                                parentDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = selectedParentDomain,
                                onValueChange = { selectedParentDomain = it },
                                label = { Text("Domain Induk") },
                                placeholder = { Text("domainanda.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = subdomainPrefix,
                            onValueChange = { subdomainPrefix = it },
                            label = { Text("Prefix Subdomain") },
                            placeholder = { Text("blog, api, toko") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (finalDomainName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pratinjau Hostname: $finalDomainName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SfrPrimaryBlue,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // Main / Addon domain input
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            label = { Text("Nama Domain") },
                            placeholder = { Text("domainanda.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

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
                            Text("Membuat direktori terisolasi di hosting/websites/", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
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
                            Text("Berbagi Document Root", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Menggunakan document root website yang sudah ada", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    if (hostingMode == "SHARED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pilih Hosting Tujuan:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        ExposedDropdownMenuBox(
                            expanded = webDropdownExpanded,
                            onExpandedChange = { webDropdownExpanded = !webDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedSharedWeb,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = webDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = webDropdownExpanded,
                                onDismissRequest = { webDropdownExpanded = false }
                            ) {
                                websites.forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w.name) },
                                        onClick = {
                                            selectedSharedWeb = w.name
                                            webDropdownExpanded = false
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
                        if (finalDomainName.isNotBlank()) {
                            val parent = if (domainType == "SUBDOMAIN") selectedParentDomain else null
                            onAddDomain(finalDomainName, domainType, hostingMode, selectedSharedWeb, parent)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrButtonColors.PrimaryBlue)
                ) {
                    Text("Simpan Domain", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun DomainItemCard(
    item: CustomDomainItem,
    isVerifying: Boolean,
    onVerifyDns: () -> Unit,
    onRemove: () -> Unit,
    onToggleHttps: () -> Unit
) {
    val context = LocalContext.current

    val (badgeBg, badgeText, statusIcon) = when (item.dnsStatus) {
        "VERIFIED" -> Triple(StatusOnlineGreen, "TERVERIFIKASI", Icons.Default.CheckCircle)
        "PROPAGATING", "PENDING" -> Triple(StatusWarningAmber, "PROPAGASI / PENDING", Icons.Default.Refresh)
        else -> Triple(StatusOfflineRed, "BELUM COCOK", Icons.Default.Error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Domain Name + Badge + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                    if (item.parentDomain != null) {
                        Text(
                            text = "Induk: ${item.parentDomain}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Text(
                        text = if (item.hostingMode == "SEPARATE") "DocRoot: ${item.documentRootPath}"
                        else "Berbagi (${item.websiteName}) • ${item.documentRootPath}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = SfrButtonColors.DangerRed)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // DNS Status Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = statusIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (item.sslActive) "SSL Aktif" else "SSL Belum Terhubung",
                    fontSize = 11.sp,
                    color = if (item.sslActive) StatusOnlineGreen else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Cryptographic TXT Verification Record Display
            if (item.verificationToken.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Record TXT Verifikasi:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SfrSecondaryOrange)
                            Text("_sfrhost-verify.${item.domain}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("sfrhost-verify=${item.verificationToken}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SfrPrimaryBlue)
                        }

                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("TXT Token", "sfrhost-verify=${item.verificationToken}"))
                                Toast.makeText(context, "TXT Token disalin!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = SfrPrimaryBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            item.diagnosticMessage.takeIf { it.isNotEmpty() }?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(msg, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Force HTTPS Switch + Cek DNS Button
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
                        onCheckedChange = { onToggleHttps() },
                        modifier = Modifier.size(36.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onVerifyDns,
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.dnsStatus == "VERIFIED") SfrButtonColors.ActiveGreen else SfrButtonColors.PrimaryBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Menguji...", fontSize = 11.sp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cek DNS", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    if (item.sslActive || item.dnsStatus == "VERIFIED") {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${item.domain}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membuka browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buka", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
