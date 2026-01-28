package com.antigravity.focusbase

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "FocusBaseCheck"

    // El "Latido": Envía un mensaje al Logcat cada 30 segundos para confirmar que sigue vivo
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "❤ Latido: El servicio sigue activo - ${System.currentTimeMillis()}")
            handler.postDelayed(this, 30000) // Se repite cada 30 segundos
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "FocusServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        // Este log te dirá si la app arrancó de cero o si resucitó tras ser cerrada
        Log.i(TAG, "🚀 SERVICIO INICIADO O REINICIADO")

        createNotificationChannel()
        handler.post(heartbeatRunnable) // Iniciamos el monitoreo del latido
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Modo Focus Activo")
            .setContentText("Protección de persistencia activada.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // El "Desfibrilador": Se activa cuando el usuario desliza la app en la lista de Recientes
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "⚠️ Se detectó cierre manual (onTaskRemoved). Intentando reanimación...")

        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
            setPackage(packageName)
        }

        val restartPendingIntent = PendingIntent.getService(
            this, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartPendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.e(TAG, "🛑 El servicio ha sido destruido por el sistema.")
        handler.removeCallbacks(heartbeatRunnable) // Limpiamos el handler si se cierra
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Servicio Focus",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mantiene la aplicación FocusBase activa en segundo plano"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}