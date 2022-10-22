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
    "Elasticsearch Kotlin query DSL - kotlinx.serialization implementation for JSON"
)
