import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.*

@OptIn(ExperimentalKotlinGradlePluginApi::class)
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

            for ((ix, entryPoint) in entryPoints.withIndex()) {
                val runTaskName = if (ix == 0) {
                    "run"
                } else {
                    "run${entryPoint.replaceFirstChar { it.uppercaseChar() }}"
                }
                registerJvmRunTask(runTaskName, "samples.$entryPoint.MainKt")
            }
        }
    }
}

// Replacement for the 'application' plugin which is not compatible with Kotlin Multiplatform
fun Project.registerJvmRunTask(name: String, mainClassName: String) {
    val mainCompilation = the<KotlinMultiplatformExtension>().jvm().compilations.getByName("main")
    tasks.register<JavaExec>(name) {
        group = "application"
        mainClass.set(mainClassName)
        classpath = files(mainCompilation.output.allOutputs, mainCompilation.runtimeDependencyFiles)
        standardInput = System.`in`
    }
}
