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
        "${p.layout.buildDirectory.get()}/classes/kotlin/jvm/main"
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

    tasks.register<JacocoReport>("jacocoJVMTestReport") {
        group = "Reporting"
        description = "Generate Jacoco coverage report."

        classDirectories.setFrom(allKotlinClassDirs)
        sourceDirectories.setFrom(allKotlinSourceDirs)

        executionData.setFrom(files("${layout.buildDirectory.get()}/jacoco/jvmTest.exec"))
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
                if (project.hasProperty("showTestsOutput")) {
                    add(TestLogEvent.STANDARD_OUT)
                    add(TestLogEvent.STANDARD_ERROR)
                }
            }
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.register("configurations") {
        println("Available configurations:")
        configurations.names.forEach { println("- $it") }
    }

    afterEvaluate {
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
        }
        val detektAll = tasks.register("detektAll") {
            dependsOn(detektJvm, detektOthers)
        }
        tasks.getByName("check").dependsOn(detektAll)

        tasks.register("quickCheck") {
            val dependsOnTasks = mutableListOf(
                "compileKotlinJvm", "compileTestKotlinJvm", "detekt"
            )
            if (!listOf("benchmarks", "test-utils", "integ-tests").contains(project.name)) {
                dependsOnTasks.add("apiCheck")
            }
            if (project.name != "integ-tests") {
                dependsOnTasks.add("jvmTest")
            }
            dependsOn(dependsOnTasks)
        }

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

data class DocsInfo(
    val version: String,
    val elasticmagicVersion: String,
    val updateLatestAlias: Boolean,
)

val headCommitMessage = grgit.head().shortMessage
val gitDescribeEndingRegex = "-\\d+-g.*".toRegex()
val docsInfo = when {
    headCommitMessage.contains("[fix docs]") -> {
        // Trim git describe ending from a version
        val docsVersion = gitDescribeEndingRegex.replace(version.toString(), "")
        DocsInfo(
            version = docsVersion,
            elasticmagicVersion = docsVersion,
            updateLatestAlias = headCommitMessage.contains("[latest docs]")
        )
    }
    gitDescribeEndingRegex.containsMatchIn(version.toString()) == true -> {
        DocsInfo(
            version = "dev",
            elasticmagicVersion = version.toString(),
            updateLatestAlias = false
        )
    }
    else -> {
        DocsInfo(
            version = version.toString(),
            elasticmagicVersion = version.toString(),
            updateLatestAlias = true
        )
    }
}

tasks.withType<DokkaMultiModuleTask> {
    outputDirectory.set(
        layout.buildDirectory.get().getAsFile().resolve("mkdocs/${docsInfo.version}/api")
    )
}

tasks.register("quickCheck") {
    dependsOn("mkdocsBuild")
}

mkdocs {
    sourcesDir = "docs"
    // With strict it will fail because of 'api' directory is missing
    strict = false

    println(docsInfo)
    publish.apply {
        docPath = docsInfo.version
        rootRedirect = docsInfo.updateLatestAlias
        generateVersionsFile = true
        if (docsInfo.updateLatestAlias) {
            rootRedirectTo = "latest"
            setVersionAliases("latest")
        }
    }

    extras = mapOf(
        "version" to docsInfo.version,
        "elasticmagic_version" to docsInfo.elasticmagicVersion,
        "ktor_version" to Versions.ktor,
    )
}

nexusPublishing {
    repositories {
        configureSonatypeRepository(project)
    }
}
