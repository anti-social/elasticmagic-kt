import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") apply false
    id("io.gitlab.arturbosch.detekt") version Versions.detekt apply false
    id("org.jetbrains.dokka") version Versions.dokka
    id("ru.vyarus.mkdocs") version Versions.mkdocs
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
        plugin("io.gitlab.arturbosch.detekt")
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

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = Versions.jvmTarget.toString()
            }
        }

        val detektConfig = "$rootDir/detekt.yml"
        val detektOthers = tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektOthers") {
            config.from(detektConfig)
            source = fileTree("$projectDir/src")
            include(
                "jsMain/**/*.kt",
                "jsTest/**/*.kt",
                "nativeMain/**/*.kt",
                "nativeTest/**/*.kt",
            )
        }
        val detekt = tasks.getByName<io.gitlab.arturbosch.detekt.Detekt>("detekt") {
            config.from(detektConfig)
            source = fileTree("$projectDir/src").apply {
                include(
                    "commonMain/**/*.kt",
                    "commonTest/**/*.kt",
                    "jvmMain/**/*.kt",
                    "jvmTest/**/*.kt",
                )
            }
            classpath.setFrom(
                configurations.getByName("detekt"),
                configurations.getByName("jvmCompileClasspath"),
                configurations.getByName("jvmTestCompileClasspath"),
            )
            jvmTarget = Versions.jvmTarget.toString()

            dependsOn(detektOthers)
        }
        tasks.getByName("check").dependsOn(detekt)
    }
}

configureMultiplatform()

configure<KotlinMultiplatformExtension> {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
        }
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(project(":elasticmagic-transport"))
                implementation(Libs.kotlinCoroutines("core"))
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
