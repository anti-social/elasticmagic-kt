import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    `maven-publish`
    signing
    kotlin("multiplatform") apply false
    id("io.gitlab.arturbosch.detekt") version Versions.detekt apply false
    id("org.jetbrains.dokka") version Versions.dokka
    id("ru.vyarus.mkdocs") version Versions.mkdocs
    id("org.ajoberstar.grgit") version Versions.grgit
    id("io.github.gradle-nexus.publish-plugin")
}

val gitDescribe = grgit.describe(mapOf("match" to listOf("v*"), "tags" to true))
    ?: "v0.0.0-SNAPSHOT"

// Collect all source and class directories for jacoco
// TODO: Is there a better way to do that?
val publishProjects = allprojects
    .filter { p -> p.name.startsWith("elasticmagic") }
    .toSet()
val allKotlinSourceDirs = publishProjects
    .flatMap { p ->
        listOf(
            "${p.projectDir}/src/commonMain/kotlin",
            "${p.projectDir}/src/jvmMain/kotlin",
        )
    }
val allKotlinClassDirs = publishProjects
    .map { p ->
        "${p.buildDir}/classes/kotlin/jvm/main"
    }

allprojects {
    group = "dev.evo.elasticmagic"
    version = gitDescribe.trimStart('v')

    // We could make `samples` a separate project,
    // but then we should open it as different project in IDE for working code completion
    if (name == "samples") {
        return@allprojects
    }

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("jacoco")
        plugin("io.gitlab.arturbosch.detekt")
    }

    if (this in publishProjects) {
        apply {
            plugin("maven-publish")
            plugin("signing")
        }

        signing {
            sign(publishing.publications)
        }
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

        tasks.findByName("nativeTest")?.run {
            outputs.upToDateWhen { false }
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

nexusPublishing {
    repositories {
        configureSonatypeRepository(project)
    }
}

extra["projectUrl"] = uri("https://github.com/anti-social/prometheus-kt")
configureMultiplatformPublishing("elasticmagic", "Elasticsearch Kotlin query DSL")
