plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "com.github.polarofficial"
version = "0.0.0-local"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    android {
        namespace = "com.polar.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    @Suppress("DEPRECATION")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64(), watchosX64(), watchosArm64(), watchosSimulatorArm64()).forEach { target ->
        target.binaries {
            framework {
                baseName = "PolarBleSdkShared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.matching { task -> task.javaClass.name == "org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest" }.configureEach {
    val repositoryRoot = rootProject.projectDir.parentFile.parentFile.parentFile.absolutePath
    val environmentMethod = javaClass.methods.first { method ->
        method.name == "environment" && method.parameterTypes.size == 2
    }
    environmentMethod.invoke(this, "SIMCTL_CHILD_POLAR_BLE_SDK_REPOSITORY_ROOT", repositoryRoot)
}

publishing {
    repositories {
        maven {
            name = "localKmpReleaseValidation"
            url = layout.buildDirectory.dir("local-maven-validation").get().asFile.toURI()
        }
    }
}
