configureMultiplatform()

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":test-utils"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "elasticmagic-query-filters",
    "Elasticsearch Kotlin query DSL - query filters"
)
