kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-serde"))
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
