package com.antigravity.focusbase

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Comentamos FLAG_SECURE temporalmente para evitar conflictos con transiciones de sistema en MIUI
        // window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        // ... resto del código ...
        requestNotificationPermission()
        requestBatteryOptimizationExemption()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startFocusService()
        }

        findViewById<Button>(R.id.btnAccessibility)?.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "El escudo ya está activo ✅", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnXiaomi)?.setOnClickListener {
            openXiaomiSettings()
        }

        findViewById<Button>(R.id.btnAdmin)?.setOnClickListener {
            requestDeviceAdmin()
        }
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        
        if (dpm.isAdminActive(componentName)) {
            Toast.makeText(this, "El Administrador ya está activo ✅", Toast.LENGTH_SHORT).show()
            android.util.Log.i("FocusBaseCheck", "Admin ya activo, no es necesario abrir la pantalla.")
            return
        }

        android.util.Log.i("FocusBaseCheck", "Lanzando solicitud de Administrador de Dispositivo...")
        try {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protección necesaria para evitar desinstalaciones accidentales.")
                // IMPORTANTE: En algunos Xiaomi, añadir este flag ayuda a que la ventana no se pierda
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            Toast.makeText(this, "Abriendo ajustes de administrador...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("FocusBaseCheck", "Error al abrir admin directo: ${e.message}")
            try {
                val intent = Intent().apply {
                    action = "android.settings.DEVICE_ADMIN_SETTINGS"
                }
                startActivity(intent)
                Toast.makeText(this, "Busca 'Focus Base Admin' en la lista", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "No se pudo acceder a los ajustes", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${FocusAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Busca 'Focus Base' y actívalo", Toast.LENGTH_LONG).show()
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
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Algunos dispositivos no permiten la intención directa
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun openXiaomiSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Busca 'Inicio Automático' en los ajustes", Toast.LENGTH_LONG).show()
        }
    }
}