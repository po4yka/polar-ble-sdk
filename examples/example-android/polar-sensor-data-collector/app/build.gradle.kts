import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    `maven-publish`
}

fun getVersion(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val describedVersion = process.inputStream.bufferedReader().readText().trim()
        val exitValue = process.waitFor()
        if (exitValue != 0) {
            logger.warn("WARNING: 'git describe --tags' failed (exit $exitValue). Version information from git is not available. Using fallback version.")
            "unknown"
        } else {
            val matcher = Pattern.compile("^(\\d*)(\\.)(\\d*)(\\.)(\\d*)(-.*)").matcher(describedVersion)
            if (matcher.matches()) {
                "${matcher.group(1)}.${matcher.group(3)}.${matcher.group(5)}"
            } else {
                describedVersion
            }
        }
    } catch (exception: Exception) {
        logger.warn("WARNING: git command is not available or failed: ${exception.message}. Version information from git is not possible to detect. Using fallback version.")
        "unknown"
    }
}

fun getSdkVersion(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val describedVersion = process.inputStream.bufferedReader().readText().trim()
        val exitValue = process.waitFor()
        if (exitValue != 0) {
            logger.warn("WARNING: 'git describe --tags' failed (exit $exitValue). SDK version from git is not available. Using fallback version.")
            "unknown"
        } else {
            val matcher = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-.*)?$").matcher(describedVersion)
            if (matcher.matches()) {
                "${matcher.group(1)}.${matcher.group(2)}.${matcher.group(3)}"
            } else {
                describedVersion
            }
        }
    } catch (exception: Exception) {
        logger.warn("WARNING: git command is not available or failed: ${exception.message}. SDK version from git is not possible to detect. Using fallback version.")
        "unknown"
    }
}

android {
    namespace = "com.polar.polarsensordatacollector"
    compileSdk = 37

    buildTypes.all {
        buildConfigField("String", "SDK_VERSION", "\"${getSdkVersion()}\"")
    }

    defaultConfig {
        applicationId = "com.polar.polarsensordatacollector"
        minSdk = 26
        targetSdk = 37
        versionCode = 76700
        versionName = "7.67.0"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        missingDimensionStrategy("library", "sdk")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        val keystorePath = System.getenv("PSDC_ANDROID_KEYSTORE_PATH")
        if (!keystorePath.isNullOrEmpty()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PSDC_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PSDC_ANDROID_KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("PSDC_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            firebaseAppDistribution {
                artifactType = "APK"
                serviceCredentialsFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
                groups = "psdc-internal-testing"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("apk") {
            groupId = "com.polar"
            version = getVersion()
            artifactId = "polar-sensor-data-collector"
            artifact(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
        }
    }
}

dependencies {
    implementation(files("../polarBleSdk/polar-ble-sdk.aar"))
    val localSharedSdkAar = file("../polarBleSdk/polar-ble-sdk-shared.aar")
    if (localSharedSdkAar.exists()) {
        implementation(files(localSharedSdkAar))
    }

    if (providers.gradleProperty("leakCanary").map(String::toBoolean).getOrElse(false)) {
        debugImplementation(libs.leakcanary.android)
    }

    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.rxkotlin)
    implementation(libs.commons.io)
    implementation(libs.protobuf.javalite)
    implementation(libs.retrofit)
    implementation(libs.gson)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.adapter.rxjava3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.viewpager2)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.rx3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.theme.adapter)
    implementation(libs.accompanist.appcompat.theme)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.androidx.legacy.support.v4)
    ksp(libs.hilt.compiler)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.navigation.dynamic.features.fragment)
    androidTestImplementation(libs.navigation.testing)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.security.identity.credential)
    implementation(libs.androidx.security.app.authenticator)
    androidTestImplementation(libs.androidx.security.app.authenticator)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

gradle.taskGraph.whenReady {
    val releaseTaskRequested = allTasks.any { task -> task.name.matches(Regex(".*[Rr]elease.*")) }
    if (releaseTaskRequested && android.signingConfigs.findByName("release") == null) {
        throw GradleException("Release signing not configured. Set PSDC_ANDROID_KEYSTORE_PATH, PSDC_ANDROID_KEYSTORE_PASSWORD, and PSDC_ANDROID_KEY_PASSWORD.")
    }
}
