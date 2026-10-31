package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange

@Composable
fun DeveloperScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka tautan browser.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label disalin ke clipboard", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Developer Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(SfrPrimaryBlue.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SfrPrimaryBlue,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Septian Fathur Rohman",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Developer & Creator of SFR Host",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .background(SfrSecondaryOrange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Versi: SFRHOST-R-001 (Code 1)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SfrSecondaryOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Official Links Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tautan Resmi & Download",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SfrPrimaryBlue
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Official GitHub Repository
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repository GitHub Resmi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "https://github.com/septianofc/sfr-host",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row {
                        IconButton(onClick = { copyText("Repository", "https://github.com/septianofc/sfr-host") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = SfrPrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { openUrl("https://github.com/septianofc/sfr-host") }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Buka", tint = SfrPrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Download Page Releases
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Unduh Rilis APK Terbaru", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "https://github.com/septianofc/sfr-host/releases",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row {
                        IconButton(onClick = { copyText("Download Link", "https://github.com/septianofc/sfr-host/releases") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = SfrPrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { openUrl("https://github.com/septianofc/sfr-host/releases") }) {
                            Icon(Icons.Default.Download, contentDescription = "Unduh", tint = SfrPrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Specifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Spesifikasi SFR Host Android",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Package ID", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("com.sfrhostapp", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Arsitektur", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Native Android Kotlin + Jetpack Compose", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Web Server Engine", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Embedded HTTP Socket & PHP 8.2 Engine", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tunnel Provider", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Cloudflare Tunnel / Named Tunnel", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}
