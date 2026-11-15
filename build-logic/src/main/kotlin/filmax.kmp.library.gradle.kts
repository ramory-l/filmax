import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("filmax.detekt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // iOS (iPhone/iPad) + tvOS (Apple TV). Общий Apple-код живёт в appleMain
    // (создаётся default-иерархией KMP и покрывает и ios, и tvos).
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    // JVM-таргет модуль заводит сам (например, протокол хаба для сервера на Маке), но байткод —
    // под Java 17, как у Android: иначе он собирается под JDK сборки и не запустится на Temurin 17.
    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Тесты общего кода — в commonTest: на Android-таргете их гоняет testDebugUnitTest (JVM),
    // а kotlin("test") сам подбирает раннер под платформу.
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}

// Android-конфигурация общая для всех KMP-модулей; namespace задаётся в каждом модуле.
extensions.configure<LibraryExtension> {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
