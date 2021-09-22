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
    "elasticmagic-transport-ktor",
    "Elasticsearch Kotlin query DSL - jackson serialization implementation"
)
