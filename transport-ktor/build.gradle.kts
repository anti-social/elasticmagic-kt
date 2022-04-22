configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(project(":elasticmagic-transport"))
                implementation(Libs.ktorClient("encoding"))
                implementation(Libs.ktorClient("core"))
                implementation(Libs.ktorClient("auth"))
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
