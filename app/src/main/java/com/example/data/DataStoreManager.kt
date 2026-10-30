package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "sfr_host_prefs")

object PrefKeys {
    val SERVER_PORT = intPreferencesKey("server_port")
    val ACTIVE_WEBSITE = stringPreferencesKey("active_website")
    val AUTO_START_SERVER = booleanPreferencesKey("auto_start_server")
    val AUTO_START_TUNNEL = booleanPreferencesKey("auto_start_tunnel")
    val AUTO_RECONNECT_TUNNEL = booleanPreferencesKey("auto_reconnect_tunnel")
    val TUNNEL_ID = stringPreferencesKey("tunnel_id")
    val TUNNEL_AUTH_TOKEN = stringPreferencesKey("tunnel_auth_token")
    val GATEWAY_ENDPOINT = stringPreferencesKey("gateway_endpoint")
    val CLOUDFLARE_TOKEN = stringPreferencesKey("cloudflare_token")
    val CUSTOM_DOMAIN = stringPreferencesKey("custom_domain")
    val NOTIF_CHECK_ON_BOOT = booleanPreferencesKey("notif_check_on_boot")
    val NOTIF_SOUND_ENABLED = booleanPreferencesKey("notif_sound_enabled")
    val FCM_TOKEN = stringPreferencesKey("fcm_token")
    val TRAFFIC_IN = longPreferencesKey("traffic_in_bytes")
    val TRAFFIC_OUT = longPreferencesKey("traffic_out_bytes")
    val FORCE_HTTPS = booleanPreferencesKey("force_https")
    val FLOATING_BAR_ENABLED = booleanPreferencesKey("floating_bar_enabled")
    val STORAGE_PATH_CUSTOM = stringPreferencesKey("storage_path_custom")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
}

class DataStoreManager(private val context: Context) {

    val serverPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.SERVER_PORT] ?: 8080
    }

    val activeWebsite: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.ACTIVE_WEBSITE] ?: "website1"
    }

    val autoStartServer: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.AUTO_START_SERVER] ?: false
    }

    val autoStartTunnel: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.AUTO_START_TUNNEL] ?: false
    }

    val autoReconnectTunnel: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.AUTO_RECONNECT_TUNNEL] ?: true
    }

    val tunnelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.TUNNEL_ID] ?: "sfr-node-" + UUID.randomUUID().toString().take(6)
    }

    val tunnelAuthToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.TUNNEL_AUTH_TOKEN] ?: "sfr_sec_" + UUID.randomUUID().toString().replace("-", "").take(16)
    }

    val gatewayEndpoint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.GATEWAY_ENDPOINT] ?: ""
    }

    val cloudflareToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.CLOUDFLARE_TOKEN] ?: ""
    }

    val customDomain: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.CUSTOM_DOMAIN] ?: ""
    }

    val notifCheckOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.NOTIF_CHECK_ON_BOOT] ?: true
    }

    val notifSoundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.NOTIF_SOUND_ENABLED] ?: true
    }

    val fcmToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.FCM_TOKEN] ?: ""
    }

    val trafficIn: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.TRAFFIC_IN] ?: 0L
    }

    val trafficOut: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.TRAFFIC_OUT] ?: 0L
    }

    val forceHttps: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.FORCE_HTTPS] ?: true
    }

    suspend fun setServerPort(port: Int) {
        context.dataStore.edit { it[PrefKeys.SERVER_PORT] = port }
    }

    suspend fun setActiveWebsite(website: String) {
        context.dataStore.edit { it[PrefKeys.ACTIVE_WEBSITE] = website }
    }

    suspend fun setAutoStartServer(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_START_SERVER] = enabled }
    }

    suspend fun setAutoStartTunnel(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_START_TUNNEL] = enabled }
    }

    suspend fun setAutoReconnectTunnel(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_RECONNECT_TUNNEL] = enabled }
    }

    suspend fun setTunnelId(id: String) {
        context.dataStore.edit { it[PrefKeys.TUNNEL_ID] = id }
    }

    suspend fun setTunnelAuthToken(token: String) {
        context.dataStore.edit { it[PrefKeys.TUNNEL_AUTH_TOKEN] = token }
    }

    suspend fun setGatewayEndpoint(endpoint: String) {
        context.dataStore.edit { it[PrefKeys.GATEWAY_ENDPOINT] = endpoint }
    }

    suspend fun setCloudflareToken(token: String) {
        context.dataStore.edit { it[PrefKeys.CLOUDFLARE_TOKEN] = token }
    }

    suspend fun setCustomDomain(domain: String) {
        context.dataStore.edit { it[PrefKeys.CUSTOM_DOMAIN] = domain }
    }

    suspend fun setNotifCheckOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NOTIF_CHECK_ON_BOOT] = enabled }
    }

    suspend fun setNotifSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NOTIF_SOUND_ENABLED] = enabled }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { it[PrefKeys.FCM_TOKEN] = token }
    }

    suspend fun addTraffic(inBytes: Long, outBytes: Long) {
        context.dataStore.edit { prefs ->
            val currIn = prefs[PrefKeys.TRAFFIC_IN] ?: 0L
            val currOut = prefs[PrefKeys.TRAFFIC_OUT] ?: 0L
            prefs[PrefKeys.TRAFFIC_IN] = currIn + inBytes
            prefs[PrefKeys.TRAFFIC_OUT] = currOut + outBytes
        }
    }

    val floatingBarEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.FLOATING_BAR_ENABLED] ?: true
    }

    suspend fun setFloatingBarEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.FLOATING_BAR_ENABLED] = enabled }
    }

    suspend fun setForceHttps(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.FORCE_HTTPS] = enabled }
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PrefKeys.HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { it[PrefKeys.HAS_COMPLETED_ONBOARDING] = completed }
    }
}
