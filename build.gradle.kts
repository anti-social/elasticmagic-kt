import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version "1.4.30" apply false
}

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
    }

    group = "dev.evo.elasticmagic"
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()
    }

    configure<KotlinMultiplatformExtension> {
        jvm {
            compilations.all {
                kotlinOptions.jvmTarget = "1.8"
            }
            testRuns["test"].executionTask.configure {
                useJUnit()
            }
        }
        js(LEGACY) {
            nodejs()

            compilations.all {
                kotlinOptions {
                    moduleKind = "umd"
                    sourceMap = true
                }
            }
        }

        val hostOs = System.getProperty("os.name")
        val isMingwX64 = hostOs.startsWith("Windows")
        val nativeTarget = when {
            hostOs == "Mac OS X" -> macosX64("native")
            hostOs == "Linux" -> linuxX64("native")
            isMingwX64 -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

        val kotestVersion = "4.4.1"
        sourceSets {
            val commonMain by getting
            val commonTest by getting {
                dependencies {
                    implementation(kotlin("test-common"))
                    implementation(kotlin("test-annotations-common"))
                    implementation(Libs.kotest("assertions-core"))
                }
            }
            val jvmMain by getting
            val jvmTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                }
            }
            val jsMain by getting
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
            val nativeMain by getting
            val nativeTest by getting
        }
    }

    tasks {
        getByName("jvmTest").outputs.upToDateWhen {
            false
        }
    }
}

configure<KotlinMultiplatformExtension> {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
            }
        }
    }
}