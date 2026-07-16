package com.ai.assistance.operit.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.*

/**
 * Servicio de Accesibilidad principal de Operit
 * Permite leer la pantalla, hacer tap, escribir texto, etc.
 * Ahora con soporte completo para automatización de WhatsApp
 */
class OperitAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OperitAccessibility"
        private var instance: OperitAccessibilityService? = null
        private var isRunning = false

        fun getInstance(): OperitAccessibilityService? = instance

        fun isServiceRunning(): Boolean = isRunning

        // ==================== MÉTODOS BÁSICOS ====================

        /**
         * Obtener la jerarquía UI actual como XML
         */
        fun getCurrentUIHierarchy(): String? {
            return instance?.let { service ->
                val rootNode = service.rootInActiveWindow
                rootNode?.let { node ->
                    val sb = StringBuilder()
                    dumpNode(node, sb, 0)
                    sb.toString()
                }
            }
        }

        private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
            val indent = "  ".repeat(depth)
            val rect = Rect()
            node.getBoundsInScreen(rect)
            sb.appendLine("$indent<node class=\"${node.className}\"" +
                    " text=\"${node.text}\"" +
                    " content-desc=\"${node.contentDescription}\"" +
                    " bounds=\"$rect\"" +
                    " clickable=\"${node.isClickable}\"" +
                    " enabled=\"${node.isEnabled}\"" +
                    " focused=\"${node.isFocused}\"" +
                    " id=\"${node.viewIdResourceName}\" />")

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                dumpNode(child, sb, depth + 1)
                child.recycle()
            }
        }

        /**
         * Realizar tap en coordenadas
         */
        fun performTap(x: Int, y: Int): Boolean {
            return instance?.let { service ->
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                service.dispatchGesture(
                    GestureDescription.Builder()
                        .addStroke(
                            GestureDescription.StrokeDescription(path, 0, 100)
                        )
                        .build(),
                    null,
                    null
                )
                true
            } ?: false
        }

        /**
         * Realizar swipe
         */
        fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 300): Boolean {
            return instance?.let { service ->
                val path = Path()
                path.moveTo(startX.toFloat(), startY.toFloat())
                path.lineTo(endX.toFloat(), endY.toFloat())
                service.dispatchGesture(
                    GestureDescription.Builder()
                        .addStroke(
                            GestureDescription.StrokeDescription(path, 0, duration)
                        )
                        .build(),
                    null,
                    null
                )
                true
            } ?: false
        }

        /**
         * Presionar tecla global (Back, Home, Recents)
         */
        fun performGlobalAction(action: Int): Boolean {
            return instance?.performGlobalAction(action) ?: false
        }

        /**
         * Encontrar nodo por texto
         */
        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            return instance?.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
        }

        /**
         * Encontrar nodo por ID
         */
        fun findNodeById(viewId: String): AccessibilityNodeInfo? {
            return instance?.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
        }

        // ==================== MÉTODOS PARA AUTOMATIZACIÓN ====================

        /**
         * Hacer tap en un nodo específico
         */
        fun performTapOnNode(node: AccessibilityNodeInfo): Boolean {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            return performTap(centerX, centerY)
        }

        /**
         * Escribir texto en un nodo de entrada
         */
        fun setTextOnNode(node: AccessibilityNodeInfo, text: String): Boolean {
            // Intentar usar ACTION_SET_TEXT
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        /**
         * Hacer clic en un nodo
         */
        fun clickNode(node: AccessibilityNodeInfo): Boolean {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        /**
         * Buscar nodo por texto (case-insensitive)
         */
        fun findNodeByTextIgnoreCase(text: String): AccessibilityNodeInfo? {
            val rootNode = instance?.rootInActiveWindow ?: return null
            return findNodeRecursive(rootNode, text, ignoreCase = true)
        }

        private fun findNodeRecursive(
            node: AccessibilityNodeInfo,
            text: String,
            ignoreCase: Boolean
        ): AccessibilityNodeInfo? {
            val nodeText = node.text?.toString() ?: ""
            val matches = if (ignoreCase) {
                nodeText.contains(text, ignoreCase = true)
            } else {
                nodeText.contains(text)
            }

            if (matches) return node

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeRecursive(child, text, ignoreCase)
                if (result != null) return result
                child.recycle()
            }

            return null
        }

        /**
         * Buscar nodo por contenido de descripción
         */
        fun findNodeByContentDescription(text: String): AccessibilityNodeInfo? {
            val rootNode = instance?.rootInActiveWindow ?: return null
            return findNodeByDescRecursive(rootNode, text)
        }

        private fun findNodeByDescRecursive(
            node: AccessibilityNodeInfo,
            text: String
        ): AccessibilityNodeInfo? {
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.contains(text, ignoreCase = true)) return node

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeByDescRecursive(child, text)
                if (result != null) return result
                child.recycle()
            }

            return null
        }

        /**
         * Obtener todos los nodos que coinciden con un texto
         */
        fun findAllNodesByText(text: String): List<AccessibilityNodeInfo> {
            val rootNode = instance?.rootInActiveWindow ?: return emptyList()
            val results = mutableListOf<AccessibilityNodeInfo>()
            findAllNodesRecursive(rootNode, text, results)
            return results
        }

        private fun findAllNodesRecursive(
            node: AccessibilityNodeInfo,
            text: String,
            results: MutableList<AccessibilityNodeInfo>
        ) {
            val nodeText = node.text?.toString() ?: ""
            if (nodeText.contains(text, ignoreCase = true)) {
                results.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findAllNodesRecursive(child, text, results)
                child.recycle()
            }
        }

        /**
         * Esperar a que aparezca un nodo en pantalla
         */
        suspend fun waitForNode(
            text: String,
            timeoutMs: Long = 10000,
            intervalMs: Long = 500
        ): AccessibilityNodeInfo? {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val node = findNodeByTextIgnoreCase(text)
                if (node != null) return node
                delay(intervalMs)
            }
            return null
        }

        /**
         * Obtener el paquete de la app en primer plano
         */
        fun getCurrentForegroundPackage(): String? {
            val rootNode = instance?.rootInActiveWindow ?: return null
            return rootNode.packageName?.toString()
        }

        // ==================== AUTOMATIZACIÓN DE WHATSAPP ====================

        /**
         * Abrir WhatsApp
         */
        fun openWhatsApp(context: Context): Boolean {
            return try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Buscar contacto en WhatsApp
         */
        suspend fun searchWhatsAppContact(
            contactName: String,
            timeoutMs: Long = 10000
        ): Boolean {
            // Esperar a que cargue WhatsApp
            delay(1000)

            // Buscar el botón de búsqueda (icono de lupa)
            val searchButton = findNodeByContentDescription("Buscar")
                ?: findNodeByContentDescription("Search")
                ?: findNodeById("com.whatsapp:id/menuitem_search")

            if (searchButton != null) {
                clickNode(searchButton)
                delay(500)
            }

            // Buscar campo de búsqueda
            val searchField = waitForNode("Buscar", timeoutMs = 3000)
                ?: findNodeById("com.whatsapp:id/search_src_text")

            if (searchField != null) {
                setTextOnNode(searchField, contactName)
                delay(1000)

                // Buscar el contacto en los resultados
                val contactNode = waitForNode(contactName, timeoutMs = 5000)
                if (contactNode != null) {
                    clickNode(contactNode)
                    delay(500)
                    return true
                }
            }

            return false
        }

        /**
         * Escribir mensaje en el campo de texto de WhatsApp
         */
        suspend fun typeWhatsAppMessage(
            message: String,
            timeoutMs: Long = 5000
        ): Boolean {
            // Buscar campo de mensaje
            val messageField = waitForNode("Escribe un mensaje", timeoutMs = timeoutMs)
                ?: waitForNode("Type a message", timeoutMs = 1000)
                ?: findNodeById("com.whatsapp:id/entry")

            if (messageField != null) {
                clickNode(messageField)
                delay(300)
                setTextOnNode(messageField, message)
                delay(500)
                return true
            }

            return false
        }

        /**
         * Presionar botón de enviar
         */
        suspend fun pressSendButton(timeoutMs: Long = 3000): Boolean {
            // Buscar botón de enviar
            val sendButton = findNodeByContentDescription("Enviar")
                ?: findNodeByContentDescription("Send")
                ?: findNodeById("com.whatsapp:id/send")

            if (sendButton != null) {
                clickNode(sendButton)
                delay(300)
                return true
            }

            return false
        }

        /**
         * Enviar mensaje completo a contacto
         */
        suspend fun sendWhatsAppMessageToContact(
            contactName: String,
            message: String,
            context: Context
        ): Result<String> {
            return try {
                // 1. Abrir WhatsApp
                if (!openWhatsApp(context)) {
                    return Result.failure(Exception("No se pudo abrir WhatsApp"))
                }
                delay(1500)

                // 2. Buscar contacto
                if (!searchWhatsAppContact(contactName)) {
                    return Result.failure(Exception("No se encontró el contacto: $contactName"))
                }
                delay(500)

                // 3. Escribir mensaje
                if (!typeWhatsAppMessage(message)) {
                    return Result.failure(Exception("No se pudo escribir el mensaje"))
                }
                delay(500)

                // 4. Presionar enviar
                if (!pressSendButton()) {
                    return Result.failure(Exception("No se pudo presionar enviar"))
                }
                delay(500)

                Result.success("Mensaje enviado a $contactName: $message")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Enviar mensaje a número específico
         */
        suspend fun sendWhatsAppMessageToNumber(
            number: String,
            message: String,
            context: Context
        ): Result<String> {
            return try {
                // Limpiar número
                val cleanNumber = number.replace(Regex("[^0-9+]"), "")

                // Abrir WhatsApp directamente con el número
                val intent = Intent(Intent.ACTION_VIEW)
                val url = if (message.isNotBlank()) {
                    "https://wa.me/$cleanNumber?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                } else {
                    "https://wa.me/$cleanNumber"
                }
                intent.data = android.net.Uri.parse(url)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                delay(2000)

                // Si hay mensaje, escribirlo y enviar
                if (message.isNotBlank()) {
                    if (typeWhatsAppMessage(message)) {
                        delay(500)
                        pressSendButton()
                    }
                }

                Result.success("Mensaje enviado al número: $cleanNumber")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Enviar mensaje de voz a contacto específico
         */
        suspend fun sendWhatsAppVoiceMessageToContact(
            contactName: String,
            audioFile: java.io.File,
            context: Context
        ): Result<String> {
            return try {
                // 1. Abrir WhatsApp
                if (!openWhatsApp(context)) {
                    return Result.failure(Exception("No se pudo abrir WhatsApp"))
                }
                delay(1500)

                // 2. Buscar contacto
                if (!searchWhatsAppContact(contactName)) {
                    return Result.failure(Exception("No se encontró el contacto: $contactName"))
                }
                delay(500)

                // 3. Adjuntar archivo de audio
                if (!attachAudioFile(audioFile)) {
                    return Result.failure(Exception("No se pudo adjuntar el archivo de audio"))
                }
                delay(500)

                // 4. Enviar
                if (!pressSendButton()) {
                    return Result.failure(Exception("No se pudo presionar enviar"))
                }
                delay(500)

                Result.success("Mensaje de voz enviado a $contactName")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Enviar mensaje de voz a número específico
         */
        suspend fun sendWhatsAppVoiceMessageToNumber(
            number: String,
            audioFile: java.io.File,
            context: Context
        ): Result<String> {
            return try {
                // Limpiar número
                val cleanNumber = number.replace(Regex("[^0-9+]"), "")

                // Abrir WhatsApp directamente con el número
                val intent = Intent(Intent.ACTION_VIEW)
                val url = "https://wa.me/$cleanNumber"
                intent.data = android.net.Uri.parse(url)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                delay(2000)

                // Adjuntar archivo de audio
                if (!attachAudioFile(audioFile)) {
                    return Result.failure(Exception("No se pudo adjuntar el archivo de audio"))
                }
                delay(500)

                // Enviar
                if (!pressSendButton()) {
                    return Result.failure(Exception("No se pudo presionar enviar"))
                }
                delay(500)

                Result.success("Mensaje de voz enviado al número: $cleanNumber")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Adjuntar archivo de audio en WhatsApp
         */
        private suspend fun attachAudioFile(audioFile: java.io.File): Boolean {
            try {
                // Buscar botón de adjuntar (clip de papel)
                val attachButton = findNodeByContentDescription("Adjuntar")
                    ?: findNodeByContentDescription("Attach")
                    ?: findNodeById("com.whatsapp:id/menuitem_attach")

                if (attachButton != null) {
                    clickNode(attachButton)
                    delay(1000)

                    // Buscar opción de audio/archivo
                    val audioOption = waitForNode("Audio", timeoutMs = 2000)
                        ?: waitForNode("Documento", timeoutMs = 1000)
                        ?: waitForNode("Document", timeoutMs = 1000)

                    if (audioOption != null) {
                        clickNode(audioOption)
                        delay(1000)

                        // Aquí necesitaríamos usar SAF para seleccionar el archivo
                        // Por ahora, usamos un intent para compartir el archivo
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(audioFile))
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        this@OperitAccessibilityService.startActivity(shareIntent)
                        delay(1500)
                        return true
                    }
                }

                // Alternativa: usar el botón de micrófono y grabar directamente
                val micButton = findNodeByContentDescription("Mensaje de voz")
                    ?: findNodeByContentDescription("Voice message")
                    ?: findNodeById("com.whatsapp:id/entry")

                if (micButton != null) {
                    // Simular mantener presionado el botón de micrófono
                    val rect = android.graphics.Rect()
                    micButton.getBoundsInScreen(rect)
                    val centerX = rect.centerX()
                    val centerY = rect.centerY()

                    // Realizar long press
                    val path = android.graphics.Path()
                    path.moveTo(centerX.toFloat(), centerY.toFloat())
                    instance?.dispatchGesture(
                        android.accessibilityservice.GestureDescription.Builder()
                            .addStroke(
                                android.accessibilityservice.GestureDescription.StrokeDescription(
                                    path, 0, 5000 // 5 segundos de grabación
                                )
                            )
                            .build(),
                        null,
                        null
                    )
                    delay(5500)
                    return true
                }

                return false
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error al adjuntar audio: ${e.message}", e)
                return false
            }
        }

        /**
         * Leer mensajes del chat actual
         */
        fun readCurrentChatMessages(): List<String> {
            val rootNode = instance?.rootInActiveWindow ?: return emptyList()
            val messages = mutableListOf<String>()

            // Buscar nodos de mensajes (en WhatsApp suelen tener la clase RecyclerView)
            findMessagesRecursive(rootNode, messages)

            return messages
        }

        private fun findMessagesRecursive(
            node: AccessibilityNodeInfo,
            messages: MutableList<String>
        ) {
            // Los mensajes de WhatsApp suelen estar en nodos con texto
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length > 1) {
                // Filtrar elementos de UI comunes
                if (!text.matches(Regex("^(WhatsApp|Buscar|Enviar|Adjuntar|...)$"))) {
                    messages.add(text)
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findMessagesRecursive(child, messages)
                child.recycle()
            }
        }

        /**
         * Obtener información del chat actual
         */
        fun getCurrentChatInfo(): Map<String, String> {
            val info = mutableMapOf<String, String>()
            val rootNode = instance?.rootInActiveWindow ?: return info

            // Buscar nombre del contacto
            val contactName = findNodeById("com.whatsapp:id/contact")
            if (contactName != null) {
                info["contact"] = contactName.text?.toString() ?: ""
            }

            // Buscar estado del contacto
            val status = findNodeById("com.whatsapp:id/status")
            if (status != null) {
                info["status"] = status.text?.toString() ?: ""
            }

            return info
        }

        /**
         * Verificar si WhatsApp está en primer plano
         */
        fun isWhatsAppInForeground(): Boolean {
            return getCurrentForegroundPackage() == "com.whatsapp"
        }

        /**
         * Obtener lista de chats recientes
         */
        fun getRecentChats(): List<Map<String, String>> {
            val rootNode = instance?.rootInActiveWindow ?: return emptyList()
            val chats = mutableListOf<Map<String, String>>()

            // Buscar lista de chats
            findChatsRecursive(rootNode, chats)

            return chats.take(20) // Limitar a 20 chats
        }

        private fun findChatsRecursive(
            node: AccessibilityNodeInfo,
            chats: MutableList<Map<String, String>>
        ) {
            // Los chats suelen tener estructura de lista
            if (node.childCount > 3) {
                // Podría ser un contenedor de chats
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val chatInfo = extractChatInfo(child)
                    if (chatInfo.isNotEmpty()) {
                        chats.add(chatInfo)
                    }
                    child.recycle()
                }
            } else {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    findChatsRecursive(child, chats)
                    child.recycle()
                }
            }
        }

        private fun extractChatInfo(node: AccessibilityNodeInfo): Map<String, String> {
            val info = mutableMapOf<String, String>()
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                info["text"] = text
            }
            return info
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true

        // Configurar el servicio
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }

        AppLogger.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos procesar eventos aquí por ahora
        // El servicio está listo para ser consultado
    }

    override fun onInterrupt() {
        AppLogger.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        isRunning = false
        AppLogger.d(TAG, "Accessibility Service destroyed")
        super.onDestroy()
    }
}