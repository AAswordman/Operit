import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
    id("kotlin-kapt")
}

// 此独立发行分支在 GitHub Actions 中生成固定签名的 Kong 共存包。
val ciPreviewRelease = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"
val ciDistributionCertificateSha256 = "5E01FFF4DD01A44BAB8318BE35107AE39C60A1261CFCE33003EA3528727EA799"

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

data class SttModelAsset(
    val targetPath: String,
    val sourceUrl: String,
    val expectedBytes: Long,
    val expectedSha256: String,
)

data class ApkRotationSigningConfig(
    val apksigner: File,
    val oldStoreFile: File,
    val oldStorePassword: String,
    val oldKeyAlias: String,
    val oldKeyPassword: String,
    val newStoreFile: File,
    val newStorePassword: String,
    val newKeyAlias: String,
    val newKeyPassword: String,
    val lineageFile: File,
)

fun requiredLocalProperty(name: String): String {
    val value = localProperties.getProperty(name)?.trim()
    require(!value.isNullOrEmpty()) {
        "local.properties must define $name for Release/Nightly APK rotation signing"
    }
    return value
}

fun configuredFileProperty(name: String): File {
    val configuredPath = File(requiredLocalProperty(name))
    val resolvedPath = if (configuredPath.isAbsolute) configuredPath else rootProject.file(configuredPath)
    require(resolvedPath.isFile) {
        "Configured $name does not point to a file: ${resolvedPath.path}"
    }
    return resolvedPath
}

fun loadApkRotationSigningConfig(): ApkRotationSigningConfig {
    val sdkDirectory = File(requiredLocalProperty("sdk.dir"))
    require(sdkDirectory.isDirectory) {
        "Configured sdk.dir does not point to a directory: ${sdkDirectory.path}"
    }

    val apksignerName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "apksigner.bat"
    } else {
        "apksigner"
    }
    val apksigner = sdkDirectory.resolve("build-tools/35.0.0/$apksignerName")
    require(apksigner.isFile) {
        "Android build-tools 35.0.0 apksigner is required: ${apksigner.path}"
    }

    return ApkRotationSigningConfig(
        apksigner = apksigner,
        oldStoreFile = configuredFileProperty("RELEASE_STORE_FILE"),
        oldStorePassword = requiredLocalProperty("RELEASE_STORE_PASSWORD"),
        oldKeyAlias = requiredLocalProperty("RELEASE_KEY_ALIAS"),
        oldKeyPassword = requiredLocalProperty("RELEASE_KEY_PASSWORD"),
        newStoreFile = configuredFileProperty("APK_ROTATION_NEW_STORE_FILE"),
        newStorePassword = requiredLocalProperty("APK_ROTATION_NEW_STORE_PASSWORD"),
        newKeyAlias = requiredLocalProperty("APK_ROTATION_NEW_KEY_ALIAS"),
        newKeyPassword = requiredLocalProperty("APK_ROTATION_NEW_KEY_PASSWORD"),
        lineageFile = configuredFileProperty("APK_ROTATION_LINEAGE_FILE"),
    )
}

