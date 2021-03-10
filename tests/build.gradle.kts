kotlin {
    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project(":"))
                implementation(project(":elasticmagic-json"))
                implementation(project(":elasticmagic-transport"))
                implementation(project(":elasticmagic-ktor-transport"))
                implementation(Libs.ktorClient("core"))
                implementation(Libs.ktorClient("cio"))
                implementation(Libs.kotlinSerialization("json"))
            }
        }
    }
}
