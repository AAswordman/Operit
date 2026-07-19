package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.R

data class OpenSourceLibrary(
    val name: String,
    val description: String = "",
    val license: String = "",
    val website: String = ""
)

private fun getOpenSourceLibraries(): List<OpenSourceLibrary> {
    return listOf(
        // UI & Android Framework
        OpenSourceLibrary("android-gif-drawable", "GIF support for Android", "MIT", "https://github.com/koral--/android-gif-drawable"),
        OpenSourceLibrary("Android-Image-Cropper", "Image cropping library for Android", "Apache-2.0", "https://github.com/CanHub/Android-Image-Cropper"),
        OpenSourceLibrary("AndroidSVG", "SVG rendering library", "Apache-2.0", "https://github.com/BigBadaboom/androidsvg"),
        OpenSourceLibrary("AndroidX Compose", "Modern declarative UI toolkit for Android", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("AndroidX Core KTX", "Kotlin extensions for Android core libraries", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("AndroidX DataStore", "Data storage solution", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
        OpenSourceLibrary("AndroidX Security Crypto", "Encryption library for Android", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/security"),
        OpenSourceLibrary("AndroidX Window", "Window manager library for foldables", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/window"),
        OpenSourceLibrary("AndroidX WorkManager", "Background job scheduling library", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/work"),
        OpenSourceLibrary("AndroidX Glance", "Compose for App Widgets", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/glance"),
        OpenSourceLibrary("Accompanist", "Utilities for Jetpack Compose", "Apache-2.0", "https://github.com/google/accompanist"),
        OpenSourceLibrary("colorpicker-compose", "A color picker for Jetpack Compose", "Apache-2.0", "https://github.com/skydoves/colorpicker-compose"),
        OpenSourceLibrary("Reorderable", "Drag-and-drop reorderable list for Compose", "Apache-2.0", "https://github.com/Calvin-LL/Reorderable"),
        OpenSourceLibrary("Swipe", "Swipe-to-reveal actions for Compose", "Apache-2.0", "https://github.com/saket/swipe"),

        // File & Archive Processing
        OpenSourceLibrary("Apache Commons Compress", "Library for working with archives", "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
        OpenSourceLibrary("Apache Commons IO", "Library of I/O utilities", "Apache-2.0", "https://commons.apache.org/proper/commons-io/"),
        OpenSourceLibrary("junrar", "RAR archive extraction library", "The Unlicense", "https://github.com/junrar/junrar"),
        OpenSourceLibrary("ZIP4J", "Java library for ZIP file handling", "Apache-2.0", "https://github.com/srikanth-lingala/zip4j"),

        // Document Processing
        OpenSourceLibrary("Apache PDFBox", "Java library for working with PDF documents", "Apache-2.0", "https://pdfbox.apache.org/"),
        OpenSourceLibrary("Apache POI", "Document processing library (Excel, Word, PowerPoint)", "Apache-2.0", "https://poi.apache.org/"),
        OpenSourceLibrary("iText (v5)", "Library for creating and manipulating PDF files", "MPL/LGPL", "https://itextpdf.com/"),

        // APK Tools
        OpenSourceLibrary("apk-parser", "A parser for APK files", "Apache-2.0", "https://github.com/hsiafan/apk-parser"),
        OpenSourceLibrary("apksig", "APK signing tool", "Apache-2.0", "https://developer.android.com/tools/apksigner"),
        OpenSourceLibrary("axml", "A-XML format parsing library", "Apache-2.0", "https://github.com/Sable/axml"),
        OpenSourceLibrary("zipalign-java", "zipalign implementation in Java", "MIT", "https://github.com/Iyxan23/zipalign-java"),

        // Image Processing
        OpenSourceLibrary("Coil", "Image loading library for Android", "Apache-2.0", "https://coil-kt.github.io/coil/"),
        OpenSourceLibrary("Glide", "Image loading library for Android", "BSD, MIT, Apache-2.0", "https://github.com/bumptech/glide"),

        // Multimedia
        OpenSourceLibrary("ExoPlayer", "Extensible media player for Android", "Apache-2.0", "https://exoplayer.dev/"),
        OpenSourceLibrary("FFmpegKit", "FFmpeg toolkit for mobile platforms", "LGPL-3.0", "https://github.com/arthenica/ffmpeg-kit"),

        // AI & Machine Learning
        OpenSourceLibrary("ML Kit", "Google's machine learning toolkit for mobile", "Apache-2.0", "https://developers.google.com/ml-kit"),
        OpenSourceLibrary("MediaPipe", "Cross-platform ML solutions", "Apache-2.0", "https://developers.google.com/mediapipe"),
        OpenSourceLibrary("MNN", "Alibaba's lightweight deep learning inference engine", "Apache-2.0", "https://github.com/alibaba/MNN"),
        OpenSourceLibrary("ONNX Runtime", "Cross-platform ML inference engine", "MIT", "https://github.com/microsoft/onnxruntime"),
        OpenSourceLibrary("TensorFlow Lite", "On-device machine learning framework", "Apache-2.0", "https://www.tensorflow.org/lite"),

        // NLP & Search
        OpenSourceLibrary("HNSWLib", "Fast approximate nearest neighbor search", "Apache-2.0", "https://github.com/jelmerk/hnswlib"),
        OpenSourceLibrary("Jieba-Android", "Jieba Chinese word segmentation for Android", "MIT", "https://github.com/huaban/jieba-analysis"),

        // Networking
        OpenSourceLibrary("Apache FTPServer", "FTP server library", "Apache-2.0", "https://mina.apache.org/ftpserver-project/"),
        OpenSourceLibrary("Apache SSHD", "SSH server and client library", "Apache-2.0", "https://mina.apache.org/sshd-project/"),
        OpenSourceLibrary("JSch", "Java SSH client library", "BSD-3-Clause", "https://github.com/mwiede/jsch"),
        OpenSourceLibrary("Jsoup", "Java HTML parser", "MIT", "https://jsoup.org/"),
        OpenSourceLibrary("Ktor", "Asynchronous networking framework", "Apache-2.0", "https://ktor.io/"),
        OpenSourceLibrary("MCP SDK", "Model Context Protocol SDK", "MIT", "https://github.com/modelcontextprotocol/kotlin-sdk"),
        OpenSourceLibrary("NanoHTTPD", "Lightweight HTTP server library", "BSD-3-Clause", "https://github.com/NanoHttpd/nanohttpd"),
        OpenSourceLibrary("OkHttp", "HTTP client library", "Apache-2.0", "https://square.github.io/okhttp/"),
        OpenSourceLibrary("Retrofit", "Type-safe HTTP client", "Apache-2.0", "https://square.github.io/retrofit/"),

        // Data & Serialization
        OpenSourceLibrary("Gson", "Google's JSON parsing library", "Apache-2.0", "https://github.com/google/gson"),
        OpenSourceLibrary("HJSON", "Human-friendly JSON format", "MIT", "https://hjson.github.io/"),
        OpenSourceLibrary("kotlin-uuid", "UUID library for Kotlin", "Apache-2.0", "https://github.com/benasher44/uuid"),
        OpenSourceLibrary("kotlinx.serialization", "Kotlin serialization library", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OpenSourceLibrary("Moshi", "Modern JSON library for Kotlin", "Apache-2.0", "https://github.com/square/moshi"),

        // Database
        OpenSourceLibrary("ObjectBox", "High-performance NoSQL database", "Apache-2.0", "https://objectbox.io/"),
        OpenSourceLibrary("Room", "Android SQLite ORM library", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),

        // LaTeX & Math Rendering
        OpenSourceLibrary("JLatexMath-Android", "LaTeX formula rendering library", "GPL-2.0 with Classpath Exception", "https://github.com/noties/jlatexmath-android"),
        OpenSourceLibrary("KaTeX", "LaTeX to MathML parser used for plain-text formula export", "MIT", "https://github.com/KaTeX/KaTeX"),
        OpenSourceLibrary("RenderX", "LaTeX rendering library", "MIT", "https://github.com/tech-pw/RenderX"),

        // Security & Crypto
        OpenSourceLibrary("Bouncy Castle", "Cryptography library", "MIT", "https://www.bouncycastle.org/"),

        // System & Root
        OpenSourceLibrary("libsu", "Root access library for Android", "Apache-2.0", "https://github.com/topjohnwu/libsu"),
        OpenSourceLibrary("Shizuku", "System service for apps to use system APIs directly", "Apache-2.0", "https://github.com/RikkaApps/Shizuku"),
        OpenSourceLibrary("Tasker Plugin Library", "Library for creating Tasker plugins", "Apache-2.0", "https://github.com/joaomgcd/TaskerPluginLibrary"),

        // Terminal & Native
        OpenSourceLibrary("Code FA", "VS Code for Android (code-server based)", "BSD-3-Clause", "https://github.com/nightmare-space/code_lfa"),
        OpenSourceLibrary("DragonBones", "Popular 2D skeletal animation library", "MIT", "https://github.com/DragonBones/DragonBonesCPP"),
        OpenSourceLibrary("scrcpy", "Display and control Android devices", "Apache-2.0", "https://github.com/Genymobile/scrcpy"),

        // Kotlin & Logging
        OpenSourceLibrary("java-diff-utils", "Diff library for Java", "Apache-2.0", "https://github.com/java-diff-utils/java-diff-utils"),
        OpenSourceLibrary("Kotlin Coroutines", "Kotlin coroutines library", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceLibrary("kotlin-logging", "Lightweight logging framework for Kotlin", "Apache-2.0", "https://github.com/oshai/kotlin-logging"),
        OpenSourceLibrary("sherpa-ncnn", "Real-time speech recognition with Next-gen Kaldi", "Apache-2.0", "https://github.com/k2-fsa/sherpa-ncnn"),
        OpenSourceLibrary("sherpa-mnn", "Speech recognition with MNN backend", "Apache-2.0", "https://github.com/k2-fsa/sherpa-mnn"),
        OpenSourceLibrary("SLF4J", "Simple Logging Facade for Java", "MIT", "https://www.slf4j.org/")
    ).sortedBy { it.name }
}

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val libraries = remember { getOpenSourceLibraries() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.open_source_licenses)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(libraries) { library ->
                    ListItem(
                        headlineContent = { Text(library.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Column {
                                if (library.description.isNotEmpty()) {
                                    Text(
                                        text = library.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = stringResource(id = R.string.license_format, library.license),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        },
                        trailingContent = {
                            if (library.website.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(library.website)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = stringResource(id = R.string.visit_project)
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(id = R.string.ok))
            }
        }
    )
}
