package com.antigravity.focusbase

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusBaseCheck"

    // --- LISTA NEGRA DE APPLICACIONES ---
    private val appBlacklist = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.twitter.android"
    )

    // --- LISTA NEGRA DE WEBS (Palabras clave) ---
    private val webBlacklist = setOf(
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "p**nhub", // Palabras clave para +18
        "xvideos"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Filtramos solo por cambios de ventana para no saturar el procesador
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val packageName = event.packageName?.toString() ?: return

            // 1. BLOQUEO DE APPS
            if (appBlacklist.contains(packageName)) {
                Log.w(TAG, "⛔ App prohibida detectada: $packageName")
                bloquearAcceso()
                return
            }

            // 2. BLOQUEO DE WEBS (Solo si la app es Chrome)
            if (packageName == "com.android.chrome") {
                verificarYBloquearWeb()
            }

            // 3. ESCUDO ANTI-DESINSTALACIÓN (Ajustes)
            if (packageName == "com.android.settings" || packageName == "com.miui.securitycenter") {
                verificarYBloquearAjustes()
            }
        }
    }

    private fun verificarYBloquearWeb() {
        try {
            val rootNode = rootInActiveWindow ?: return

            // Buscamos la barra de direcciones de Chrome por su ID técnico
            // IDs comunes: 'url_bar' o 'location_bar_edit_text'
            val urlNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")

            if (urlNodes.isNotEmpty()) {
                val urlText = urlNodes[0].text?.toString()?.lowercase() ?: ""

                // Comprobamos si la URL contiene alguna palabra prohibida
                for (site in webBlacklist) {
                    if (urlText.contains(site)) {
                        Log.w(TAG, "⛔ Web prohibida detectada en Chrome: $urlText")
                        bloquearAcceso()
                        break
                    }
                }
            }

            // Limpieza de memoria
            urlNodes.forEach { it.recycle() }

        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando Chrome: ${e.message}")
        }
    }

    private fun verificarYBloquearAjustes() {
        try {
            val rootNode = rootInActiveWindow ?: return

            val nodesFocus = rootNode.findAccessibilityNodeInfosByText("Focus Base")
            val nodesUninstall = rootNode.findAccessibilityNodeInfosByText("Desinstalar")
            val nodesForceStop = rootNode.findAccessibilityNodeInfosByText("Forzar detención")

            if (nodesFocus.isNotEmpty() && (nodesUninstall.isNotEmpty() || nodesForceStop.isNotEmpty())) {
                Log.w(TAG, "⛔ Intento de sabotaje detectado en Ajustes.")
                bloquearAcceso()
            }

            // Reciclaje de nodos
            nodesFocus.forEach { it.recycle() }
            nodesUninstall.forEach { it.recycle() }
            nodesForceStop.forEach { it.recycle() }

        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando ajustes: ${e.message}")
        }
    }

    private fun bloquearAcceso() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT
        }
        this.serviceInfo = info
        Log.i(TAG, "✅ FocusBase: Detector de Webs y Apps iniciado.")
    }
}