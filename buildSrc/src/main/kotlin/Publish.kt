import io.github.gradlenexus.publishplugin.NexusRepositoryContainer

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

import java.net.URI

fun Project.configureMultiplatformPublishing(projectName: String, projectDescription: String) {
    val javadocJar by tasks.registering(Jar::class) {
        archiveClassifier.set("javadoc")
    }

    configure<PublishingExtension> {
        publications.withType<MavenPublication> {
            artifact(javadocJar)

            configurePom(
                rootProject.extra["projectUrl"] as URI,
                projectName,
                projectDescription
            )
        }

        repositories {
            configureTestRepository(this@configureMultiplatformPublishing)
        }
    }
}

fun Project.configureJvmPublishing(projectName: String, projectDescription: String) {
    val javadocJar by tasks.registering(Jar::class) {
        archiveClassifier.set("javadoc")
    }

    val sourcesJar = tasks.register<Jar>("sourcesJar") {
        val kotlin = this@configureJvmPublishing.extensions.getByName<KotlinJvmProjectExtension>("kotlin")
        from(kotlin.sourceSets.named("main").get().kotlin)
        archiveClassifier.set("sources")
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(project.components["java"])

                artifact(sourcesJar)
                artifact(javadocJar)

                configurePom(
                    rootProject.extra["projectUrl"] as URI,
                    projectName,
                    projectDescription
                )
            }
        }

        repositories {
            configureTestRepository(this@configureJvmPublishing)
        }
    }
}

fun RepositoryHandler.configureTestRepository(project: Project): MavenArtifactRepository = maven {
    name = "test"
    url = project.uri("file://${project.rootProject.buildDir}/localMaven")
}

fun NexusRepositoryContainer.configureSonatypeRepository(project: Project) = sonatype {
    val baseSonatypeUrl = project.properties["sonatypeUrl"]?.toString()
        ?: System.getenv("SONATYPE_URL")
        ?: "https://s01.oss.sonatype.org"

    nexusUrl.set(project.uri("$baseSonatypeUrl/service/local/"))
    snapshotRepositoryUrl.set(project.uri("$baseSonatypeUrl/content/repositories/snapshots/"))

    val sonatypeUser = project.properties["sonatypeUser"]?.toString()
        ?: System.getenv("SONATYPE_USER")
    val sonatypePassword = project.properties["sonatypePassword"]?.toString()
        ?: System.getenv("SONATYPE_PASSWORD")

    username.set(sonatypeUser)
    password.set(sonatypePassword)
}

fun MavenPublication.configurePom(projectUrl: URI, projectName: String, projectDescription: String) = pom {
    name.set(projectName)
    description.set(projectDescription)
    url.set(projectUrl.toString())

    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    scm {
        url.set(projectUrl.toString())
        connection.set("scm:${URI("git", projectUrl.host, "${projectUrl.path}.git", null)}")
        developerConnection.set("scm:${URI(projectUrl.scheme, projectUrl.host, "${projectUrl.path}.git", null)}")
    }

    developers {
        developer {
            id.set("anti-social")
            name.set("Oleksandr Koval")
            email.set("kovalidis@gmail.com")
        }
    }
}
