import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*

fun Project.configureMultiplatform(
    configureJvm: Boolean = true,
    configureJs: Boolean = true,
    configureNative: Boolean = true,
    entryPoints: List<String> = emptyList(),
) {
    configure<KotlinMultiplatformExtension> {
        if (configureJvm) {
            jvm {
                compilations.all {
                    kotlinOptions.jvmTarget = "1.8"
                }
                testRuns["test"].executionTask.configure {
                    useJUnit()
                }
                if (entryPoints.isNotEmpty()) {
                    withJava()
                    configure<JavaApplication> {
                        mainClass.set("samples.${entryPoints[0]}.MainKt")
                    }
                }
            }
        }

        if (configureJs) {
            js(IR) {
                nodejs()

                compilations.all {
                    kotlinOptions {
                        moduleKind = "umd"
                        sourceMap = true
                    }
                }
            }
        }

        if (configureNative) {
            val hostOs = System.getProperty("os.name")
            val nativeTarget = when {
                hostOs == "Mac OS X" -> macosX64("native")
                hostOs == "Linux" -> linuxX64("native")
                hostOs.startsWith("Windows") -> mingwX64("native")
                else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
            }
            nativeTarget.binaries {
                for ((ix, entryPoint) in entryPoints.withIndex()) {
                    executable(if (ix == 0) "" else entryPoint) {
                        entryPoint("samples.$entryPoint.main")
                    }
                }
            }
            targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java) {
                binaries.all {
                    binaryOptions["memoryModel"] = "experimental"
                }
            }
        }

        @Suppress("UNUSED_VARIABLE")
        sourceSets {
            val commonMain by getting
            val commonTest by getting {
                dependencies {
                    implementation(kotlin("test-common"))
                    implementation(kotlin("test-annotations-common"))
                    implementation(Libs.kotest("assertions-core"))
                }
            }

            if (configureJvm) {
                val jvmMain by getting
                val jvmTest by getting {
                    dependencies {
                        implementation(kotlin("test-junit"))
                    }
                }
            }

            if (configureJs) {
                val jsMain by getting
                val jsTest by getting {
                    dependencies {
                        implementation(kotlin("test-js"))
                    }
                }
            }

            if (configureNative) {
                val nativeMain by getting
                val nativeTest by getting
            }
        }
    }

    if (configureJvm) {
        tasks {
            getByName("jvmTest").outputs.upToDateWhen {
                false
            }

            if (entryPoints.size > 1) {
                val sourceSets = extensions.getByType(JavaPluginExtension::class).sourceSets
                val startScripts = tasks.getByName<CreateStartScripts>("startScripts")

                for (entryPoint in entryPoints.slice(1 until entryPoints.size)) {
                    val startScript = create("${entryPoint}StartScript", CreateStartScripts::class) {
                        applicationName = entryPoint
                        mainClass.set("samples.$entryPoint.MainKt")
                        classpath = sourceSets.getByName("main").runtimeClasspath
                        outputDir = startScripts.outputDir
                    }
                    startScripts.dependsOn(startScript)

                    register<JavaExec>("run${entryPoint.capitalize()}") {
                        mainClass.set(startScript.mainClass)
                        classpath = startScript.classpath!!
                        standardInput = System.`in`
                    }
                }
            }
        }
    }
}
