package com.antigravity.focusbase

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {

    private val tag = "FocusBaseCheck"
    private var lastBlockedPackage: String? = null

    // --- LISTA NEGRA DE APLICACIONES ---
    private val appBlacklist = setOf(
        "com.instagram.android",      // Instagram
        "com.facebook.katana",        // Facebook
        "com.zhiliaoapp.musically",   // TikTok
        "com.twitter.android",        // X (ex Twitter)
        "org.telegram.messenger",     // Telegram
    )

    // --- TIENDAS DE APPS (Permitidas para actualizar, pero NO para instalar nuevas) ---
    private val storePackages = setOf(
        "com.android.vending",           // Google Play Store
        "com.xiaomi.mipicks",            // Xiaomi GetApps
        "com.sec.android.app.samsungapps" // Samsung Galaxy Store
    )

    // --- LISTA NEGRA DE WEBS ---
    private val webBlacklist = setOf(
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "tiktok.com",
        "t.me",
        "porn",
        "xvideos"
    )

    private val compiledWebRegex = webBlacklist.filter { it.contains(".") }.map {
        Regex("(^|\\.)" + Regex.escape(it.lowercase()) + "($|/|\\?|:)")
    }

    private val plainKeywords = webBlacklist.filter { !it.contains(".") }

    // --- LISTA DE INSTALADORES Y TIENDAS ---
    private val installerPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller"
    )

    // --- LISTA DE NAVEGADORES (Para restringir el bloqueo de URLs) ---
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.mi.globalbrowser",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.brave.browser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.UCMobile.intl",
        "com.android.browser",
        "org.torproject.torbrowser"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Monitoreo constante en eventos relevantes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                
                // Debug log para ver qué está pasando
                if (appBlacklist.contains(packageName) || installerPackages.contains(packageName) || storePackages.contains(packageName)) {
                    Log.d(tag, "🔍 Evento detectado en: $packageName (Tipo: ${event.eventType})")
                }

                // 0. Escudo de Recientes (Punto 4)
                // Si el usuario entra a Recientes y venía de una app bloqueada, lo sacamos
                // para proteger el contenido de la miniatura.
                if (packageName == "com.android.systemui" || packageName.contains("launcher")) {
                    val className = event.className?.toString() ?: ""
                    if (className.contains("Recents", ignoreCase = true) || 
                        className.contains("OverviewProxy", ignoreCase = true)) {
                        
                        if (lastBlockedPackage != null) {
                            Log.w(tag, "🛡️ Escudo de Recientes activado para: $lastBlockedPackage")
                            sacarUsuarioDeApp()
                            lastBlockedPackage = null // Resetear tras proteger
                            return
                        }
                    }
                }

                // 1. Bloqueo de Apps en Lista Negra
                if (appBlacklist.contains(packageName)) {
                    Log.w(tag, "⛔ App bloqueada: $packageName")
                    lastBlockedPackage = packageName
                    sacarUsuarioDeApp()
                    return
                } else {
                    // Si entra a una app permitida (y no es el sistema/launcher), reseteamos el estado
                    if (packageName != "com.android.systemui" && !packageName.contains("launcher")) {
                        lastBlockedPackage = null
                    }
                }

                // 2. Bloqueo de Navegadores (Mejorado - Solo en apps de navegación)
                // Solo ejecutamos esto si estamos en un navegador para evitar falsos positivos
                // en apps de chat (como WhatsApp) donde se previsualizan links.
                if (browserPackages.contains(packageName)) {
                    verificarYBloquearWeb()
                }

                // 3. Escudo Anti-Desinstalación y Sabotaje
                if (packageName == "com.android.settings" || packageName == "com.miui.securitycenter") {
                    verificarYBloquearAjustes()
                }

                // 4. Escudo Anti-Instalación de APKs sideload (Modo Ultra)
                if (installerPackages.contains(packageName)) {
                    verificarYBloquearInstalacion()
                }

                // 5. Escudo Anti-Instalación en tiendas: solo bloquea apps NUEVAS, permite actualizaciones
                if (storePackages.contains(packageName)) {
                    verificarYBloquearNuevaInstalacionEnTienda()
                }
            }
            else -> {}
        }
    }

    private fun verificarYBloquearWeb() {
        try {
            val rootNode = rootInActiveWindow ?: return

            // Lista extendida de IDs comunes (siempre es bueno tenerlos como prioridad)
            val browserIds = arrayOf(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/location_bar_edit_text",
                "com.android.chrome:id/search_box_text",
                "com.mi.globalbrowser:id/url_bar",
                "com.mi.globalbrowser:id/search_hint",
                "com.mi.globalbrowser:id/at_url_bar",
                "org.mozilla.firefox:id/url_bar_title",
                "com.microsoft.emmx:id/url_bar"
            )

            var detectedUrl = ""

            // Primero intentamos por IDs conocidos
            for (id in browserIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val node = nodes[0]
                    // Si el campo está enfocado, el usuario está escribiendo — no interrumpir
                    if (node.isFocused) return
                    val text = node.text?.toString()?.lowercase() ?: ""
                    if (text.isNotBlank() && isBlacklisted(text)) {
                        detectedUrl = text
                    }
                    if (detectedUrl.isNotEmpty()) break
                }
            }

            // Si no detectamos por ID, usamos búsqueda recursiva inteligente (Punto 1)
            if (detectedUrl.isEmpty()) {
                detectedUrl = buscarUrlEnNodos(rootNode) ?: ""
            }

            if (detectedUrl.isNotEmpty()) {
                Log.w(tag, "⛔ Web bloqueada: $detectedUrl")
                retrocederEnNavegador()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error en verificarYBloquearWeb", e)
        }
    }

    private fun retrocederEnNavegador() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun isBlacklisted(text: String): Boolean {
        for (regex in compiledWebRegex) {
            if (regex.containsMatchIn(text)) return true
        }
        for (keyword in plainKeywords) {
            if (text.contains(keyword, ignoreCase = true)) return true
        }
        return false
    }

    private fun buscarUrlEnNodos(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        // Nodo en foco = usuario escribiendo, ignorar
        if (node.isFocused) return null

        val text = node.text?.toString()?.lowercase() ?: ""

        if (text.isNotBlank()) {
            val looksLikeUrl = text.contains(".") || text.contains("/") || text.contains("http")
            if (looksLikeUrl && isBlacklisted(text)) return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = buscarUrlEnNodos(child)
            if (result != null) return result
        }
        return null
    }

    private fun verificarYBloquearAjustes() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // Buscamos "Focus Base" para saber si estamos en la pantalla de gestión de NUESTRA app
            val nodesFocus = rootNode.findAccessibilityNodeInfosByText("Focus Base")
            
            // EXCEPCIÓN CRÍTICA: Si la pantalla contiene "Activar" o "Permitir", 
            // probablemente es la pantalla de configuración inicial. NO BLOQUEAR.
            val isActivationScreen = rootNode.findAccessibilityNodeInfosByText("Activar").isNotEmpty() || 
                                   rootNode.findAccessibilityNodeInfosByText("Activate").isNotEmpty() ||
                                   rootNode.findAccessibilityNodeInfosByText("Permitir").isNotEmpty() ||
                                   rootNode.findAccessibilityNodeInfosByText("Allow").isNotEmpty()

            if (nodesFocus.isNotEmpty() && !isActivationScreen) {
                val actions = arrayOf(
                    "Desinstalar", "Uninstall", "Supprimer", 
                    "Forzar detención", "Force stop", "Forcer l'arrêt",
                    "Borrar datos", "Clear data", "Effacer les données"
                )
                for (action in actions) {
                    val actionNodes = rootNode.findAccessibilityNodeInfosByText(action)
                    if (actionNodes.isNotEmpty()) {
                        Log.w(tag, "⛔ BLOQUEO POR ACCIÓN: Se detectó '$action' en la pantalla de Focus Base")
                        sacarUsuarioDeApp()
                        break
                    }
                }
            }

            // --- ESCUDO EXTRA: Bloquear desactivación de Administrador de Dispositivos ---
            // Solo si NO es la pantalla de activación (que también dice Focus Base Admin)
            val adminNodes = rootNode.findAccessibilityNodeInfosByText("Focus Base Admin")
            if (adminNodes.isNotEmpty() && !isActivationScreen) {
                val deactivateActions = arrayOf("Desactivar", "Deactivate", "Désactiver")
                for (action in deactivateActions) {
                    val deactivateNodes = rootNode.findAccessibilityNodeInfosByText(action)
                    if (deactivateNodes.isNotEmpty()) {
                        Log.w(tag, "⛔ Bloqueando intento de desactivar administrador.")
                        sacarUsuarioDeApp()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error en verificarYBloquearAjustes", e)
        }
    }

    private fun verificarYBloquearNuevaInstalacionEnTienda() {
        try {
            val rootNode = rootInActiveWindow ?: return

            // Solo bloqueamos "Instalar"/"Install" — NO "Actualizar"/"Update"
            // El texto "Install" cubre "Installer" (francés) por ser subcadena
            val installTexts = arrayOf("Instalar", "Install")

            val suspiciousNodes = mutableListOf<AccessibilityNodeInfo>()
            buscarBotonesSospechosos(rootNode, installTexts, suspiciousNodes)

            for (node in suspiciousNodes) {
                if (node.isClickable || node.parent?.isClickable == true || node.className?.contains("Button") == true) {
                    Log.e(tag, "⛔ NUEVA INSTALACIÓN BLOQUEADA en tienda: '${node.text ?: node.contentDescription}'")
                    sacarUsuarioDeApp()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error en verificarYBloquearNuevaInstalacionEnTienda", e)
        }
    }

    private fun verificarYBloquearInstalacion() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                // A veces en transiciones rápidas es null, reintentamos brevemente
                Log.d(tag, "⏳ rootInActiveWindow es null, esperando UI...")
                return
            }
            val criticalTexts = arrayOf(
                "Instalar", "Install", "Installer",
                "Actualizar", "Update", "Mettre à jour",
                "Aceptar", "Accept", "Accepter",
                "Descargar", "Download", "Télécharger"
            )
            
            val suspiciousNodes = mutableListOf<AccessibilityNodeInfo>()
            buscarBotonesSospechosos(rootNode, criticalTexts, suspiciousNodes)

            for (node in suspiciousNodes) {
                if (node.isClickable || node.parent?.isClickable == true || node.className?.contains("Button") == true) {
                    Log.e(tag, "⛔ BLOQUEO: Se detectó botón crítico ('${node.text ?: node.contentDescription}')")
                    sacarUsuarioDeApp()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error en verificarYBloquearInstalacion", e)
        }
    }

    private fun buscarBotonesSospechosos(node: AccessibilityNodeInfo?, targets: Array<String>, resultList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        for (target in targets) {
            val t = target.lowercase()
            if (desc.contains(t) || text.contains(t)) {
                resultList.add(node)
                break
            }
        }
        for (i in 0 until node.childCount) {
            buscarBotonesSospechosos(node.getChild(i), targets, resultList)
        }
    }

    private fun sacarUsuarioDeApp() {
        Log.e(tag, "⚠️ EJECUTANDO CIERRE: Enviando usuario a Home/Back para proteger la app.")
        performGlobalAction(GLOBAL_ACTION_HOME)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or 
                         AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = AccessibilityServiceInfo.DEFAULT or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        this.serviceInfo = info
        
        // Iniciamos el servicio de persistencia desde aquí, 
        // ya que el AccessibilityService tiene permisos privilegiados de ejecución.
        try {
            val intent = android.content.Intent(this, FocusService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(tag, "No se pudo iniciar FocusService desde Accessibility: ${e.message}")
        }

        Log.i(tag, "✅ Servicio Focus Base Activo (Modo Ultra)")
    }
}