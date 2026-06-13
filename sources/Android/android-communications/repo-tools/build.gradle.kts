plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlin.stdlib)
}

val repositoryRoot = rootProject.projectDir.parentFile.parentFile.parentFile

fun repoToolTask(taskName: String, commandName: String) {
    tasks.register<JavaExec>(taskName) {
        dependsOn(tasks.named("classes"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.polar.tools.RepoToolsKt")
        args(commandName)
        workingDir = repositoryRoot
        systemProperty("polar.repo.root", repositoryRoot.absolutePath)
    }
}

repoToolTask("kmpNonGradleChecks", "kmpNonGradleChecks")
repoToolTask("verifyReleasePackagingPolicy", "verifyReleasePackagingPolicy")
repoToolTask("iosXcodeValidationProbe", "iosXcodeValidationProbe")
repoToolTask("validateSpmXcframeworkConsumption", "validateSpmXcframeworkConsumption")
