configureMultiplatform()

kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(project(":"))
                api(project(":elasticmagic-serde-serialization-json"))
                api(project(":elasticmagic-transport-ktor"))

                implementation(Libs.kotlinxCoroutines("core"))
                implementation(Libs.ktorClient("core"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":elasticmagic-serde-jackson-json"))
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
