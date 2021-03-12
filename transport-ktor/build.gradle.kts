kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                implementation(project(":elasticmagic-serde-json"))
                implementation(Libs.ktorClient("encoding"))
                api(Libs.ktorClient("core"))
                api(Libs.kotlinSerialization("json"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(Libs.ktorClient("mock"))
            }
        }
    }
}
