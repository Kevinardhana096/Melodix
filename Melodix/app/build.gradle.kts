plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") // Add this line
}

android {
    namespace = "com.example.melodix"
    compileSdk = 35  // Updated from 34 to 35 for compatibility with androidx.activity:activity:1.10.1

    defaultConfig {
        applicationId = "com.example.melodix"
        minSdk = 26
        targetSdk = 34  // Keeping targetSdk unchanged
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation (libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation ("de.hdodenhof:circleimageview:3.1.0")

    // Add Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))  // Menggunakan versi yang lebih stabil
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.7.0")  // Versi yang lebih stabil

    // Facebook Sign In
    implementation("com.facebook.android:facebook-login:16.2.0")  // Versi yang lebih stabil
    implementation("com.facebook.android:facebook-android-sdk:16.2.0")  // Versi yang lebih stabil

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")  // Versi yang lebih baru dan stabil
    implementation("de.hdodenhof:circleimageview:3.1.0")  // For circular profile image

    // For image loading (optional but recommended)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Alternatif untuk Deezer SDK
    // Jika Deezer SDK tidak tersedia, kita akan menggunakan Retrofit saja untuk REST API
    // implementation ("com.deezer.sdk:deezer-android-sdk:1.11.0")

    // Untuk permintaan jaringan
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

