import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x built-in Kotlin: do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.adamsjack.beanshelf"
    // compileSdk 37 = Android 17; current androidx requires compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.adamsjack.beanshelf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.subjectsegmentation)
    implementation(libs.play.services.base)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
