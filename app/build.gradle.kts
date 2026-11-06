plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.examprep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.examprep"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += "META-INF/LICENSE.md"
        resources.excludes += "META-INF/NOTICE.md"
        resources.excludes += "META-INF/LICENSE"
        resources.excludes += "META-INF/NOTICE"
        resources.excludes += "META-INF/DEPENDENCIES"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        resources.excludes += "META-INF/BC*.DSA"
        resources.excludes += "META-INF/BC*.SF"
        resources.excludes += "META-INF/BC*.RSA"
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.fragment)
    implementation(libs.preference)
    implementation(libs.swiperefreshlayout)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // JSON
    implementation(libs.gson)

    // ML Kit OCR (will be used in Stage 2)
    implementation(libs.mlkit.text)

    // PDF extraction (will be used in Stage 2)
    implementation(libs.pdfbox.android)

    // PDF creation (will be used in Stage 4)
    implementation(libs.itext7.core)

    // Media3 (will be used in Stage 7)
    implementation(libs.media3.transformer)
    implementation(libs.media3.common)
    implementation(libs.media3.effect)
    implementation(libs.media3.muxer)

    // Firebase Auth + Google Sign-In (Credential Manager)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
