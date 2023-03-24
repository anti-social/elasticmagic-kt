plugins {
    id("org.jetbrains.kotlinx.benchmark") version "0.4.4"
}

configureMultiplatform(configureJs = false)

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.4")
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
        register("native")
    }
}
