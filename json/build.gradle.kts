kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                api(Libs.kotlinSerialization("json"))
            }
        }
    }
}
