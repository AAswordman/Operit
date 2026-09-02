import java.net.URL
import java.io.FileOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ai.assistance.mnn"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        if (project.findProperty("skipNativeBuild")?.toString()?.toBoolean() != true) {
            ndk {
                // 支持的 ABI（与主 app 保持一致）
                abiFilters.addAll(listOf("arm64-v8a"))
            }

            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-fno-emulated-tls")
                    arguments += listOf(
                        "-DANDROID_STL=c++_static",
                        "-DANDROID_PLATFORM=android-26",
                        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                        "-DMNN_BUILD_SHARED_LIBS=ON",
                        "-DMNN_SEP_BUILD=OFF",
                        "-DMNN_BUILD_TOOLS=OFF",
                        "-DMNN_BUILD_DEMO=OFF",
                        "-DMNN_BUILD_CONVERTER=OFF",
                        "-DMNN_USE_LOGCAT=ON",
                        "-DMNN_BUILD_TEST=OFF",
                        "-DMNN_BUILD_BENCHMARK=OFF",
                        "-DMNN_BUILD_QUANTOOLS=OFF",
                        "-DMNN_OPENCL=OFF",
                        "-DMNN_OPENGL=OFF",
                        "-DMNN_VULKAN=OFF",
                        "-DMNN_ARM82=ON",
                        // 启用 LLM 支持
                        "-DMNN_BUILD_LLM=ON",
                        "-DMNN_SUPPORT_TRANSFORMER_FUSE=ON",
                        "-DMNN_LOW_MEMORY=ON",
                        "-DMNN_CPU_WEIGHT_DEQUANT_GEMM=ON"
                    )
                }
            }
        }
    }


    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    if (project.findProperty("skipNativeBuild")?.toString()?.toBoolean() != true) {
        externalNativeBuild {
            cmake {
                path = file("CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

