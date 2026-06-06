// :app —— Android 应用模块。
// 持有所有依赖 Android 平台的实现(CameraX、ML Kit、未来的 Gemma4Engine / SherpaTtsEngine),
// 与 :core 的纯接口隔开(CLAUDE.md §4)。
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
    // 提的硬下限,见 docs/FACTS.md#F9 与 docs/PROGRESS.md Phase 0 注脚。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumiread"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 3
        versionName   = "3.0.0"

        // Phase 3 曳光弹(CLAUDE.md §B2)走 AndroidJUnitRunner,见 Gemma4EngineTracerTest。
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
            // 黑客松交付期不上 Play,先不接 ProGuard。Phase 6 再调。
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
    // AppCompat —— v1.1 步骤二:per-app locale 走 AppCompatDelegate.setApplicationLocales
    // MainActivity 因此从 ComponentActivity 切到 AppCompatActivity(它仍是 ComponentActivity 子类,Compose 不受影响)
    implementation(libs.androidx.appcompat)

    // Compose (BOM 统一对齐版本)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX —— FACTS#F5
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit —— FACTS#F3,真实 OCR 实现 Phase 2 接入
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.image.labeling)

    // LiteRT-LM —— FACTS#F2,Phase 3 才真实使用;现在引入让骨架完整。
    implementation(libs.litertlm.android)

    // sherpa-onnx 1.13.2 —— FACTS#F4,Phase 4 真实 TTS 引擎。
    // 不在 Maven Central,走本地 AAR(54 MB,见 docs/spikes/sherpaonnx_NOTE.md §4)。
    implementation(files("../libs/sherpa-onnx-1.13.2.aar"))

    // 协程
    implementation(libs.kotlinx.coroutines.core)

    // 持久化(Phase 5 才用到,先引以避免后续返工)
    implementation(libs.androidx.datastore.preferences)
    // Room —— Phase 5 学习记录(StudyRecord/StudyDao/LumiReadDb)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Phase 3 曳光弹仪表化测试(CLAUDE.md §B2 真机跑)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
