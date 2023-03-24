buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(Libs.kotlinxAtomicfuGradlePlugin())
    }
}

apply(plugin = "kotlinx-atomicfu")

configureMultiplatform()

kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(project(":elasticmagic"))

                implementation(project(":elasticmagic-serde-serialization-json"))

                implementation(project(":elasticmagic-transport-ktor"))
                implementation(Libs.ktorClient("core"))
                implementation(Libs.kotlinxCoroutines("core"))
                implementation(Libs.kotlinxAtomicfu())

                implementation(project(":elasticmagic-kotlinx-datetime"))
                implementation(Libs.kotlinxDatetime())

            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":elasticmagic-serde-jackson-json"))
                implementation(project(":elasticmagic-serde-jackson-yaml"))
                implementation(Libs.jackson("databind"))

                implementation(Libs.ktorClient("cio"))
            }
        }

        val nativeTest by getting {
            dependencies {
                implementation(Libs.ktorClient("curl"))
            }
        }
    }
}
