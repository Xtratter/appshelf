plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.xtratter.appshelf"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.xtratter.appshelf.expressive"   // эксперимент: ставится рядом с обычным AppShelf
        minSdk = 26
        targetSdk = 35
        versionCode = 57
        versionName = "1.26.2-expressive"
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
