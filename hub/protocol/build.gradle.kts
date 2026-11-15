// Протокол Filmax Hub: DTO и пути API, общие для сервера на Маке (JVM) и приложения (Android,
// iOS через :shared). Один источник правды — поле не разойдётся между клиентом и сервером.
plugins {
    id("filmax.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.filmax.hub.protocol"
}

kotlin {
    // JVM — для hub:server: сервер собирается обычным Kotlin/JVM-приложением.
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