fun signApkWithRotation(apkFile: File) {
    require(apkFile.isFile) { "APK to sign was not produced: ${apkFile.path}" }
    val config = loadApkRotationSigningConfig()
    val rotatedApk = apkFile.resolveSibling(".${apkFile.name}.rotation-signing")
    require(!rotatedApk.exists()) {
        "Refusing to overwrite an existing rotation signing output: ${rotatedApk.path}"
    }

    // API 28 is the first platform that selects V3 and understands proof-of-rotation;
    // API 26/27 therefore continue to select the old signer from the V2 block.
    val signingArguments = listOf(
        "sign",
        "--in", apkFile.path,
        "--out", rotatedApk.path,
        "--min-sdk-version", "26",
        "--v1-signing-enabled", "false",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--v4-signing-enabled", "false",
        "--lineage", config.lineageFile.path,
        "--rotation-min-sdk-version", "28",
        "--ks", config.oldStoreFile.path,
        "--ks-type", "PKCS12",
        "--ks-key-alias", config.oldKeyAlias,
        "--ks-pass", "env:OPERIT_OLD_STORE_PASSWORD",
        "--key-pass", "env:OPERIT_OLD_KEY_PASSWORD",
        "--next-signer",
        "--ks", config.newStoreFile.path,
        "--ks-type", "PKCS12",
        "--ks-key-alias", config.newKeyAlias,
        "--ks-pass", "env:OPERIT_NEW_STORE_PASSWORD",
        "--key-pass", "env:OPERIT_NEW_KEY_PASSWORD",
    )

    // 显式读取执行结果，签名命令失败时立即终止任务。
    providers.exec {
        commandLine(listOf(config.apksigner.path) + signingArguments)
        environment("OPERIT_OLD_STORE_PASSWORD", config.oldStorePassword)
        environment("OPERIT_OLD_KEY_PASSWORD", config.oldKeyPassword)
        environment("OPERIT_NEW_STORE_PASSWORD", config.newStorePassword)
        environment("OPERIT_NEW_KEY_PASSWORD", config.newKeyPassword)
    }.result.get().assertNormalExitValue()

    providers.exec {
        commandLine(config.apksigner.path, "verify", "--verbose", "--print-certs", rotatedApk.path)
    }.result.get().assertNormalExitValue()

    Files.move(
        rotatedApk.toPath(),
        apkFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
    )
}

val requiredExternallyBuiltNativeLibraries =
    listOf(
        file("src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so"),
    )

val ffmpegKitLocalAar = file("libs/ffmpeg-kit-local.aar")
val requiredFfmpegKitArm64Libraries =
    setOf(
        "jni/arm64-v8a/libavcodec.so",
        "jni/arm64-v8a/libavdevice.so",
        "jni/arm64-v8a/libavfilter.so",
        "jni/arm64-v8a/libavformat.so",
        "jni/arm64-v8a/libavutil.so",
        "jni/arm64-v8a/libc++_shared.so",
        "jni/arm64-v8a/libffmpegkit.so",
        "jni/arm64-v8a/libffmpegkit_abidetect.so",
        "jni/arm64-v8a/libswresample.so",
        "jni/arm64-v8a/libswscale.so",
    )

