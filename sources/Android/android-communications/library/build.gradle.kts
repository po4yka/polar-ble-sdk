import com.google.protobuf.gradle.id
import org.gradle.api.publish.maven.MavenPublication
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.dokka)
    `maven-publish`
}

fun getVersion(): String {
    val process = ProcessBuilder("git", "describe", "--tags", "--always")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val describedVersion = process.inputStream.bufferedReader().readText().trim()
    val exitValue = process.waitFor()
    val pattern = Pattern.compile("^([0-9]+)(\\.)([0-9]+)(\\.)([0-9]+)")
    val matcher = pattern.matcher(describedVersion)
    return if (exitValue == 0 && matcher.find()) {
        val major = matcher.group(1)
        val minor = matcher.group(3)
        val patch = matcher.group(5)
        "$major.$minor.$patch"
    } else {
        "0.0.0"
    }
}

configurations.all {
    resolutionStrategy {
        force(libs.byte.buddy)
        force(libs.byte.buddy.agent)
        force(libs.kotlin.metadata.jvm)
    }
}

android {
    namespace = "com.polar.androidcommunications"

    defaultConfig {
        compileSdk = 37
        minSdk = 26
        @Suppress("DEPRECATION")
        val applicationFlavor = this as com.android.build.api.dsl.ApplicationBaseFlavor
        applicationFlavor.targetSdk = 37
        applicationFlavor.versionCode = 14
        applicationFlavor.versionName = "14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes.all {
        val version = getVersion()
        buildConfigField("String", "GIT_VERSION", "\"$version\"")
    }

    buildTypes {
        release {
            consumerProguardFiles("lib-proguard-rules.pro")
        }
    }

    flavorDimensions += "library"
    productFlavors {
        create("sdk") {
            dimension = "library"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("sdkRelease") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    packaging {
        resources {
            excludes += "**/*.proto"
        }
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-Xshare:off")
        }
    }
}

components.matching { it.name == "sdkRelease" }.all {
    val sdkReleaseComponent = this
    publishing {
        publications {
            create<MavenPublication>("aar") {
                from(sdkReleaseComponent)
                groupId = "com.github.polarofficial"
                artifactId = "polar-ble-sdk"
                version = getVersion()
            }
        }
    }
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(rootProject.layout.projectDirectory.dir("../../../docs/polar-sdk-android"))
    }
    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set(".*\\.androidcommunications.*")
            suppress.set(true)
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.commons.io)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.protobuf.javalite)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.rx3)
    "sdkImplementation"(libs.protobuf.javalite)
    "sdkImplementation"(libs.flatbuffers.java)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
