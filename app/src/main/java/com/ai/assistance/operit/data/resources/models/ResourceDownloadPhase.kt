package com.ai.assistance.operit.data.resources.models

/** 网络传输与本地校验安装分开计时，避免把解包吞吐量当成网速。 */
enum class ResourceDownloadPhase { CONNECTING, DOWNLOADING, INSTALLING }
