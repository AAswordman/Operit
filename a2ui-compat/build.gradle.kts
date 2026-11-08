import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ai.assistance.operit.a2uicompat"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    // 官方协议、引擎与 Compose 源码一起使用当前工具链编译，保留原始协议行为。
    api("androidx.collection:collection:1.5.0")
    api(libs.coroutines.core)
    api(libs.kotlinx.serialization)
    implementation(libs.androidx.core.ktx)
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.androidx.runtime.android)
}
