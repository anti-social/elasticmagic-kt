kotlin {
    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project(":"))
                implementation(project(":elasticmagic-serde-json"))
                implementation(project(":elasticmagic-transport-ktor"))
                implementation(Libs.ktorClient("cio"))
            }
        }
    }
}
