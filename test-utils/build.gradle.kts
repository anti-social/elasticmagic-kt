configureMultiplatform()

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":elasticmagic"))
                implementation(Libs.kotest("assertions-core"))
            }
        }
    }
}
