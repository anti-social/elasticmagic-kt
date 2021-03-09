kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                implementation(Libs.ktorClient("core"))
                implementation(Libs.ktorClient("encoding"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(Libs.ktorClient("mock"))
                implementation(Libs.kotlinSerialization("json"))
            }
        }
    }
}
