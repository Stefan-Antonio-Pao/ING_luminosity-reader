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
    // OCR 修正 JSON 防御式解析(FACTS#F13)。只用 Json.parseToJsonElement 树解析,无编译器插件。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    // 词典硬验证(任务书 §1.3):JVM 直接打开随包 SQLite,断言 caterpillar/毛毛虫 真实词条。
    testImplementation(libs.sqlite.jdbc)
}
