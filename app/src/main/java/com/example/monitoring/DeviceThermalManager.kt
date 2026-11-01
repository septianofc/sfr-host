package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalLevel(val label: String, val thresholdCelsius: Float) {
    NORMAL("Normal", 38.0f),
    WARM("Hangat", 40.0f),
    HOT("Panas (Beban Dikurangi)", 44.0f),
    CRITICAL("Panas Berlebih (Darurat Dimatikan)", 48.0f)
}

/**
 * Real device thermal monitor utilizing actual Android hardware sensors:
 * 1. BatteryManager.EXTRA_TEMPERATURE (Battery sensor reading in 0.1°C units)
 * 2. PowerManager.OnThermalStatusChangedListener (Android 10+ API 29 hardware thermal status)
 *
 * Implements automated device protection:
 * - When HP reaches HOT (>= 44°C): Auto-reduce running hostings down to 1 primary website.
 * - When HP reaches CRITICAL (>= 48°C): Emergency shutoff of hosting & tunnel to protect phone hardware.
 */
class DeviceThermalManager(
    private val context: Context,
    private val onHotState: (() -> Unit)? = null,
    private val onCriticalState: (() -> Unit)? = null
) {

    private val _temperatureCelsius = MutableStateFlow(32.0f)
    val temperatureCelsius: StateFlow<Float> = _temperatureCelsius.asStateFlow()

    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private val _thermalStatusDescription = MutableStateFlow("Suhu perangkat aman dan optimal")
    val thermalStatusDescription: StateFlow<String> = _thermalStatusDescription.asStateFlow()

    private var thermalListener: Any? = null
    private var isReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320)
                val tempC = tempTenths / 10.0f
                _temperatureCelsius.value = tempC
                evaluateThermalState(tempC)
            }
        }
    }

    init {
        startMonitoring()
    }

    fun startMonitoring() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> {
                            if (_temperatureCelsius.value < 40.0f) {
                                _thermalLevel.value = ThermalLevel.NORMAL
                                _thermalStatusDescription.value = "Suhu perangkat normal dan optimal"
                            }
                        }
                        PowerManager.THERMAL_STATUS_LIGHT -> {
                            _thermalLevel.value = ThermalLevel.WARM
                            _thermalStatusDescription.value = "Suhu sedikit hangat (${_temperatureCelsius.value}°C)"
                        }
                        PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE -> {
                            _thermalLevel.value = ThermalLevel.HOT
                            _thermalStatusDescription.value = "Peringatan: HP Panas (${_temperatureCelsius.value}°C). Mengurangi beban hosting ke 1 website aktif."
                            onHotState?.invoke()
                        }
                        PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                            _thermalLevel.value = ThermalLevel.CRITICAL
                            _thermalStatusDescription.value = "BAHAYA: Panas berlebih (${_temperatureCelsius.value}°C)! Menghentikan semua layanan hosting & tunnel."
                            onCriticalState?.invoke()
                        }
                    }
                }
                powerManager?.addThermalStatusListener(listener)
                thermalListener = listener
            } catch (ignored: Exception) {}
        }
    }

    fun stopMonitoring() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (ignored: Exception) {}
            isReceiverRegistered = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("UNCHECKED_CAST")
                powerManager?.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            } catch (ignored: Exception) {}
            thermalListener = null
        }
    }

    private fun evaluateThermalState(tempCelsius: Float) {
        val newLevel = when {
            tempCelsius >= 48.0f -> ThermalLevel.CRITICAL
            tempCelsius >= 44.0f -> ThermalLevel.HOT
            tempCelsius >= 40.0f -> ThermalLevel.WARM
            else -> ThermalLevel.NORMAL
        }

        val previous = _thermalLevel.value
        _thermalLevel.value = newLevel

        when (newLevel) {
            ThermalLevel.NORMAL -> {
                _thermalStatusDescription.value = "Suhu HP optimal (${tempCelsius}°C). Layanan beroperasi normal."
            }
            ThermalLevel.WARM -> {
                _thermalStatusDescription.value = "Suhu HP agak hangat (${tempCelsius}°C)."
            }
            ThermalLevel.HOT -> {
                _thermalStatusDescription.value = "HP Panas (${tempCelsius}°C)! Otomatis membatasi hosting aktif menjadi 1 website."
                if (previous != ThermalLevel.HOT && previous != ThermalLevel.CRITICAL) {
                    onHotState?.invoke()
                }
            }
            ThermalLevel.CRITICAL -> {
                _thermalStatusDescription.value = "DARURAT: Panas berlebih (${tempCelsius}°C)! Hosting & tunnel dimatikan otomatis demi keamanan HP."
                if (previous != ThermalLevel.CRITICAL) {
                    onCriticalState?.invoke()
                }
            }
        }
    }
}
