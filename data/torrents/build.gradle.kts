// Раздачи с трекеров через TorrServer в своей сети: поиск (Torznab-мост TorrServer к Jackett),
// разбор качества и озвучки из названия, файлы раздачи и поток серии. KMP, как и остальные
// data-модули: протокол TorrServer и разбор названий не привязаны к Android.
plugins {
    id("filmax.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.filmax.data.torrents"
}

kotlin {
    val koinBom = project.dependencies.platform(libs.koin.bom)
    sourceSets {
        commonMain.dependencies {
            // Движок HTTP и настройки устройства — общие; клиент свой: другой адрес, без авторизации.
            implementation(project(":core:network"))
            implementation(project(":core:domain"))
            implementation(libs.bundles.ktor.common)
            implementation(libs.multiplatform.settings)

            implementation(koinBom)
            implementation(libs.koin.core)
        }
        // Запросы к TorrServer в тестах — подменённым движком Ktor, без сети.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
