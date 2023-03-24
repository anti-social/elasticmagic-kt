configureMultiplatform()

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":elasticmagic-serde"))
            }
        }

        getByName("commonTest") {
            dependencies {
                api(project(":elasticmagic-serde-kotlinx-json"))
                api(Libs.kotlinxSerialization("json"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "Elasticsearch Kotlin query DSL - transport interface"
)
