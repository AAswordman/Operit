buildscript {
    val objectboxVersion by extra("5.3.0")
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:$objectboxVersion")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}

// 与 CI 和构建文档使用同一 NDK，避免 AGP 按默认版本另行下载工具链。
val androidNdkVersion = "25.1.8937393"
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            ndkVersion = androidNdkVersion
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            ndkVersion = androidNdkVersion
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

tasks.register("assembleDebugClone") {
    dependsOn(":app:assembleClone")
    description = "Build the clone (co-installable) debug APK with package name suffix .clone"
}
