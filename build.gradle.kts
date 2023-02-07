import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    `maven-publish`
    signing
    kotlin("multiplatform") apply false
    id(Plugins.binaryCompatibilityValidator) version Versions.binaryCompatibilityValidator
    id("io.gitlab.arturbosch.detekt") version Versions.detekt apply false
    id("org.jetbrains.dokka") version Versions.dokka
    id("ru.vyarus.mkdocs") version Versions.mkdocs
    id("org.ajoberstar.grgit") version Versions.grgit
    id("io.github.gradle-nexus.publish-plugin")
}

repositories {
    mavenCentral()
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

apiValidation {
    ignoredProjects.addAll(
        (allprojects.toSet() - publishProjects).map(Project::getName)
    )
}

group = "dev.evo.elasticmagic"
version = gitDescribe.trimStart('v')
extra["projectUrl"] = uri("https://github.com/anti-social/elasticmagic-kt")

@OptIn(ExperimentalStdlibApi::class)
subprojects {
    group = rootProject.group
    version = rootProject.version

    if (name == "samples") {
        return@subprojects
    }

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("jacoco")
        plugin("io.gitlab.arturbosch.detekt")
    }

    val jacoco = project.extensions.getByType(JacocoPluginExtension::class)
    jacoco.toolVersion = Versions.jacoco

    if (this in publishProjects) {
        apply {
            plugin("org.jetbrains.dokka")
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
        tasks.findByName("jsNodeTest")?.run {
            outputs.upToDateWhen { false }
        }

        tasks.findByName("jvmTest")?.run {
            outputs.upToDateWhen { false }

            finalizedBy("jacocoJVMTestReport")
        }

        tasks.withType<Test>().configureEach {
            testLogging {
                events = buildSet<TestLogEvent> {
                    add(TestLogEvent.FAILED)
                    if (project.hasProperty("showPassedTests")) {
                        add(TestLogEvent.PASSED)
                    }
                }
                exceptionFormat = TestExceptionFormat.FULL
                stackTraceFilters = setOf(
                    TestStackTraceFilter.ENTRY_POINT
                )
            }
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = Versions.jvmTarget.toString()
            }
        }

        tasks.register("configurations") {
            println("Available configurations:")
            configurations.names.forEach { println("- $it") }
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
        val detektJvm = tasks.getByName<io.gitlab.arturbosch.detekt.Detekt>("detekt") {
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
        }
        val detektAll = tasks.register("detektAll") {
            dependsOn(detektJvm, detektOthers)
        }
        tasks.getByName("check").dependsOn(detektAll)

        tasks.withType<DokkaTaskPartial> {
            failOnWarning.set(true)

            dokkaSourceSets {
                configureEach {
                    val packageDocs = "${rootProject.projectDir}/docs/api/${project.name}.md"
                    if (file(packageDocs).exists()) {
                        includes.from(packageDocs)
                    }
                    samples.from(
                        "${rootProject.projectDir}/samples/src/commonMain/kotlin",
                        "${rootProject.projectDir}/samples/src/jvmMain/kotlin"
                    )
                }
            }
        }
    }
}

val docsVersion = if ("-\\d+-g.*".toRegex().containsMatchIn(version.toString())) {
    "dev"
} else {
    version.toString()
}

tasks.withType<DokkaMultiModuleTask> {
    outputDirectory.set(buildDir.resolve("mkdocs/$docsVersion/api"))
}

mkdocs {
    sourcesDir = "docs"
    // With strict it will fail because of 'api' directory is missing
    strict = false

    val isLatest = grgit.branch.current().name == "master" && docsVersion != "dev"
    publish.apply {
        docPath = docsVersion
        rootRedirect = isLatest
        generateVersionsFile = true
        if (isLatest) {
            rootRedirectTo = "latest"
            setVersionAliases("latest")
        }
    }

    extras = mapOf(
        "version" to docsVersion,
        "elasticmagic_version" to version.toString(),
        "ktor_version" to Versions.ktor,
    )
}

nexusPublishing {
    repositories {
        configureSonatypeRepository(project)
    }
}
