// Filmax Hub — свой сервер на Маке вместо kino.pub: витрина из TMDB (ряды, каталог, поиск,
// карточки с сезонами), прокси картинок, дальше — подбор раздач и прогресс. Обычное JVM-приложение:
// `installDist` даёт каталог со скриптом запуска, его и кладём на Мак под LaunchAgent.
plugins {
    id("filmax.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass = "com.filmax.hub.server.MainKt"
    applicationName = "filmax-hub"
    // Мак держит Jellyfin, TorrServer и Jackett, памяти мало: хабу хватает небольшой кучи.
    applicationDefaultJvmArgs = listOf("-Xmx192m", "-XX:+UseSerialGC")
}

dependencies {
    implementation(project(":hub:protocol"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
}
