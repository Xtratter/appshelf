plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.xtratter.appshelf"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.xtratter.appshelf"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "1.12"
    }
    buildTypes {
        release {
            // R8: сжатие и оптимизация кода — приложение меньше и быстрее, чем debug-сборка
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // в Android org.json встроен, а в unit-тестах на JVM нужна настоящая реализация
    testImplementation("org.json:json:20240303")
}
