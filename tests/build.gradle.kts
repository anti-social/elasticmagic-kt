configureMultiplatform()

kotlin {
    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project(":"))
                implementation(project(":elasticmagic-serde-serialization-json"))
                implementation(project(":elasticmagic-serde-jackson-json"))
                implementation(project(":elasticmagic-transport-ktor"))
                implementation(Libs.ktorClient("cio"))
            }
        }
    }
}
