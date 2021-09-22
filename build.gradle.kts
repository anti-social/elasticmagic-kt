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

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config = files("$rootDir/detekt.yml")
        source = files("$projectDir/src")
    }

    val detektJvm = tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektJvm") {
        source = fileTree("$projectDir/src")
        include(
            "commonMain/**/*.kt",
            "commonTest/**/*.kt",
            "jvmMain/**/*.kt",
            "jvmTest/**/*.kt",
        )
        classpath.setFrom(
            configurations.getByName("detekt"),
            configurations.getByName("jvmCompileClasspath"),
            configurations.getByName("jvmTestCompileClasspath"),
        )
        config.from("$rootDir/detekt.yml")
        debug = true
        jvmTarget = JavaVersion.VERSION_11.toString()
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
                jvmTarget = JavaVersion.VERSION_11.toString()
            }
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
