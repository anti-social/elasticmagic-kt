configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":elasticmagic-serde"))
                api(Libs.kotlinSerialization("json"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":"))
            }
        }
    }
}
