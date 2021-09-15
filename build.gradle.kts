import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.dokka") version "1.5.0"
    id("ru.vyarus.mkdocs") version "2.1.1"
}

// Collect all source and class directories for jacoco
// TODO: Is there a better way to do that?
val ignoreCoverageForProjects = setOf("integ-tests", "samples")
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
    group = "dev.evo.elasticmagic"
    version = "0.0.1"

    if (name == "samples") {
        return@allprojects
    }

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("jacoco")
    }

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

        tasks.findByName("jvmTest")?.run {
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

tasks.dokkaHtml {
    outputDirectory.set(buildDir.resolve("mkdocs/api/latest"))

    dokkaSourceSets {
        configureEach {
            includes.from("docs/api/packages.md")
            samples.from("samples/src/main/kotlin")
        }
    }
}

mkdocs {
    sourcesDir = "docs"

    publish.docPath = ""
}

tasks.getByName("mkdocsBuild").dependsOn(":samples:compileKotlin")
