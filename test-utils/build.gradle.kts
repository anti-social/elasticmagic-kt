configureMultiplatform()

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":"))
                implementation(Libs.kotest("assertions-core"))
            }
        }
    }
}
