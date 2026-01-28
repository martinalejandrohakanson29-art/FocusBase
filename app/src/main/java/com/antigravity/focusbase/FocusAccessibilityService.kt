package com.antigravity.focusbase

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusBaseCheck"

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // MEJORA 1: Solo escuchamos cuando la VENTANA cambia (al abrir o cambiar de app/pantalla)
        // Eliminamos TYPE_WINDOW_CONTENT_CHANGED porque genera miles de eventos y causa crashes.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val packageName = event.packageName?.toString() ?: return

            // MEJORA 2: Usamos un try-catch para que, si algo falla, el servicio NO muera
            try {
                val rootNode = rootInActiveWindow ?: return

                if (packageName == "com.android.settings" || packageName == "com.miui.securitycenter") {

                    // Buscamos los textos
                    val nodesFocus = rootNode.findAccessibilityNodeInfosByText("Focus Base")
                    val nodesUninstall = rootNode.findAccessibilityNodeInfosByText("Desinstalar")
                    val nodesForceStop = rootNode.findAccessibilityNodeInfosByText("Forzar detención")

                    val hasFocusText = nodesFocus.isNotEmpty()
                    val hasUninstall = nodesUninstall.isNotEmpty() || rootNode.findAccessibilityNodeInfosByText("Uninstall").isNotEmpty()
                    val hasForceStop = nodesForceStop.isNotEmpty() || rootNode.findAccessibilityNodeInfosByText("Force stop").isNotEmpty()

                    // MEJORA 3: Limpieza de memoria manual de los nodos (Importante para evitar que se cuelgue)
                    nodesFocus.forEach { it.recycle() }
                    nodesUninstall.forEach { it.recycle() }
                    nodesForceStop.forEach { it.recycle() }

                    if (hasFocusText && hasUninstall && hasForceStop) {
                        Log.w(TAG, "⛔ Bloqueo preventivo: Pantalla de gestión detectada.")
                        bloquearAcceso()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en el escaneo de pantalla: ${e.message}")
            }
        }
    }

    private fun bloquearAcceso() {
        // Te saca de Ajustes y te manda al inicio
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        Log.e(TAG, "🚨 Servicio interrumpido.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // MEJORA 4: Configuramos el servicio por código para asegurar estabilidad
        val info = AccessibilityServiceInfo().apply {
            // Solo eventos de cambio de ventana
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT
        }
        this.serviceInfo = info

        Log.i(TAG, "✅ Escudo Quirúrgico Conectado y Optimizado")
    }
}