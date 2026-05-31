// :core —— 纯 Kotlin/JVM 模块。
// 不依赖 Android UI 类(为将来 Windows 移植留余地,CLAUDE.md §2.3 / §4)。
// Android 特定实现(ML Kit、LiteRT-LM、sherpa-onnx、CameraX 等)放在 :app 模块。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
