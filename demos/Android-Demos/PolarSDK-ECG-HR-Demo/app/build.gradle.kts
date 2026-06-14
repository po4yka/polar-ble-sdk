plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.polar.polarsdkecghrdemo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.polar.polarsdkdemo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("library", "sdk")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    val sdkArtifactDir = rootProject.file("../../../examples/example-android/polar-sensor-data-collector/polarBleSdk")
    implementation(files(sdkArtifactDir.resolve("polar-ble-sdk.aar")))
    val localSharedSdkAar = sdkArtifactDir.resolve("polar-ble-sdk-shared.aar")
    if (localSharedSdkAar.exists()) {
        implementation(files(localSharedSdkAar))
    }

    implementation(libs.androidplot.core)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlin.stdlib.jdk8)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
