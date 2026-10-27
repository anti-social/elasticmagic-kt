import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlinx.benchmark") version Versions.kotlinxBenchmark
}

configureMultiplatform(configureJs = false)

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:${Versions.kotlinxBenchmark}")
                implementation(project(":elasticmagic-serde-kotlinx-json"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":elasticmagic-serde-jackson-json"))
            }
        }
        val nativeMain by getting
    }
}

benchmark {
    configurations {
        // warmups = 2
        named("main") {
            warmups = 1
            iterations = 2
        }
    }

    targets {
        register("jvm")
        kotlin.targets.withType<KotlinNativeTarget>().forEach { register(it.name) }
    }
}
