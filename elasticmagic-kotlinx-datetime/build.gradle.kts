configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic"))
                api(Libs.kotlinxDatetime())
            }
        }
    }
}

configureMultiplatformPublishing(
    "Elasticsearch Kotlin query DSL - datetime field types using kotlinx.datetime"
)
