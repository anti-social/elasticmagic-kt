plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":"))
}

application {
    mainClass.set("samples.started.MainKt")
}
