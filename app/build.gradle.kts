import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "fi.anssi.kalakartta"
    compileSdk = 35

    defaultConfig {
        applicationId = "fi.anssi.kalakartta"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val buildTime = SimpleDateFormat("d.M.yyyy HH:mm", Locale("fi", "FI")).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.osmdroidAndroid)
    implementation(libs.material)

    implementation(libs.roomRuntime)
    ksp(libs.roomCompiler)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

tasks.register("generateFishingSessionTestData") {
    group = "verification"
    description = "Generates large fishing session test data."
    
    doLast {
        println("TestDataGenerator on toteutettu.")
        println("Voit ajaa sen suoraan IDE:stä (TestDataRunner.kt) tai Gradlen kautta:")
        println("./gradlew :app:testDebugUnitTest --tests fi.anssi.kalakartta.testdata.TestDataRunner -PrunGenerator")
    }
}

tasks.withType<Test> {
    if ((name == "testDebugUnitTest" || name == "testReleaseUnitTest") && !project.hasProperty("runGenerator")) {
        filter {
            excludeTestsMatching("fi.anssi.kalakartta.testdata.TestDataRunner")
        }
    }
}