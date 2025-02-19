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
        compilerOptions {
            // TODO: Find out how to add the option only for tests
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        if (configureJvm) {
            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
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

        // FIXME: Uncomment after upgrading to Kotlin 2.x
        // https://youtrack.jetbrains.com/issue/KT-65870/KJS-Gradle-kotlinUpgradePackageLock-fails-making-Yarn-unusable
        if (configureJs) {
            js(IR) {
                nodejs()

                compilerOptions {
                    moduleKind.set(JsModuleKind.MODULE_UMD)
                    sourceMap.set(true)
                }
            }
        }

        if (configureNative) {
            val hostOs = System.getProperty("os.name")
            val nativeTarget = when {
                hostOs == "Mac OS X" -> macosX64()
                hostOs == "Linux" -> linuxX64()
                hostOs.startsWith("Windows") -> mingwX64()
                else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
            }

            nativeTarget.binaries {
                for ((ix, entryPoint) in entryPoints.withIndex()) {
                    executable(if (ix == 0) "" else entryPoint) {
                        entryPoint("samples.$entryPoint.main")
                    }
                }
            }
        }

        applyDefaultHierarchyTemplate()

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

                    val runTaskName = "run${entryPoint.capitalize()}"
                    register<JavaExec>(runTaskName) {
                        mainClass.set(startScript.mainClass)
                        classpath = startScript.classpath!!
                        standardInput = System.`in`
                    }
                }
            }
        }
    }
}
