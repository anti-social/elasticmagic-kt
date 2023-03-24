configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(Libs.kotlinxSerialization("json"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "Elasticsearch Kotlin query DSL - kotlinx.serialization implementation for JSON"
)
