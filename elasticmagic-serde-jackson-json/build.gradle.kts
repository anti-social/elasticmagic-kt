configureMultiplatform(configureJs = false, configureNative = false)

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                implementation(Libs.jackson("databind"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "Elasticsearch Kotlin query DSL - json jackson serialization implementation"
)
