package com.antigravity.focusbase

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FocusService : Service() {

    private val CHANNEL_ID = "FocusServiceChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Modo Focus Activo")
            .setContentText("Protección de persistencia activada.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Mayor prioridad para Xiaomi
            .build()

        // CORRECCIÓN CRÍTICA PARA ANDROID 15:
        // Debemos pasar el tipo de servicio también aquí, no solo en el Manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Este es el "resucitador" cuando deslizas la app en recientes
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)

        // En Android 15, relanzar un servicio desde el fondo tiene restricciones.
        // Usamos este truco para intentar que el sistema lo mantenga en cola.
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Focus",
                NotificationManager.IMPORTANCE_HIGH // Subimos importancia para evitar el cierre
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}