val verifyExternallyBuiltNativeLibraries by tasks.registering {
    description = "Checks native libraries built outside Gradle before Android packaging."
    group = "verification"
    inputs.property(
        "requiredLibraries",
        requiredExternallyBuiltNativeLibraries.map { library -> library.path },
    )
    inputs.property("ffmpegKitAar", ffmpegKitLocalAar.path)
    inputs.property("ffmpegKitArm64Libraries", requiredFfmpegKitArm64Libraries)
    outputs.upToDateWhen { false }

    doLast {
        val invalidLibraries =
            requiredExternallyBuiltNativeLibraries.filter { library ->
                !library.isFile || library.length() == 0L
            }
        require(invalidLibraries.isEmpty()) {
            "Missing or empty externally built native library: " +
                invalidLibraries.joinToString { library -> library.path } +
                ". Run tools/native_ripgrep/build_native_ripgrep.ps1 before packaging."
        }

        require(ffmpegKitLocalAar.isFile && ffmpegKitLocalAar.length() > 0L) {
            "Missing or empty FFmpegKit AAR: ${ffmpegKitLocalAar.path}. " +
                "Build it with tools/ffmpeg/build_ffmpeg_kit_wsl.sh and import it with " +
                "tools/ffmpeg/import_local_ffmpeg_kit.ps1 before packaging."
        }

        ZipFile(ffmpegKitLocalAar).use { archive ->
            val invalidEntries =
                requiredFfmpegKitArm64Libraries.filter { entryName ->
                    val entry = archive.getEntry(entryName)
                    entry == null || entry.size <= 0L
                }
            require(invalidEntries.isEmpty()) {
                "FFmpegKit AAR is missing or contains empty arm64 native libraries: " +
                    invalidEntries.joinToString()
            }
        }
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun parseSttModelAssetManifest(manifestFile: File): List<SttModelAsset> {
    return manifestFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapIndexed { index, line ->
            val parts = line.split("|")
            require(parts.size == 6) {
                "Invalid STT model asset manifest line ${index + 1}: expected 6 fields"
            }
            val targetPath = parts[0]
            require(!targetPath.startsWith("/") && !targetPath.contains("..") && !targetPath.contains('\\')) {
                "Invalid STT model asset target path: $targetPath"
            }
            SttModelAsset(
                targetPath = targetPath,
                sourceUrl = parts[1],
                expectedBytes = parts[2].toLong(),
                expectedSha256 = parts[3].lowercase(),
            )
        }
}

fun verifySttModelAsset(file: File, asset: SttModelAsset): Boolean {
    return file.isFile &&
        file.length() == asset.expectedBytes &&
        sha256(file) == asset.expectedSha256
}

fun downloadSttModelAsset(asset: SttModelAsset, destination: File) {
    destination.parentFile.mkdirs()
    require(destination.parentFile.isDirectory) {
        "Unable to create STT model asset directory: ${destination.parent}"
    }

    val tempFile = File(destination.parentFile, "${destination.name}.download")
    if (tempFile.exists()) {
        tempFile.delete()
    }

    val connection = URI(asset.sourceUrl).toURL().openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.setRequestProperty("User-Agent", "Operit Android build STT asset sync")
    try {
        val responseCode = connection.responseCode
        require(responseCode in 200..299) {
            "Unable to download ${asset.targetPath}: HTTP $responseCode from ${asset.sourceUrl}"
        }
        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } finally {
        connection.disconnect()
    }

    require(verifySttModelAsset(tempFile, asset)) {
        "Downloaded STT model asset failed verification: ${asset.targetPath}"
    }
    Files.move(tempFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

val sttModelAssetsManifestFile = layout.projectDirectory.file("config/stt-model-assets.properties")
val generatedSttModelAssetsDir = layout.buildDirectory.dir("generated/stt-model-assets")
val generatedMainAssetsDir = layout.buildDirectory.dir("generated/main-assets")

val syncSttModelAssets by tasks.registering {
    description = "Downloads and verifies generated assets for local STT recognition."
    group = "build setup"

    inputs.file(sttModelAssetsManifestFile)
    outputs.dir(generatedSttModelAssetsDir)
    outputs.upToDateWhen { false }

    doLast {
        val manifestFile = sttModelAssetsManifestFile.asFile
        val assets = parseSttModelAssetManifest(manifestFile).filter { it.targetPath == "models/silero_vad.onnx" }
        val outputRoot = generatedSttModelAssetsDir.get().asFile
        outputRoot.mkdirs()

        val outputRootPath = outputRoot.toPath().toAbsolutePath().normalize()
        val expectedFiles = mutableSetOf<File>()

        assets.forEach { asset ->
            val destinationPath = outputRootPath.resolve(asset.targetPath).normalize()
            require(destinationPath.startsWith(outputRootPath)) {
                "STT model asset target escapes generated assets directory: ${asset.targetPath}"
            }
            val destination = destinationPath.toFile()
            expectedFiles.add(destination.canonicalFile)

            if (!verifySttModelAsset(destination, asset)) {
                if (destination.exists() && !destination.delete()) {
                    error("Unable to replace invalid STT model asset: ${destination.path}")
                }
                downloadSttModelAsset(asset, destination)
            }

            require(verifySttModelAsset(destination, asset)) {
                "STT model asset verification failed after sync: ${asset.targetPath}"
            }
        }

        outputRoot.walkBottomUp()
            .filter { it.isFile && it.canonicalFile !in expectedFiles }
            .forEach { file ->
                require(file.delete()) {
                    "Unable to remove stale STT model asset: ${file.path}"
                }
            }
        outputRoot.walkBottomUp()
            .filter { it.isDirectory && it != outputRoot && it.list()?.isEmpty() == true }
            .forEach { directory ->
                require(directory.delete()) {
                    "Unable to remove empty STT model asset directory: ${directory.path}"
                }
            }
    }
}

// providers.exec 会捕获输出；先打印诊断再检查退出码，避免 CI 只剩 Python 退出码。
fun runResourceScript(scriptName: String, arguments: List<String> = emptyList()) {
    logger.lifecycle("离线资源脚本：$scriptName")
    val execution = providers.exec {
        commandLine(listOf("python3", rootProject.file("ci/distribution/resources/$scriptName").path) + arguments)
        isIgnoreExitValue = true
    }
    val result = execution.result.get()
    val output = execution.standardOutput.asText.get()
    val error = execution.standardError.asText.get()
    if (output.isNotBlank()) logger.lifecycle(output.trimEnd())
    if (error.isNotBlank()) logger.error(error.trimEnd())
    result.assertNormalExitValue()
}

val bundleProjectResources = providers.gradleProperty("operit.bundleProjectResources").map { it.toBooleanStrict() }.getOrElse(false)
val ocrArtifacts = listOf("text-recognition", "text-recognition-chinese", "text-recognition-japanese", "text-recognition-korean", "text-recognition-devanagari")
val ocrModelAars = configurations.create("ocrModelAars") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}
val strippedOcrSdkDir = layout.buildDirectory.dir("generated/offline-ocr-sdk")
val stripOcrSdk by tasks.registering {
    description = "Separates verified OCR model assets from the unchanged ML Kit SDK."
    inputs.files(ocrModelAars)
    inputs.file(file("config/mlkit-ocr-assets.json"))
    inputs.file(rootProject.file("ci/distribution/resources/prepare_ocr_sdk.py"))
    outputs.dir(strippedOcrSdkDir)
    doLast {
        val arguments = mutableListOf("--output", strippedOcrSdkDir.get().asFile.path)
        ocrModelAars.files.sortedBy { it.name }.forEach { arguments.addAll(listOf("--aar", it.path)) }
        runResourceScript("prepare_ocr_sdk.py", arguments)
    }
}
val verifyOfflineHubDistribution by tasks.registering {
    description = "Checks downloadable resource packaging before JVM unit tests."
    inputs.files(
        rootProject.file("ci/test/test_offline_hub_assets.py"),
        rootProject.file("ci/distribution/resources/check_distribution.py"),
        rootProject.file("ci/distribution/resources/prepare_hub.py"),
        rootProject.file("ci/distribution/resources/builtin_packages.py"),
        rootProject.file("ci/distribution/resources/verify_packaged_resources.py"),
    )
    doLast { runResourceScript("check_distribution.py") }
}
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(verifyOfflineHubDistribution)
}

val generatedTerminalCommandAssetsDir = layout.buildDirectory.dir("generated/terminal-command-assets")
val verifyTerminalCommandAssets by tasks.registering {
    description = "Verifies the ARM64 Linux terminal command and its asset manifest."
    inputs.dir(generatedTerminalCommandAssetsDir)
    inputs.file(rootProject.file("tools/native_apply_patch/package_assets.py"))
    doLast {
        val execution = providers.exec {
            commandLine(
                "python3", rootProject.file("tools/native_apply_patch/package_assets.py").path,
                "--verify", "--output", generatedTerminalCommandAssetsDir.get().asFile.path,
            )
        }
        execution.result.get().assertNormalExitValue()
    }
}

val syncMainAssets by tasks.registering(Sync::class) {
    description = "Assembles application assets with verified generated STT model files."
    group = "build setup"
    dependsOn(syncSttModelAssets, verifyTerminalCommandAssets)

    from("src/main/assets") {
        exclude("models/**", "subpack/**")
        if (!bundleProjectResources) exclude("templates/**", "packages/**")
    }
    from(generatedSttModelAssetsDir)
    from(generatedTerminalCommandAssetsDir)
    from("config/offline-resources.properties")
    from("config/local-speech-models.json")
    from("config/offline-packs.json")
    inputs.property("bundleProjectResources", bundleProjectResources)
    inputs.file(rootProject.file("ci/distribution/resources/prepare_hub.py"))
    inputs.file(rootProject.file("ci/distribution/resources/verify_hub_catalog.py"))
    inputs.file(rootProject.file("ci/distribution/resources/builtin_packages.py"))
    inputs.file(rootProject.file("ci/distribution/resources/builtin_source_snapshot.py"))
    inputs.file(file("config/offline-pack-sources.json"))
    inputs.dir(rootProject.file("examples"))
    inputs.file(rootProject.file("tools/example_packages/packages_whitelist.txt"))
    inputs.dir(file("src/main/assets/templates"))
    inputs.dir(rootProject.file("examples/apktool/resources/apktool"))
    into(generatedMainAssetsDir)
    doLast {
        runResourceScript("verify_hub_catalog.py")
        // 只修改生成目录，源码模板和完整插件仍保留，供独立资源发行与完整构建使用。
        if (bundleProjectResources) {
            val sourceRoot = file("src/main/assets/templates")
            sourceRoot.walkTopDown().filter { it.isFile && it.name == ".gitignore" }.forEach { source ->
                val relative = source.relativeTo(sourceRoot).invariantSeparatorsPath.split('/').joinToString("/") { if (it == ".gitignore") "gitignore" else it }
                val target = File(generatedMainAssetsDir.get().asFile, "templates/$relative")
                target.parentFile.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        } else {
            runResourceScript("prepare_hub.py", listOf("--slim-assets", generatedMainAssetsDir.get().asFile.path))
        }
    }
}

android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf(generatedMainAssetsDir.get().asFile))
        }
        if (ciPreviewRelease) {
            getByName("release") {
                manifest.srcFile("src/kong/AndroidManifest.xml")
                res.srcDir("src/kong/res")
            }
        }
    }

    signingConfigs {
        if (ciPreviewRelease) {
            create("ciDistribution") {
                storeFile = rootProject.file(".ci-signing/operit-distribution.p12")
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("OPERIT_DISTRIBUTION_STORE_PASSWORD").orNull
                keyAlias = "operit-distribution"
                keyPassword = providers.environmentVariable("OPERIT_DISTRIBUTION_STORE_PASSWORD").orNull
            }
        }
        val releaseKeystorePath = localProperties.getProperty("RELEASE_STORE_FILE")
        val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")

        if (releaseKeystorePath != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null &&
            File(releaseKeystorePath).exists()
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        applicationId = "com.ai.assistance.operit"
        minSdk = 26
        targetSdk = 34
        versionCode = 76
        versionName = "1.12.2+20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            // Keep native compilation aligned with the app's only supported ABI.
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }

    }

    buildTypes {
        val releaseSigningConfig = signingConfigs.findByName("release")

        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (ciPreviewRelease) {
                // 包名和固定签名共同维持独立发行版后续覆盖安装的身份。
                applicationIdSuffix = ".kong"
                signingConfig = signingConfigs.getByName("ciDistribution")
            } else if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "Operit Debug")
        }
        create("clone") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".clone"
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Operit Clone")
        }
        create("nightly") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    applicationVariants.all {
        if (buildType.name == "release" && ciPreviewRelease) {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-kong.apk"
            }
        }
        if (buildType.name == "nightly") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-nightly.apk"
            }
        }
        if (buildType.name == "clone") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-clone.apk"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    packaging {
        
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE-EPL-1.0.txt"
            excludes += "LICENSE-EPL-1.0.txt"
            excludes += "/META-INF/LICENSE-EDL-1.0.txt"
            excludes += "LICENSE-EDL-1.0.txt"
            
            // Resolve merge conflicts for document libraries
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
            // Fix for any other potential duplicate files
            pickFirsts += "**/*.so"
        }
    }
