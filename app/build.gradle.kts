import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.termuxcodex.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.termuxcodex.client"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Pinned to the newest releases compatible with compileSdk 36 and AGP 8.13.
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.15")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
