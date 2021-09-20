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
                api(project(":elasticmagic-serde-serialization-json"))
                api(Libs.kotlinSerialization("json"))
            }
        }
    }
}
