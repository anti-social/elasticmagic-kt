kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                api(Libs.ktorClient("core"))
                implementation(Libs.ktorClient("encoding"))
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
