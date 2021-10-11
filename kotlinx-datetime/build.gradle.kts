configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                api(Libs.kotlinxDatetime())
            }
        }
    }
}

configureMultiplatformPublishing(
    "elasticmagic-kotlinx-datetime",
    "Elasticsearch Kotlin query DSL - datetime field types using kotlinx.datetime"
)