//    aaptOptions {
//        noCompress += "tflite"
//    }
}

val signRotatedReleaseApk by tasks.registering {
    description = "Signs the Release APK with the legacy V2 signer and rotated V3 signer."
    group = "distribution"
    dependsOn("packageRelease")
    doLast {
        signApkWithRotation(
            project.layout.buildDirectory
                .file("outputs/apk/release/app-release.apk")
                .get()
                .asFile,
        )
    }
}

val signRotatedNightlyApk by tasks.registering {
    description = "Signs the Nightly APK with the legacy V2 signer and rotated V3 signer."
    group = "distribution"
    dependsOn("packageNightly")
    doLast {
        signApkWithRotation(
            project.layout.buildDirectory
                .file("outputs/apk/nightly/app-nightly.apk")
                .get()
                .asFile,
        )
    }
}

if (!ciPreviewRelease) {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(signRotatedReleaseApk)
    }
}

tasks.matching { it.name == "assembleNightly" }.configureEach {
    finalizedBy(signRotatedNightlyApk)
}

if (ciPreviewRelease) {
    val verifyCiPreviewRelease by tasks.registering {
        group = "verification"
        description = "Verifies the signed Kong coexist APK."
        dependsOn("assembleRelease")
        doLast {
            val apkFile = layout.buildDirectory.file("outputs/apk/release/app-kong.apk").get().asFile
            require(apkFile.isFile && apkFile.length() > 0) {
                "Kong coexist APK was not produced: ${apkFile.path}"
            }
            val sdkDirectory = File(requiredLocalProperty("sdk.dir"))
            val apksigner = sdkDirectory.resolve("build-tools/35.0.0/apksigner")
            val signingVerification = providers.exec {
                commandLine(apksigner.path, "verify", "--verbose", "--print-certs", apkFile.path)
            }
            signingVerification.result.get().assertNormalExitValue()
            val signingReport = signingVerification.standardOutput.asText.get()
            val certificateSha256 = Regex("""Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)""")
                .find(signingReport)?.groupValues?.get(1)
            require(certificateSha256 != null && certificateSha256.equals(ciDistributionCertificateSha256, ignoreCase = true)) {
                "Kong coexist APK has an unexpected signing certificate"
            }
            // 校验实际 APK 的包名和调试标记，避免把调试产物当作发行包上传。
            val aapt2 = sdkDirectory.resolve("build-tools/35.0.0/aapt2")
            // 由 Gradle 读取标准输出，避免 java 扩展遮蔽同名包而使脚本编译失败。
            val badging = providers.exec {
                commandLine(aapt2.path, "dump", "badging", apkFile.path)
            }.standardOutput.asText.get()
            require(badging.lineSequence().any {
                it.startsWith("package: name='com.ai.assistance.operit.kong' ")
            }) {
                "Kong coexist APK has an unexpected package name"
            }
            require(badging.lineSequence().none {
                it.startsWith("application-debuggable")
            }) {
                "Kong coexist APK must not be debuggable"
            }
            val badgingReport = layout.buildDirectory.file("reports/kong-preview/apk-badging.txt").get().asFile
            badgingReport.parentFile.mkdirs()
            badgingReport.writeText(badging)
            badgingReport.resolveSibling("apk-signing.txt").writeText(signingReport)
        }
    }
    // 保留现有发布入口；下载产物只保留共存 APK，避免上传规则同时收集 AAB。
    tasks.matching { it.name == "bundleRelease" }.configureEach {
        dependsOn(verifyCiPreviewRelease)
        doLast {
            project.delete(layout.buildDirectory.dir("outputs/bundle"))
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncMainAssets)
    dependsOn(verifyExternallyBuiltNativeLibraries)
}

tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(syncMainAssets)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.github.jelmerk:hnswlib-core:1.2.1")
    implementation(project(":terminal")) {
        // 终端保留独立编译依赖，应用只打包图标子集，避免完整扩展库产生重复类。
        exclude(group = "androidx.compose.material", module = "material-icons-extended")
        exclude(group = "androidx.compose.material", module = "material-icons-extended-android")
    }
    implementation(project(":mnn"))
    implementation(project(":llama"))
    implementation(project(":showerclient"))
    // 原生动态界面兼容层使用当前 SDK 和 Compose 版本编译。
    implementation(project(":a2ui-compat"))
    implementation(project(":quickjs"))

    implementation(libs.androidx.ui.graphics.android)
    // The only vendored artifact is the custom FFmpegKit AAR.
    implementation(files("libs/ffmpeg-kit-local.aar"))
    implementation("com.arthenica:smart-exception-common:0.2.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.androidx.animation.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.activity.ktx)

    // Desugaring support for modern Java APIs on older Android
    coreLibraryDesugaring(libs.desugar.jdk)

    // 仅拆出模型资产；原 SDK 描述符、JNI 运行库和多语言识别接口继续随 APK 提供。
    ocrArtifacts.forEach { add(ocrModelAars.name, "com.google.mlkit:$it:16.0.0") }
    if (bundleProjectResources) {
        implementation(libs.mlkit.text.recognition)
        implementation(libs.mlkit.text.chinese)
        implementation(libs.mlkit.text.japanese)
        implementation(libs.mlkit.text.korean)
        implementation(libs.mlkit.text.devanagari)
    } else {
        implementation(files(ocrArtifacts.map { artifact -> strippedOcrSdkDir.map { it.file("$artifact-16.0.0.aar").asFile } }).builtBy(stripOcrSdk))
        implementation("com.google.mlkit:text-recognition-bundled-common:16.0.0")
        implementation("com.google.mlkit:common:18.8.0")
        implementation("com.google.android.gms:play-services-base:18.1.0")
        implementation("com.google.android.gms:play-services-basement:18.1.0")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-common:19.0.0")
        listOf("chinese", "japanese", "korean", "devanagari").forEach {
            implementation("com.google.android.gms:play-services-mlkit-text-recognition-$it:16.0.0")
        }
    }
    
    implementation(libs.zxing.core)
    
    // diff
    implementation(libs.java.diff.utils)
    
    // APK解析和修改库
    implementation(libs.android.apksig) // APK签名工具
    implementation(libs.apk.parser) // 用于解析和处理AndroidManifest.xml
    implementation(libs.sable.axml) // 用于Android二进制XML的读写
    implementation(libs.zipalign.java) // 用于处理ZIP文件对齐
    
    // ZIP处理库 - 用于APK解压和重打包
    implementation(libs.commons.compress)
    implementation(libs.commons.io) // 添加Apache Commons IO
    
    // 图片处理库
    implementation(libs.glide) // 用于处理图像
    
    // XML处理
    implementation(libs.androidx.core.ktx)
    
    // libsu - root access library
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")
    
    // Add missing SVG support
    implementation(libs.androidsvg)
    
    // Add missing GIF support for Markwon
    implementation(libs.android.gif)
    
    // Image Cropper for background image cropping
    implementation(libs.image.cropper)
    
    // ExoPlayer for video background
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    
    // Material 3 Window Size Class
    implementation(libs.material3.window)
    
    // Window metrics library for foldables and adaptive layouts
    implementation(libs.window)
    implementation(libs.androidx.webkit)

    // Document conversion libraries
    implementation(libs.itextg)
    implementation(libs.pdfbox)
    implementation(libs.zip4j)
    
    // 图片加载库
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    
    // LaTeX rendering libraries
    implementation(libs.jlatexmath)
    implementation(libs.renderx) // RenderX library for LaTeX rendering
    
    // Base Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.reflect)
    
    // UUID dependencies
    implementation(libs.uuid)
    
    // Gson for JSON parsing
    implementation(libs.gson)

    // HJSON dependency for human-friendly JSON parsing
    implementation(libs.hjson)

    // 中文分词库 - Jieba Android
    implementation(libs.jieba)

    // 向量搜索库 - 轻量级实现，适合Android
    implementation(libs.hnswlib.core)
    implementation(libs.hnswlib.utils)
    
    // MediaPipe 文本任务
    implementation(libs.mediapipe.tasks.text)
    
    // ONNX Runtime for Android - 支持更强大的多语言Embedding模型
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Kotlin扩展和协程支持
    kapt(libs.room.compiler) // 使用kapt代替ksp

    // ObjectBox
    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)
    implementation(libs.commons.compress.v2)
    implementation(libs.junrar)

    // Compose dependencies - use BOM for version consistency
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Use BOM version for all Compose dependencies
    implementation(project(":material-icons-subset"))
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Shizuku dependencies
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Tasker Plugin Library
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    
    // WorkManager for scheduled workflows
    implementation(libs.work.runtime.ktx)

    // Network dependencies
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.jsoup)

    // DataStore dependencies
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    // JVM 中文排序测试使用 ICU，匹配 Android 的 Collator，不打包进应用。
    testImplementation("com.ibm.icu:icu4j:76.1")
    // 目录迁移的 JVM 测试通过 JDBC 执行真实 SQLite SQL，需要在测试运行时提供驱动。
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")
    // JVM tests need a real implementation because Android's org.json methods are throwing stubs.
    testImplementation(libs.json.jvm)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))

    // 单元测试中真实 org.json（Android 桩在 JVM 测试里会抛 Stub! 异常）；
    // 统计 usage 归一化测试需要解析 JSONObject。
    testImplementation("org.json:json:20240303")

    // Apache POI - for Document processing (DOC, DOCX, etc.)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)

    // Color picker for theme customization
    implementation(libs.colorpicker)
    implementation(libs.backdrop)
    implementation(libs.liquid)
    
    // NanoHTTPD for local web server
    implementation(libs.nanohttpd)

    // 添加测试依赖
    testImplementation(libs.junit)
    
    // Android测试依赖
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    
    // 协程测试依赖
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)
    
    // 模拟测试框架 - 保留现有的 mockito 并新增 mockk
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.mockito.android)
    
    // // 新增的测试依赖 - mockk 和 kotlin-test
    // testImplementation(libs.mockk)
    // testImplementation(libs.ktor.server.test.host)
    // testImplementation(libs.kotlinx.coroutines.debug)
    // androidTestImplementation(libs.mockk)
    
    implementation(libs.reorderable)

    // Swipe to reveal actions
    implementation(libs.swipe)

    // Coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.mcp.sdk.client)
    implementation(libs.ktor.client.okhttp)
    
    // Exclude bcprov-jdk15to18 from all configurations to avoid duplicate classes
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // BouncyCastle - explicitly include jdk18on version to avoid conflicts
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation(libs.okhttp.logging.interceptor)


    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // Glance for Widgets (Compose for Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}

// 向 JVM 回归提供明确的源码根目录，供 Compose DSL 运行时用例读取宿主脚本。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("operit.repositoryRoot", rootProject.projectDir.absolutePath)
}

// 终端库使用独立仓库，宿主 JVM 检查必须一并执行其生命周期回归。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn(":terminal:testDebugUnitTest")
    }
}

// 开发构建使用固定资源快照，正式发行仍要求当前源码与该资源版本一致。
val verifyCurrentOfflinePackSources by tasks.registering {
    group = "verification"
    doLast {
        runResourceScript("verify_hub_catalog.py", listOf("--current-source"))
    }
}
tasks.configureEach {
    if (name == "verifyCiPreviewRelease" || name == "bundleRelease") {
        dependsOn(verifyCurrentOfflinePackSources)
    }
}
