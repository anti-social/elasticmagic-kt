configureMultiplatform()

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":"))
            }
        }
    }
}

configureMultiplatformPublishing(
    "elasticmagic-query-filters",
    "Elasticsearch Kotlin query DSL - query filters"
)
