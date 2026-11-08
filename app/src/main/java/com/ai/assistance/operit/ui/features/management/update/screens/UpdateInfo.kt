package com.ai.assistance.operit.ui.features.management.update.screens

data class UpdateInfo(
    val version: String,
    val date: String,
    val title: String,
    val description: String,
    val highlights: List<String>,
    val allChanges: List<String>,
    val isLatest: Boolean = false,
    val downloadUrl: String = "",
    val releaseUrl: String = ""
)
