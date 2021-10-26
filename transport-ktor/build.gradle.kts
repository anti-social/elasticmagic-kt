configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                implementation(Libs.ktorClient("encoding"))
                api(Libs.ktorClient("core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(Libs.ktorClient("mock"))
                implementation(project(":elasticmagic-serde-serialization-json"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "elasticmagic-transport-ktor",
    "Elasticsearch Kotlin query DSL - ktor transport implementation"
)
