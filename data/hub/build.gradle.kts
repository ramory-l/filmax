// Filmax Hub — свой сервер дома вместо kino.pub: витрина из TMDB, видео — раздачи через TorrServer.
// Клиент — те же контракты core:domain поверх HTTP API хаба. Протокол (пути и DTO) — общий с
// сервером модуль :hub:protocol, поэтому клиент и сервер не разойдутся в полях.
plugins {
    id("filmax.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.filmax.data.hub"
}

kotlin {
    val koinBom = project.dependencies.platform(libs.koin.bom)
    sourceSets {
        commonMain.dependencies {
            // Движок HTTP и настройки устройства — те же, что у остальных источников.
            implementation(project(":core:network"))
            implementation(project(":core:domain"))
            implementation(project(":hub:protocol"))
            // Поиск хаба в сети — широковещательный UDP, как у Jellyfin.
            implementation(libs.ktor.network)

            implementation(koinBom)
            implementation(libs.koin.core)
        }
        // Запросы к хабу в тестах — подменённым движком Ktor, без сети; TorrServer — фейк из data:fake.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
            implementation(project(":data:fake"))
        }
    }
}
