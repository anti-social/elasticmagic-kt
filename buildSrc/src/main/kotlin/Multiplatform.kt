import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*

fun Project.configureMultiplatform(
    configureJvm: Boolean = true,
    configureJs: Boolean = true,
    configureNative: Boolean = true
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
            }
        }

        if (configureJs) {
            js(LEGACY) {
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
        }

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
        }
    }
}
