configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(project(":elasticmagic-transport"))
                implementation(Libs.kotlinxCoroutines("core"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":elasticmagic-kotlinx-datetime"))
                implementation(project(":test-utils"))
            }
        }
    }
}

configureMultiplatformPublishing("elasticmagic", "Elasticsearch Kotlin query DSL")
