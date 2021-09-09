import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") apply false
}

// Collect all source and class directories for jacoco
// TODO: Is there a better way to do that?
val ignoreCoverageForProjects = setOf("integ-tests")
val allKotlinSourceDirs = allprojects
    .filter { p -> p.name !in ignoreCoverageForProjects }
    .flatMap { p ->
        listOf(
            "${p.projectDir}/src/commonMain/kotlin",
            "${p.projectDir}/src/jvmMain/kotlin",
        )
    }
val allKotlinClassDirs = allprojects
    .filter { p -> p.name !in ignoreCoverageForProjects }
    .map { p ->
        "${p.buildDir}/classes/kotlin/jvm/main"
    }

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("jacoco")
    }

    group = "dev.evo.elasticmagic"
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    afterEvaluate {
        tasks.register<JacocoReport>("jacocoJVMTestReport") {
            group = "Reporting"
            description = "Generate Jacoco coverage report."

            classDirectories.setFrom(allKotlinClassDirs)
            sourceDirectories.setFrom(allKotlinSourceDirs)

            executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
            reports {
                html.required.set(true)
                xml.required.set(true)
                csv.required.set(false)
            }
        }

        tasks.named("jvmTest") {
            outputs.upToDateWhen { false }

            finalizedBy("jacocoJVMTestReport")
        }
    }
}

configureMultiplatform()

configure<KotlinMultiplatformExtension> {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(project(":elasticmagic-transport"))
            }
        }
    }
}
