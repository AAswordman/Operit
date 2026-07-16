package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.NotificationManager
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.services.accessibility.OperitAccessibilityService
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.*

/**
 * Gestor de WhatsApp AI - Versión Completa
 * Interactúa con WhatsApp de forma completamente automática usando AccessibilityService
 */
class StandardWhatsAppTools(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppTools"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Enviar mensaje completamente automático
     * Abre WhatsApp, busca contacto, escribe mensaje y envía
     */
    fun sendMessageAutomatic(tool: AITool): ToolResult {
        val contactName = tool.parameters.find { it.name == "contact_name" }?.value?.trim().orEmpty()
        val message = tool.parameters.find { it.name == "message" }?.value?.trim().orEmpty()

        if (contactName.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: contact_name")
        }
        if (message.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: message")
        }

        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado. Ve a Ajustes → Accesibilidad → Operit"
                )
            }

            // Ejecutar en coroutine
            var result: Result<String>? = null
            runBlocking {
                result = OperitAccessibilityService.sendWhatsAppMessageToContact(
                    contactName, message, context
                )
            }

            result?.fold(
                onSuccess = { msg ->
                    ToolResult(toolName = tool.name, success = true, result = StringResultData(msg))
                },
                onFailure = { e ->
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message ?: "Error desconocido")
                }
            ) ?: ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error desconocido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Enviar mensaje a número específico completamente automático
     */
    fun sendMessageToNumberAutomatic(tool: AITool): ToolResult {
        val number = tool.parameters.find { it.name == "number" }?.value?.trim().orEmpty()
        val message = tool.parameters.find { it.name == "message" }?.value?.trim().orEmpty()

        if (number.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: number")
        }

        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado"
                )
            }

            var result: Result<String>? = null
            runBlocking {
                result = OperitAccessibilityService.sendWhatsAppMessageToNumber(
                    number, message, context
                )
            }

            result?.fold(
                onSuccess = { msg ->
                    ToolResult(toolName = tool.name, success = true, result = StringResultData(msg))
                },
                onFailure = { e ->
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message ?: "Error desconocido")
                }
            ) ?: ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error desconocido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Abrir chat específico en WhatsApp
     */
    fun openChat(tool: AITool): ToolResult {
        val contactName = tool.parameters.find { it.name == "contact_name" }?.value?.trim().orEmpty()

        if (contactName.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: contact_name")
        }

        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                // Fallback: abrir WhatsApp normal
                val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ToolResult(toolName = tool.name, success = true, result = StringResultData("WhatsApp abierto. Busca el chat de: $contactName"))
                }
                return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "WhatsApp no está instalado")
            }

            // Abrir WhatsApp y buscar contacto
            runBlocking {
                OperitAccessibilityService.openWhatsApp(context)
                delay(1500)
                val found = OperitAccessibilityService.searchWhatsAppContact(contactName)
                if (found) {
                    ToolResult(toolName = tool.name, success = true, result = StringResultData("Chat abierto: $contactName"))
                } else {
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "No se encontró el contacto: $contactName")
                }
            }
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Obtener notificaciones de WhatsApp
     */
    fun getWhatsAppNotifications(tool: AITool): ToolResult {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = notificationManager.activeNotifications

            val whatsappNotifications = notifications.filter { notification ->
                notification.packageName == WHATSAPP_PACKAGE || notification.packageName == WHATSAPP_BUSINESS_PACKAGE
            }

            if (whatsappNotifications.isEmpty()) {
                return ToolResult(toolName = tool.name, success = true, result = StringResultData("No hay notificaciones de WhatsApp"))
            }

            val info = buildString {
                appendLine("=== Notificaciones de WhatsApp (${whatsappNotifications.size}) ===")
                whatsappNotifications.forEach { notification ->
                    val timestamp = notification.postTime
                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(timestamp))
                    
                    // Extraer texto de la notificación
                    val extras = notification.notification?.extras
                    val title = extras?.getCharSequence("android.title")?.toString() ?: ""
                    val text = extras?.getCharSequence("android.text")?.toString() ?: ""
                    
                    appendLine("\n📩 Notificación de WhatsApp ($timeStr)")
                    if (title.isNotBlank()) appendLine("   De: $title")
                    if (text.isNotBlank()) appendLine("   Mensaje: $text")
                    appendLine("   ID: ${notification.id}")
                }
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: SecurityException) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Permiso de notificaciones no concedido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Contar mensajes no leídos de WhatsApp
     */
    fun getUnreadCount(tool: AITool): ToolResult {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = notificationManager.activeNotifications

            val whatsappNotifications = notifications.filter { notification ->
                notification.packageName == WHATSAPP_PACKAGE || notification.packageName == WHATSAPP_BUSINESS_PACKAGE
            }

            val info = buildString {
                appendLine("=== WhatsApp No Leídos ===")
                appendLine("Notificaciones activas: ${whatsappNotifications.size}")

                if (whatsappNotifications.isNotEmpty()) {
                    appendLine("\nMensajes recientes:")
                    whatsappNotifications.take(10).forEach { notification ->
                        val extras = notification.notification?.extras
                        val title = extras?.getCharSequence("android.title")?.toString() ?: "Desconocido"
                        val text = extras?.getCharSequence("android.text")?.toString() ?: ""
                        appendLine("• $title: $text")
                    }
                }
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: SecurityException) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Permiso de notificaciones no concedido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Verificar si WhatsApp está instalado
     */
    fun checkInstallation(tool: AITool): ToolResult {
        return try {
            val regularInstalled = isPackageInstalled(WHATSAPP_PACKAGE)
            val businessInstalled = isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)

            val info = buildString {
                appendLine("=== Estado de WhatsApp ===")
                appendLine("WhatsApp Regular: ${if (regularInstalled) "✅ Instalado" else "❌ No instalado"}")
                appendLine("WhatsApp Business: ${if (businessInstalled) "✅ Instalado" else "❌ No instalado"}")
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Abrir WhatsApp Business
     */
    fun openWhatsAppBusiness(tool: AITool): ToolResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult(toolName = tool.name, success = true, result = StringResultData("WhatsApp Business abierto"))
            } else {
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "WhatsApp Business no está instalado")
            }
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Enviar mensaje largo completamente automático
     */
    fun sendLongMessageAutomatic(tool: AITool): ToolResult {
        val number = tool.parameters.find { it.name == "number" }?.value?.trim().orEmpty()
        val message = tool.parameters.find { it.name == "message" }?.value?.trim().orEmpty()

        if (number.isBlank() || message.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: number o message")
        }

        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado"
                )
            }

            var result: Result<String>? = null
            runBlocking {
                result = OperitAccessibilityService.sendWhatsAppMessageToNumber(
                    number, message, context
                )
            }

            result?.fold(
                onSuccess = { msg ->
                    ToolResult(toolName = tool.name, success = true, result = StringResultData("$msg\nLongitud: ${message.length} caracteres"))
                },
                onFailure = { e ->
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message ?: "Error desconocido")
                }
            ) ?: ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error desconocido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Leer mensajes del chat actual (usando AccessibilityService)
     */
    fun readCurrentChat(tool: AITool): ToolResult {
        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado"
                )
            }

            if (!OperitAccessibilityService.isWhatsAppInForeground()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "WhatsApp no está abierto"
                )
            }

            val messages = OperitAccessibilityService.readCurrentChatMessages()
            val info = buildString {
                appendLine("=== Mensajes del chat actual ===")
                appendLine("Mensajes encontrados: ${messages.size}")
                appendLine()
                messages.take(20).forEach { msg ->
                    appendLine("• $msg")
                }
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Obtener chats recientes
     */
    fun getRecentChats(tool: AITool): ToolResult {
        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado"
                )
            }

            if (!OperitAccessibilityService.isWhatsAppInForeground()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "WhatsApp no está abierto"
                )
            }

            val chats = OperitAccessibilityService.getRecentChats()
            val info = buildString {
                appendLine("=== Chats recientes ===")
                appendLine("Chats encontrados: ${chats.size}")
                appendLine()
                chats.forEach { chat ->
                    val text = chat["text"] ?: ""
                    if (text.isNotBlank()) {
                        appendLine("• $text")
                    }
                }
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Obtener estado del servicio de accesibilidad
     */
    fun getAccessibilityStatus(tool: AITool): ToolResult {
        return try {
            val isEnabled = OperitAccessibilityService.isServiceRunning()
            val info = buildString {
                appendLine("=== Estado del Servicio de Accesibilidad ===")
                appendLine("Estado: ${if (isEnabled) "✅ Activado" else "❌ Desactivado"}")
                if (isEnabled) {
                    appendLine("\nCapacidades:")
                    appendLine("• Leer pantalla: ✅")
                    appendLine("• Hacer tap: ✅")
                    appendLine("• Escribir texto: ✅")
                    appendLine("• Hacer swipe: ✅")
                    appendLine("• Automatizar WhatsApp: ✅")
                } else {
                    appendLine("\nPara activar:")
                    appendLine("1. Ve a Ajustes → Accesibilidad")
                    appendLine("2. Busca 'Operit'")
                    appendLine("3. Actívalo")
                }
            }

            ToolResult(toolName = tool.name, success = true, result = StringResultData(info))
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Enviar mensaje de voz por WhatsApp
     * Graba audio del micrófono y lo envía como mensaje de voz
     */
    fun sendVoiceMessage(tool: AITool): ToolResult {
        val contactName = tool.parameters.find { it.name == "contact_name" }?.value?.trim().orEmpty()
        val durationSeconds = tool.parameters.find { it.name == "duration" }?.value?.trim()?.toIntOrNull() ?: 5
        val number = tool.parameters.find { it.name == "number" }?.value?.trim().orEmpty()

        if (contactName.isBlank() && number.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Falta: contact_name o number")
        }

        return try {
            val service = OperitAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Servicio de Accesibilidad no activado. Ve a Ajustes → Accesibilidad → Operit"
                )
            }

            // Verificar permiso de micrófono
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Permiso de micrófono no concedido. Ve a Ajustes → Apps → Operit → Permisos → Micrófono"
                )
            }

            // 1. Grabar audio del micrófono
            val audioFile = recordAudio(durationSeconds)
            if (audioFile == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No se pudo grabar audio. Verifica que el micrófono esté disponible"
                )
            }

            // 2. Abrir WhatsApp y enviar el audio
            var result: Result<String>? = null
            runBlocking {
                result = if (contactName.isNotBlank()) {
                    OperitAccessibilityService.sendWhatsAppVoiceMessageToContact(
                        contactName, audioFile, context
                    )
                } else {
                    OperitAccessibilityService.sendWhatsAppVoiceMessageToNumber(
                        number, audioFile, context
                    )
                }
            }

            result?.fold(
                onSuccess = { msg ->
                    ToolResult(toolName = tool.name, success = true, result = StringResultData(msg))
                },
                onFailure = { e ->
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message ?: "Error desconocido")
                }
            ) ?: ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error desconocido")
        } catch (e: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error: ${e.message}")
        }
    }

    /**
     * Grabar audio del micrófono
     */
    private fun recordAudio(durationSeconds: Int): java.io.File? {
        return try {
            val sampleRate = 16000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize <= 0) {
                AppLogger.e(TAG, "AudioRecord buffer size inválido: $minBufferSize")
                return null
            }

            val audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                AppLogger.e(TAG, "AudioRecord no se inicializó correctamente")
                audioRecord.release()
                return null
            }

            // Crear archivo temporal para el audio
            val audioFile = java.io.File(context.cacheDir, "whatsapp_voice_${System.currentTimeMillis()}.ogg")
            val bufferSize = minBufferSize
            val audioBuffer = ShortArray(bufferSize)

            audioRecord.startRecording()
            AppLogger.d(TAG, "Grabando audio por $durationSeconds segundos...")

            // Grabar audio durante la duración especificada
            val totalSamples = sampleRate * durationSeconds
            var samplesRecorded = 0

            java.io.FileOutputStream(audioFile).use { outputStream ->
                while (samplesRecorded < totalSamples) {
                    val samplesToRead = minOf(bufferSize, totalSamples - samplesRecorded)
                    val bytesRead = audioRecord.read(audioBuffer, 0, samplesToRead)
                    if (bytesRead > 0) {
                        // Convertir ShortArray a ByteArray para escritura
                        val byteBuffer = java.nio.ByteBuffer.allocate(bytesRead * 2)
                        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        byteBuffer.asShortBuffer().put(audioBuffer, 0, bytesRead)
                        outputStream.write(byteBuffer.array())
                        samplesRecorded += bytesRead
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()

            AppLogger.d(TAG, "Audio grabado: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
            audioFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error al grabar audio: ${e.message}", e)
            null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}