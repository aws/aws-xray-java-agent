import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.github.johnrengelman.shadow") apply false
    id("nebula.release")
    id("io.github.gradle-nexus.publish-plugin")
    id("com.github.ben-manes.versions")
}

// Expose DiSCo & X-Ray SDK version to subprojects
val discoVersion by extra("0.11.0")
val xraySdkVersion by extra("2.9.1")
val awsSdkV1Version by extra("1.11.1031")
val awsSdkV2Version by extra("2.16.76")

val releaseTask = tasks.named("release")

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://aws.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://aws.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

nebulaRelease {
    addReleaseBranchPattern("main")
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    checkForGradleUpdate = true
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

allprojects {
    group = "com.amazonaws"

    repositories {
        mavenCentral()
        mavenLocal()
    }

    // Configure the shadow jar task, which does shading, to run after Gradle runs the jar task
    pluginManager.withPlugin("com.github.johnrengelman.shadow") {
        tasks {
            named<DefaultTask>("assemble") {
                dependsOn(named("shadowJar"))
            }

            named<ShadowJar>("shadowJar") {
                // Suppress the "-all" suffix on the jar name, simply replace the default built jar instead
                archiveClassifier.set("")
            }

            // Disable the conventional JAR task, and only run shadowJar instead
            named<Jar>("jar") {
                enabled = false
                dependsOn(named("shadowJar"))
            }
        }
    }

    plugins.withId("java") {
        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        dependencies {
            // BOMs for common projects
            add("implementation", platform("com.amazonaws:aws-xray-recorder-sdk-bom:${xraySdkVersion}"))
            add("implementation", platform("software.amazon.disco:disco-toolkit-bom:${discoVersion}"))
            add("implementation", platform("com.fasterxml.jackson:jackson-bom:2.11.0"))
            add("implementation", platform("com.amazonaws:aws-java-sdk-bom:${awsSdkV1Version}"))
            add("implementation", platform("software.amazon.awssdk:bom:${awsSdkV2Version}"))

            // TODO: Add build step for running Null checker
            add("compileOnly", "org.checkerframework:checker-qual:3.4.1")

            // Common test dependencies
            add("testImplementation", "junit:junit:4.12")
            add("testImplementation", "org.assertj:assertj-core:3.16.1")
            add("testImplementation", "org.mockito:mockito-core:2.28.2")
            add("testImplementation", "org.checkerframework:checker-qual:3.4.1")
        }
    }

    plugins.withId("maven-publish") {
        plugins.apply("signing")

        afterEvaluate {
            val publishTask = tasks.named("publishToSonatype")

            releaseTask.configure {
                dependsOn(publishTask)
            }
        }

        // Disable publishing a bunch of unnecessary Gradle metadata files
        tasks.withType<GenerateModuleMetadata> {
            enabled = false
        }

        // Defer maven publish until the assemble task has finished, giving time for shadowJar to complete if present
        tasks.withType<AbstractPublishToMaven> {
            dependsOn(tasks.named<DefaultTask>("assemble"))
        }

        plugins.withId("java") {
            //create a task to publish our sources
            tasks.register<Jar>("sourcesJar") {
                from(project.the<SourceSetContainer>()["main"].allJava)
                archiveClassifier.set("sources")
            }

            //create a task to publish javadoc
            tasks.register<Jar>("javadocJar") {
                from(tasks.named<Javadoc>("javadoc"))
                archiveClassifier.set("javadoc")
            }
        }

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("maven") {

                    // We only publish the Agent plugin jar to maven central
                    plugins.withId("com.github.johnrengelman.shadow") {
                        artifact(tasks.named<Jar>("shadowJar").get())
                    }

                    artifact(tasks["sourcesJar"])
                    artifact(tasks["javadocJar"])

                    versionMapping {
                        allVariants {
                            fromResolutionResult()
                        }
                    }

                    pom {
                        afterEvaluate {
                            pom.name.set(project.description)
                        }
                        description.set("The AWS X-Ray Auto-Instrumentation Agent for Java is a Java agent that " +
                                        "automatically instruments your code to use X-Ray tracing.")
                        url.set("https://aws.amazon.com/documentation/xray/")


                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://aws.amazon.com/apache2.0")
                                distribution.set("repo")
                            }
                        }

                        developers {
                            developer {
                                id.set("amazonwebservices")
                                organization.set("Amazon Web Services")
                                organizationUrl.set("https://aws.amazon.com")
                                roles.add("developer")
                            }

                            developer {
                                id.set("armiros")
                                name.set("William Armiros")
                                email.set("armiros@amazon.com")
                            }
                        }

                        scm {
                            url.set("https://github.com/aws/aws-xray-java-agent.git")
                        }
                    }
                }
            }
        }

        tasks.withType<Sign>().configureEach {
            onlyIf { System.getenv("CI") == "true" }
        }

        configure<SigningExtension> {
            val signingKeyId = System.getenv("GPG_KEY_ID")
            val signingKey = System.getenv("GPG_PRIVATE_KEY")
            val signingPassword = System.getenv("GPG_PASSWORD")
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            sign(the<PublishingExtension>().publications["maven"])
        }
    }
}
