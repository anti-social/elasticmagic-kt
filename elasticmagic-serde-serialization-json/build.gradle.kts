configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(Libs.kotlinxSerialization("json"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":elasticmagic"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "elasticmagic-transport-ktor",
    "Elasticsearch Kotlin query DSL - kotlinx.serialization implementation"
)
