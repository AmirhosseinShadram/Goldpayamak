import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.goldpayamak.issuer"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.goldpayamak.issuer"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    packaging.resources.merges += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
}

dependencies {
    implementation(project(":core"))
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
}
