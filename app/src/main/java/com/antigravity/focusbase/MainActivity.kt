package com.antigravity.focusbase

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Solicitar permisos de notificaciones al iniciar (Android 13+)
        requestNotificationPermission()

        // 2. Solicitar ignorar optimizaciones de batería (Vital para persistencia)
        requestBatteryOptimizationExemption()

        // Botón para iniciar el servicio
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startFocusService()
        }

        /* NUEVO: Botón para configuración de Xiaomi.
           Descomenta esto cuando añadas un botón con id 'btnXiaomi' en tu XML
        */
        // findViewById<Button>(R.id.btnXiaomi).setOnClickListener {
        //    openXiaomiSettings()
        // }
    }

    private fun startFocusService() {
        val intent = Intent(this, FocusService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Servicio Focus Iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun openXiaomiSettings() {
        try {
            // Intenta abrir directamente la gestión de Inicio Automático de MIUI/HyperOS
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Si no es Xiaomi o la ruta cambió, abre los detalles de la app
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Busca 'Inicio Automático' en los ajustes", Toast.LENGTH_LONG).show()
        }
    }
}