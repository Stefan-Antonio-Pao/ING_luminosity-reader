// :app —— Android 应用模块。
// 持有所有依赖 Android 平台的实现(CameraX、ML Kit、Gemma4Engine / SherpaTtsEngine),
// 与 :core 的纯接口隔开。
plugins {
    // AGP 9.0+ 内置 Kotlin 支持,不再需要 alias(libs.plugins.kotlin.android)
    // 见 https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.lumiread"
    // compileSdk 36 是 2026-05 Compose BOM 拉的传递依赖(activity 1.12.4 / navigationevent-compose 1.0.2)
    // 提的硬下限。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumiread"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "0.1.0"

        // 曳光弹(tracer-bullet)集成测试走 AndroidJUnitRunner,见 Gemma4EngineTracerTest。
        // 真机跑:./gradlew :app:connectedDebugAndroidTest
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled   = false
            isDebuggable      = true
        }
        getByName("release") {
            isMinifyEnabled = false
            // 黑客松交付期不上 Play,先不接 ProGuard。
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM 统一对齐版本)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit —— 端侧 OCR(拉丁 + 中文)/ 语种判定 / 图像打标
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.image.labeling)

    // LiteRT-LM —— 端侧 Gemma 4 推理引擎
    implementation(libs.litertlm.android)

    // sherpa-onnx 1.13.2 —— 端侧 TTS 运行时。
    // 不在 Maven Central,走本地 AAR(开发者按 README 从 GitHub Releases 下载)。
    implementation(files("../libs/sherpa-onnx-1.13.2.aar"))

    // 协程
    implementation(libs.kotlinx.coroutines.core)

    // 持久化
    implementation(libs.androidx.datastore.preferences)
    // Room —— 学习记录(StudyRecord/StudyDao/LumiReadDb)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 曳光弹(tracer-bullet)仪表化测试(真机跑)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
