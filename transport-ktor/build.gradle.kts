configureMultiplatform()

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                implementation(Libs.ktorClient("encoding"))
                api(Libs.ktorClient("core"))
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
