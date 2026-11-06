import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ai.assistance.showerclient"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = false
        aidl = true
        buildConfig = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}

// The server and client Binder protocol must come from the same checkout.
val showerServerSources = rootProject.layout.projectDirectory.dir("tools/shower/app/src/main/java")
val showerServerAndroidJar = android.sdkDirectory.resolve("platforms/android-${requireNotNull(android.compileSdk)}/android.jar")
val showerServerClasses = layout.buildDirectory.dir("generated/showerServer/classes")
val showerServerAssets = layout.buildDirectory.dir("generated/showerServer/assets")

val compileShowerServerJava by tasks.registering(JavaCompile::class) {
    source(fileTree(showerServerSources) { include("**/*.java") })
    classpath = files(showerServerAndroidJar)
    destinationDirectory.set(showerServerClasses)
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.encoding = "UTF-8"
}

val packageShowerServerClasses by tasks.registering(Jar::class) {
    dependsOn(compileShowerServerJava)
    from(showerServerClasses)
    destinationDirectory.set(layout.buildDirectory.dir("generated/showerServer"))
    archiveFileName.set("server-classes.jar")
}

val generateShowerServerAssets by tasks.registering(JavaExec::class) {
    dependsOn(packageShowerServerClasses)
    val classesJar = packageShowerServerClasses.flatMap { it.archiveFile }
    val serverJar = showerServerAssets.map { it.file("shower-server.jar") }
    inputs.file(classesJar)
    outputs.file(serverJar)
    classpath = files(android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}/lib/d8.jar"))
    mainClass.set("com.android.tools.r8.D8")
    args("--lib", showerServerAndroidJar.absolutePath, "--min-api", "26", "--output",
        serverJar.get().asFile.absolutePath, classesJar.get().asFile.absolutePath)
    doFirst { showerServerAssets.get().asFile.mkdirs() }
}

android.sourceSets.getByName("main").assets.setSrcDirs(listOf(showerServerAssets))
tasks.named("preBuild").configure { dependsOn(generateShowerServerAssets) }
