plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
}

android {
    namespace = "fi.anssi.kalakartta"
    compileSdk = 35

    defaultConfig {
        applicationId = "fi.anssi.kalakartta"
        minSdk = 24
        targetSdk = 35
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
    implementation(libs.osmdroidAndroid)
    implementation(libs.material)

    implementation(libs.roomRuntime)
    ksp(libs.roomCompiler)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}