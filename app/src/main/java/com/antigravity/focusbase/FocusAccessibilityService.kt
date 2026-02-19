package com.antigravity.focusbase

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusBaseCheck"

    // --- LISTA NEGRA DE APLICACIONES (Actualizada) ---
    private val appBlacklist = setOf(
        "com.instagram.android",      // Instagram
        "com.facebook.katana",        // Facebook
        "com.zhiliaoapp.musically",   // TikTok
        "com.twitter.android",        // X (ex Twitter)
        "org.telegram.messenger"     // Telegram

    )

    // --- LISTA NEGRA DE WEBS (Actualizada) ---
    private val webBlacklist = setOf(
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "tiktok.com",
        "t.me",                       // Web de Telegram
        "porn",                       // Filtro +18
        "xvideos"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Escuchamos cambios de ventana y de contenido (necesario para Chrome)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val packageName = event.packageName?.toString() ?: return

            // 1. Bloqueo de Apps en Lista Negra
            if (appBlacklist.contains(packageName)) {
                Log.w(TAG, "⛔ App bloqueada por Focus Base: $packageName")
                sacarUsuarioDeApp()
                return
            }

            // 2. Bloqueo de Navegador Chrome (Optimizado para Android 16)
            if (packageName == "com.android.chrome") {
                verificarYBloquearWeb()
            }

            // 3. Escudo Anti-Desinstalación (Ajustes)
            if (packageName == "com.android.settings" || packageName == "com.miui.securitycenter") {
                verificarYBloquearAjustes()
            }
        }
    }

    private fun verificarYBloquearWeb() {
        try {
            val rootNode = rootInActiveWindow ?: return

            // IDs conocidos de la barra de Chrome
            val chromeIds = arrayOf(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/location_bar_edit_text",
                "com.android.chrome:id/search_box_text"
            )

            var detectedUrl = ""

            // Intento A: Buscar por ID directo
            for (id in chromeIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    detectedUrl = nodes[0].text?.toString()?.lowercase() ?: ""
                    nodes.forEach { it.recycle() }
                    if (detectedUrl.isNotEmpty()) break
                }
            }

            // Intento B: Búsqueda profunda si el ID falla (Plan B para HyperOS 3)
            if (detectedUrl.isEmpty()) {
                detectedUrl = buscarUrlEnNodos(rootNode) ?: ""
            }

            if (detectedUrl.isNotEmpty()) {
                for (site in webBlacklist) {
                    if (detectedUrl.contains(site)) {
                        Log.w(TAG, "⛔ Web bloqueada en Chrome: $detectedUrl")
                        sacarUsuarioDeApp()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en detector web: ${e.message}")
        }
    }

    private fun buscarUrlEnNodos(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString()?.lowercase() ?: ""

        // Si el texto parece una URL o contiene algo de nuestra lista negra
        if (text.contains(".") && (text.contains("http") || text.contains("/") || webBlacklist.any { text.contains(it) })) {
            return text
        }

        for (i in 0 until node.childCount) {
            val result = buscarUrlEnNodos(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    private fun verificarYBloquearAjustes() {
        try {
            val rootNode = rootInActiveWindow ?: return
            val nodesFocus = rootNode.findAccessibilityNodeInfosByText("Focus Base")

            if (nodesFocus.isNotEmpty()) {
                val nodesUninstall = rootNode.findAccessibilityNodeInfosByText("Desinstalar")
                val nodesForceStop = rootNode.findAccessibilityNodeInfosByText("Forzar detención")

                if (nodesUninstall.isNotEmpty() || nodesForceStop.isNotEmpty()) {
                    Log.w(TAG, "⛔ Intento de sabotaje bloqueado en Ajustes.")
                    sacarUsuarioDeApp()
                }

                nodesUninstall.forEach { it.recycle() }
                nodesForceStop.forEach { it.recycle() }
            }
            nodesFocus.forEach { it.recycle() }
        } catch (e: Exception) {}
    }

    private fun sacarUsuarioDeApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        this.serviceInfo = info
        Log.i(TAG, "✅ Servicio Focus Base: Lista Negra Expandida y Activa")
    }
}