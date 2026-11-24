import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// آدرس سرور فعال‌سازی؛ هنگام ساخت: ./gradlew :app:assembleRelease -PactivationUrl=https://your-server
val activationUrl = (project.findProperty("activationUrl") as String?) ?: "https://CHANGE-ME.invalid"

// نسخهٔ دمو (بدون کد فعال‌سازی، فقط برای آزمایش): -PdemoMode=true
val demoMode = (project.findProperty("demoMode") as String?) == "true"

android {
    namespace = "ir.goldpayamak.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.goldpayamak.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = if (demoMode) "1.7-demo" else "1.7"
        buildConfigField("boolean", "DEMO", demoMode.toString())
        if (demoMode) applicationIdSuffix = ".demo"
        manifestPlaceholders["appLabel"] = if (demoMode) "گلدپیامک (دمو)" else "گلدپیامک"
        buildConfigField("String", "ACTIVATION_URL", "\"${activationUrl.trimEnd('/')}\"")
        // فقط اگر آدرس http (بدون TLS) داده شده باشد ترافیک متنی مجاز می‌شود
        manifestPlaceholders["cleartext"] = if (activationUrl.startsWith("http://")) "true" else "false"
    }

    // کلید امضای ثابت: اگر هر بار کلید جدید ساخته شود، اندروید اجازهٔ بروزرسانی روی نسخهٔ قبلی را نمی‌دهد
    // و کاربر مجبور به حذف برنامه می‌شود (که مخاطبین و گزارش‌های محلی را پاک می‌کند).
    signingConfigs {
        create("shared") {
            storeFile = file("signing.jks")
            storePassword = "android"
            keyAlias = "goldpayamak"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
