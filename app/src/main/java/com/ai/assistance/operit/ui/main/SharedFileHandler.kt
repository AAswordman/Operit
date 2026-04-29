package com.ai.assistance.operit.ui.main

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton to handle shared files from external apps
 * Manages the state flow of shared file URIs between MainActivity and AIChatScreen
 */
object SharedFileHandler {
    private val _sharedFiles = MutableStateFlow<List<Uri>?>(null)
    val sharedFiles: StateFlow<List<Uri>?> = _sharedFiles
    private val _sharedLinks = MutableStateFlow<List<String>?>(null)
    val sharedLinks: StateFlow<List<String>?> = _sharedLinks
    private val _lifeCareMessage = MutableStateFlow<String?>(null)
    val lifeCareMessage: StateFlow<String?> = _lifeCareMessage
    
    /**
     * Set the shared files to be processed
     */
    fun setSharedFiles(uris: List<Uri>) {
        _sharedFiles.value = uris
    }

    fun setSharedLinks(urls: List<String>) {
        _sharedLinks.value = urls
    }
    
    /**
     * Clear the shared files after processing
     */
    fun clearSharedFiles() {
        _sharedFiles.value = null
    }

    fun clearSharedLinks() {
        _sharedLinks.value = null
    }

    fun setLifeCareMessage(message: String) {
        _lifeCareMessage.value = message
    }

    fun clearLifeCareMessage() {
        _lifeCareMessage.value = null
    }
}

